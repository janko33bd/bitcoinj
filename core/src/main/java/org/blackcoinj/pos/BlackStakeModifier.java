package org.blackcoinj.pos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class BlackStakeModifier {
	private static final Logger log = LoggerFactory.getLogger(BlackStakeModifier.class);	
	
	public void setBlackCoinStake(StoredBlock prevBlock, Block newBlock) throws BlockStoreException {
    	// GetBlockTrust (const)
    	//setChainTrust(prevBlock, newBlock);
    	setStakeEntropyBit(newBlock);
    	//ComputeStakeModifierV2(pindexNew->pprev, IsProofOfWork() ? hash : vtx[1].vin[0].prevout.hash);
    	Sha256Hash stakeModifier2 = computeStakeModifierV2(prevBlock, newBlock.getTransactions().get(1).getInput(0).getOutpoint().getHash());
    		
	    newBlock.setStakeModifier2(stakeModifier2);
    	//
	}
	
	private void setStakeEntropyBit(Block newBlock) {
		//unsigned int nEntropyBit = ((GetHash().Get64()) & 1llu);
		long lastBit = newBlock.getHash()
				.toBigInteger().and(BigInteger.ONE).longValue();
		newBlock.setEntropyBit(lastBit);
	}

	private Sha256Hash computeStakeModifierV2(StoredBlock prevBlock, Sha256Hash kernel) throws BlockStoreException {
		
		ByteArrayOutputStream ssStakeStream = new UnsafeByteArrayOutputStream(32+32);
		byte[] stakeModifier2 = prevBlock.getHeader().getStakeModifier2().getBytes();
		try {
			// ss << kernel << pindexPrev->bnStakeModifierV2;
			ssStakeStream.write(Utils.reverseBytes(kernel.getBytes()));
			ssStakeStream.write(Utils.reverseBytes(stakeModifier2));
		} catch (IOException e) {
			throw new BlockStoreException(e.getMessage());
		} finally {
			try {
				ssStakeStream.close();
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		return Sha256Hash.wrapReversed(Sha256Hash.hashTwice((ssStakeStream.toByteArray())));
	}

}
