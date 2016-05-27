package org.blackcoinj.stake;

import static com.google.common.base.Preconditions.checkState;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.Executor;

import javax.annotation.Nullable;

import org.bitcoinj.core.AbstractPeerEventListener;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Message;
import org.bitcoinj.core.Peer;
import org.bitcoinj.core.PeerEventListener;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.RejectMessage;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.Threading;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.google.common.annotations.VisibleForTesting;
import com.google.common.base.Joiner;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.SettableFuture;

public class BlockBroadcast {
	private static final Logger log = LoggerFactory.getLogger(BlockBroadcast.class);

    private final SettableFuture<Block> future = SettableFuture.create();
    private final PeerGroup peerGroup;
    private final Block block;
    private int minConnections;
    private int numWaitingFor;

    /** Used for shuffling the peers before broadcast: unit tests can replace this to make themselves deterministic. */
    @VisibleForTesting
    public static Random random = new Random();
    
    // Tracks which nodes sent us a reject message about this broadcast, if any. Useful for debugging.
    private Map<Peer, RejectMessage> rejects = Collections.synchronizedMap(new HashMap<Peer, RejectMessage>());

    public BlockBroadcast(PeerGroup peerGroup, Block block) {
        this.peerGroup = peerGroup;
        this.block = block;
        this.minConnections = Math.max(1, peerGroup.getMinBroadcastConnections());
    }

    // Only for mock broadcasts.
    private BlockBroadcast(Block block) {
        this.peerGroup = null;
        this.block = block;
    }

    @VisibleForTesting
    public static BlockBroadcast createMockBroadcast(Block block, final SettableFuture<Block> future) {
        return new BlockBroadcast(block) {
            @Override
            public ListenableFuture<Block> broadcast() {
                return future;
            }

            @Override
            public ListenableFuture<Block> future() {
                return future;
            }
        };
    }

    public ListenableFuture<Block> future() {
        return future;
    }

    public void setMinConnections(int minConnections) {
        this.minConnections = minConnections;
    }

    private PeerEventListener rejectionListener = new AbstractPeerEventListener() {
        @Override
        public Message onPreMessageReceived(Peer peer, Message m) {
            if (m instanceof RejectMessage) {
                RejectMessage rejectMessage = (RejectMessage)m;
                if (block.getHash().equals(rejectMessage.getRejectedObjectHash())) {
                    rejects.put(peer, rejectMessage);
                    int size = rejects.size();
                    long threshold = Math.round(numWaitingFor / 2.0);
                    if (size > threshold) {
                        log.warn("Threshold for considering broadcast rejected has been reached ({}/{})", size, threshold);
                        future.setException(new RejectedBlockException(block, rejectMessage));
                        peerGroup.removeEventListener(this);
                    }
                }
            }
            return m;
        }
    };

    public ListenableFuture<Block> broadcast() {
        peerGroup.addEventListener(rejectionListener, Threading.SAME_THREAD);
        log.info("Waiting for {} peers required for broadcast, we have {} ...", minConnections, peerGroup.getConnectedPeers().size());
        peerGroup.waitForPeers(minConnections).addListener(new EnoughAvailablePeers(), Threading.SAME_THREAD);
        return future;
    }

    private class EnoughAvailablePeers implements Runnable {
        @Override
        public void run() {
            // We now have enough connected peers to send the transaction.
            // This can be called immediately if we already have enough. Otherwise it'll be called from a peer
            // thread.

            // We will send the tx simultaneously to half the connected peers and wait to hear back from at least half
            // of the other half, i.e., with 4 peers connected we will send the tx to 2 randomly chosen peers, and then
            // wait for it to show up on one of the other two. This will be taken as sign of network acceptance. As can
            // be seen, 4 peers is probably too little - it doesn't taken many broken peers for tx propagation to have
            // a big effect.
            List<Peer> peers = peerGroup.getConnectedPeers();    // snapshots
            // Prepare to send the transaction by adding a listener that'll be called when confidence changes.
            // Only bother with this if we might actually hear back:
            
            int numConnected = peers.size();
            int numToBroadcastTo = (int) Math.max(1, Math.round(Math.ceil(peers.size() / 2.0)));
            numWaitingFor = (int) Math.ceil((peers.size() - numToBroadcastTo) / 2.0);
            Collections.shuffle(peers, random);
            peers = peers.subList(0, numToBroadcastTo);
            log.info("broadcastBlock: We have {} peers", numConnected);
            log.info("serializing:");
            log.info(Utils.HEX.encode(block.bitcoinSerialize()));
            log.info("Sending to {} peers, will wait for {}, sending to: {}", numToBroadcastTo, numWaitingFor, Joiner.on(",").join(peers));
            for (Peer peer : peers) {
                try {
                    peer.sendMessage(block);
                    // We don't record the peer as having seen the tx in the memory pool because we want to track only
                    // how many peers announced to us.
                } catch (Exception e) {
                    log.error("Caught exception sending to {}", peer, e);
                }
            }
            // If we've been limited to talk to only one peer, we can't wait to hear back because the
            // remote peer won't tell us about transactions we just announced to it for obvious reasons.
            // So we just have to assume we're done, at that point. This happens when we're not given
            // any peer discovery source and the user just calls connectTo() once.
            if (minConnections == 1) {
                peerGroup.removeEventListener(rejectionListener);
                future.set(block);
            }
        }
    }

    private int numSeemPeers;
    private boolean mined;

    private void invokeProgressCallback(int numSeenPeers, boolean mined) {
        final ProgressCallback callback;
        Executor executor;
        synchronized (this) {
            callback = this.callback;
            executor = this.progressCallbackExecutor;
        }
        if (callback != null) {
            final double progress = Math.min(1.0, mined ? 1.0 : numSeenPeers / (double) numWaitingFor);
            checkState(progress >= 0.0 && progress <= 1.0, progress);
            try {
                if (executor == null)
                    callback.onBroadcastProgress(progress);
                else
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            callback.onBroadcastProgress(progress);
                        }
                    });
            } catch (Throwable e) {
                log.error("Exception during progress callback", e);
            }
        }
    }

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////

    /** An interface for receiving progress information on the propagation of the tx, from 0.0 to 1.0 */
    public interface ProgressCallback {
        /**
         * onBroadcastProgress will be invoked on the provided executor when the progress of the transaction
         * broadcast has changed, because the transaction has been announced by another peer or because the transaction
         * was found inside a mined block (in this case progress will go to 1.0 immediately). Any exceptions thrown
         * by this callback will be logged and ignored.
         */
        void onBroadcastProgress(double progress);
    }

    @Nullable private ProgressCallback callback;
    @Nullable private Executor progressCallbackExecutor;

    /**
     * Sets the given callback for receiving progress values, which will run on the user thread. See
     * {@link org.bitcoinj.utils.Threading} for details.  If the broadcast has already started then the callback will
     * be invoked immediately with the current progress.
     */
    public void setProgressCallback(ProgressCallback callback) {
        setProgressCallback(callback, Threading.USER_THREAD);
    }

    /**
     * Sets the given callback for receiving progress values, which will run on the given executor. If the executor
     * is null then the callback will run on a network thread and may be invoked multiple times in parallel. You
     * probably want to provide your UI thread or Threading.USER_THREAD for the second parameter. If the broadcast
     * has already started then the callback will be invoked immediately with the current progress.
     */
    public void setProgressCallback(ProgressCallback callback, @Nullable Executor executor) {
        boolean shouldInvoke;
        int num;
        boolean mined;
        synchronized (this) {
            this.callback = callback;
            this.progressCallbackExecutor = executor;
            num = this.numSeemPeers;
            mined = this.mined;
            shouldInvoke = numWaitingFor > 0;
        }
        if (shouldInvoke)
            invokeProgressCallback(num, mined);
    }
}
