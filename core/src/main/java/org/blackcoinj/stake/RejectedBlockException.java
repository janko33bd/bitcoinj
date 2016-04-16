package org.blackcoinj.stake;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.RejectMessage;

public class RejectedBlockException extends Exception {
    private Block block;
    private RejectMessage rejectMessage;

    public RejectedBlockException(Block block, RejectMessage rejectMessage) {
        super(rejectMessage.toString());
        this.block = block;
        this.rejectMessage = rejectMessage;
    }

    /** Return the original Transaction object whose broadcast was rejected. */
    public Block getBlock() { return block; }

    /** Return the RejectMessage object representing the broadcast rejection. */
    public RejectMessage getRejectMessage() { return rejectMessage; }
}
