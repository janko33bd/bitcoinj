package org.bitcoinj.examples;

import static com.google.common.base.Preconditions.checkState;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.bitcoinj.core.AbstractBlockChain;
import org.bitcoinj.core.AbstractBlockChainListener;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.Context;
import org.bitcoinj.core.ECKey;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.PeerGroup;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionConfidence;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.TransactionOutput;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.core.Wallet;
import org.bitcoinj.script.Script;
import org.bitcoinj.script.ScriptOpCodes;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.blackcoinj.pos.BlackcoinPOS;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Iterables;
import com.google.common.util.concurrent.AbstractExecutionThreadService;


public class Staker extends AbstractExecutionThreadService {
	
    private static final Logger log = LoggerFactory.getLogger(Staker.class);

    private NetworkParameters params; 
    private PeerGroup peers;
    private Wallet wallet;
    private FullPrunedBlockStore store; 
    private AbstractBlockChain chain;
    private boolean newBestBlockArrivedFromAnotherNode = false;
    
    public Staker(NetworkParameters params, PeerGroup peers, Wallet wallet, FullPrunedBlockStore store, AbstractBlockChain chain) {
        this.params = params;
        this.peers = peers;
        this.wallet = wallet;
        this.store = store;
        this.chain = chain;
    }
	
    private class MinerBlockChainListener extends AbstractBlockChainListener {

        @Override
        public void notifyNewBestBlock(StoredBlock storedBlock) throws VerificationException {
        	log.info("notify new block");
        	newBestBlockArrivedFromAnotherNode = true;
        }

        @Override
        public void reorganize(StoredBlock splitPoint, List<StoredBlock> oldBlocks, List<StoredBlock> newBlocks) throws VerificationException {
        	newBestBlockArrivedFromAnotherNode = true;
        }
        
    }
    
    MinerBlockChainListener minerBlockChainListener = new MinerBlockChainListener();
    
    @Override
    protected void startUp() throws Exception {
        super.startUp();
        log.info("adding listener");
        chain.addListener(minerBlockChainListener);
    }
    
    @Override
    protected void shutDown() throws Exception {
        super.shutDown();
        chain.removeListener(minerBlockChainListener);
    }

    @Override
    protected void run() throws Exception {
    	while (isRunning()) {
            try {
                //System.out.println("Press any key to mine 1 block...");
                //System.in.read();
                stake();
                
            } catch (Exception e) {
                log.error("Exception mining", e);
            }
        }
    }
	
	
	void stake() throws Exception {
		
        Transaction coinbaseTransaction = new Transaction(params);
        String coibaseMessage = "Staked on blackcoinj" + System.currentTimeMillis();
        char[] chars = coibaseMessage.toCharArray();
        byte[] bytes = new byte[chars.length];
        for(int i=0;i<bytes.length;i++) bytes[i] = (byte) chars[i];
        TransactionInput ti = new TransactionInput(params, coinbaseTransaction, bytes);
        coinbaseTransaction.addInput(ti);
        coinbaseTransaction.addOutput(new TransactionOutput(params, coinbaseTransaction, Coin.ZERO, new byte[0]));
        
        StoredBlock prevBlock = null;
        Block newBlock;
        ECKey key = new ECKey();
        chain.getLock().lock();
        try {
            prevBlock = chain.getChainHead();
            newBestBlockArrivedFromAnotherNode = false;
        } finally {
        	chain.getLock().unlock();
        }    
            Sha256Hash prevBlockHash = prevBlock.getHeader().getHash();        
            
            BigInteger bigNextTargetRequired = params.getNextTargetRequired(prevBlock, store);
            long difficultyTarget = Utils.encodeCompactBits(bigNextTargetRequired);
            
            
            List<TransactionOutput> calculateAllSpendCandidates = wallet.calculateAllSpendCandidates();
            long stakeTxTime = Utils.currentTimeSeconds();       
    		
            Sha256Hash stakeKernelHash = null;
            Transaction coinstakeTx = new Transaction(params);
            coinstakeTx.addOutput(Coin.ZERO, new Script(new byte[32]));
            log.info("loop till block arrives");
            
            while (!newBestBlockArrivedFromAnotherNode) {
            	stakeKernelHash = blackStake(prevBlock, key, difficultyTarget, calculateAllSpendCandidates,
						stakeTxTime, coinstakeTx);
            	if(stakeKernelHash!=null){
            		log.info("kernel found");
            		break;
            	}
            		
                Thread.sleep(16000);	                      
            }
            log.info("block arrived or stake?");
            
            if(stakeKernelHash == null){
            	log.info("block arrived");
            	return;
            }
              	 
            chain.getLock().lock();
            try {
            	Set<Transaction> transactionsToInclude = getTransactionsToInclude(Context.get().getConfidenceTable().getAll(), prevBlock.getHeight());
                Coin Fees = extractFees(transactionsToInclude);
                coinstakeTx.getOutput(1).getValue().add(Fees);
                long time = System.currentTimeMillis() / 1000;                  
                newBlock = new Block(params, NetworkParameters.PROTOCOL_VERSION, prevBlockHash, time, difficultyTarget);
                newBlock.addTransaction(coinbaseTransaction);
                newBlock.addTransaction(coinstakeTx);
                
                for (Transaction transaction : transactionsToInclude) {
                    newBlock.addTransaction(transaction);
                }
                byte[] blockSignature = key.sign(newBlock.getHash()).encodeToDER();
                newBlock.setSignature(blockSignature);
            } finally {
            	chain.getLock().unlock();
            } 
        log.info("broadcasting: " + newBlock.getHash());
        
        if (newBestBlockArrivedFromAnotherNode) {
            log.info("Interrupted mining because another best block arrived(so close!)");
            return;
        }
        
        peers.broadcastMinedBlock(newBlock);
        log.info("Sent mined block: " + newBlock.getHash());
        wallet.importKey(key);
	}

	private Coin extractFees(Set<Transaction> transactionsToInclude) {
		Coin fee = Coin.ZERO;
		for(Transaction tx:transactionsToInclude){
			fee.add(tx.getFee());
		}
		return fee;
	}

	private Sha256Hash blackStake(StoredBlock prevBlock, ECKey key, long difficultyTarget, List<TransactionOutput> calculateAllSpendCandidates, long stakeTxTime, Transaction coinstakeTx) {
		Sha256Hash stakeKernelHash = null;
		for(TransactionOutput candidate: calculateAllSpendCandidates){
			log.info("staking " + candidate.getValue().toPlainString());
			for (int n = 0; n < 60; n++) {
				//if (CheckKernel(pindexPrev, nBits, txNew.nTime - n, prevoutStake, &nBlockTime[not needed]))
				stakeKernelHash = checkForKernel(prevBlock, difficultyTarget, stakeTxTime - n, candidate);
		    	if(stakeKernelHash!=null){
		    		log.info("kernel found");
		    		Script scriptPubKeyKernel = candidate.getParentTransaction().getOutput(candidate.getIndex()).getScriptPubKey();
		    		coinstakeTx.setnTime(stakeTxTime - n);
					coinstakeTx.addSignedInput(candidate.getOutPointFor(), scriptPubKeyKernel, key);			        
			        ByteArrayOutputStream scriptPubKeyBytes = new ByteArrayOutputStream();
			        try {
						Script.writeBytes(scriptPubKeyBytes, key.getPubKey());
					} catch (IOException e) {
						
						e.printStackTrace();
					}
			        scriptPubKeyBytes.write(ScriptOpCodes.OP_CHECKSIG);
					coinstakeTx.addOutput(new TransactionOutput(params, coinstakeTx, Coin.valueOf(1, 50), scriptPubKeyBytes.toByteArray()));					
					break;              
		    	}  
			}
			
		}
		return stakeKernelHash;
	}

	private Sha256Hash checkForKernel(StoredBlock prevBlock, long difficultyTarget, long stakeTxTime, TransactionOutput candidate) {
		Sha256Hash stakeKernelHash = null;
		
			try{
				TransactionOutPoint prevoutStake = candidate.getOutPointFor();
				UTXO txPrev = store.getTransactionOutput(prevoutStake.getHash(), prevoutStake.getIndex());
				if(txPrev == null){
					log.info("can't check for kernel");
					return stakeKernelHash;
				}
					
				BlackcoinPOS blkPOS = new BlackcoinPOS(store);
				stakeKernelHash = blkPOS.checkStakeKernelHash(prevBlock, difficultyTarget, txPrev, stakeTxTime, prevoutStake);
			}catch(BlockStoreException ex){
				// either prevout was not found or stake kernel..
			}
		return stakeKernelHash;
	}

	private Set<Transaction> getTransactionsToInclude(List<TransactionConfidence> list, int prevHeight) throws BlockStoreException {
        checkState(chain.getLock().isHeldByCurrentThread());
        Set<TransactionOutPoint> spentOutPointsInThisBlock = new HashSet<TransactionOutPoint>();
        Set<Transaction> transactionsToInclude = new TreeSet<Transaction>(new TransactionPriorityComparator());
        for (TransactionConfidence txConf : list) {
        	Transaction tx = wallet.getTransaction(txConf.getTransactionHash());
            if (!store.hasUnspentOutputs(tx.getHash(), tx.getOutputs().size())) {                
                // Transaction was not already included in a block that is part of the best chain 
                boolean allOutPointsAreInTheBestChain = true;
                boolean allOutPointsAreMature = true;
                boolean doesNotDoubleSpend = true;
                for (TransactionInput transactionInput : tx.getInputs()) {
                    TransactionOutPoint outPoint = transactionInput.getOutpoint();
                    UTXO storedOutPoint = store.getTransactionOutput(outPoint.getHash(), outPoint.getIndex());
                    if (storedOutPoint == null) {
                        //Outpoint not in the best chain
                        allOutPointsAreInTheBestChain = false;
                        break;
                    }
                    if ((prevHeight+1) - storedOutPoint.getHeight() < params.getSpendableCoinbaseDepth()) {
                        //Outpoint is a non mature coinbase
                        allOutPointsAreMature = false;
                        break;
                    }
                    if (spentOutPointsInThisBlock.contains(outPoint)) {
                        doesNotDoubleSpend = false;
                        break;
                    } else {
                        spentOutPointsInThisBlock.add(outPoint);
                    }                 
                    
                }
                if (allOutPointsAreInTheBestChain && allOutPointsAreMature && doesNotDoubleSpend) {
                    transactionsToInclude.add(tx);                    
                }
            }
            
        }	    
        return ImmutableSet.copyOf(Iterables.limit(transactionsToInclude, 1000));	        
    }

    private static class TransactionPriorityComparator implements Comparator<Transaction>{
        @Override
        public int compare(Transaction tx1, Transaction tx2) {
            int updateTimeComparison = tx1.getUpdateTime().compareTo(tx2.getUpdateTime());
            //If time1==time2, compare by tx hash to make comparator consistent with equals
            return updateTimeComparison!=0 ? updateTimeComparison : tx1.getHash().compareTo(tx2.getHash());
        }
    }
	
}