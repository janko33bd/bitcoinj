/*
 * Copyright 2013 Google Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.bitcoinj.params;

import java.math.BigInteger;

import org.bitcoinj.core.Block;
import org.bitcoinj.core.Coin;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.Utils;
import org.bitcoinj.utils.MonetaryFormat;
import org.blackcoinj.pos.BlackcoinMagic;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStore;
import org.bitcoinj.store.BlockStoreException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Parameters for Bitcoin-like networks.
 */
public abstract class AbstractBitcoinNetParams extends NetworkParameters {
    /**
     * Scheme part for Bitcoin URIs.
     */
    public static final String BITCOIN_SCHEME = "bitcoin";

    private static final Logger log = LoggerFactory.getLogger(AbstractBitcoinNetParams.class);

    public AbstractBitcoinNetParams() {
        super();
    }

    /** 
     * Checks if we are at a difficulty transition point. 
     * @param storedPrev The previous stored block 
     * @return If this is a difficulty transition point 
     */
    protected boolean isDifficultyTransitionPoint(StoredBlock storedPrev) {
        return ((storedPrev.getHeight() + 1) % this.getInterval()) == 0;
    }

    @Override
    public void checkDifficultyTransitions(final StoredBlock storedPrev, final Block nextBlock,
        	final BlockStore blockStore) throws VerificationException, BlockStoreException {
    	verifyDifficulty(getNextTargetRequired(storedPrev, blockStore), nextBlock);
    }
    
    private void verifyDifficulty(BigInteger newTarget, Block nextBlock) {
        int accuracyBytes = (int) (nextBlock.getDifficultyTarget() >>> 24) - 3;
        long receivedTargetCompact = nextBlock.getDifficultyTarget();

        // The calculated difficulty is to a higher precision than received, so reduce here.
        BigInteger mask = BigInteger.valueOf(0xFFFFFFL).shiftLeft(accuracyBytes * 8);
        newTarget = newTarget.and(mask);
        long newTargetCompact = Utils.encodeCompactBits(newTarget);

        if (newTargetCompact != receivedTargetCompact)
            throw new VerificationException("Network provided difficulty bits do not match what was calculated: " +
                    newTargetCompact + " vs " + receivedTargetCompact);
      }
      
    @Override  
    public BigInteger getNextTargetRequired(StoredBlock pindexLast, final BlockStore blockStore) throws BlockStoreException {
  		BigInteger targetLimit = BlackcoinMagic.proofOfWorkLimit;
  		
  		Block prevBlock = pindexLast.getHeader();		

  		StoredBlock storedPrevPrev = blockStore.get(prevBlock.getPrevBlockHash());
  		Block prevPrevBlock = storedPrevPrev.getHeader();	

  	    int targetSpacing = BlackcoinMagic.targetSpacing2;
  	    
  	    //int64_t nActualSpacing = pindexPrev->GetBlockTime() - pindexPrevPrev->GetBlockTime();
  	    int actualSpacing = (int) (prevBlock.getTimeSeconds() - prevPrevBlock.getTimeSeconds());
  	    
  	    if  (pindexLast.getHeight() > BlackcoinMagic.protocolV1RetargetingFixed) {
  	    	if (actualSpacing < 0)
  		    	actualSpacing = targetSpacing;
  	    }
  	    
  	    //nTime > 1444028400;
  	    if (pindexLast.getHeader().getTimeSeconds() > BlackcoinMagic.txTimeProtocolV3) {
  	        if (actualSpacing > targetSpacing * 10)
  	        	actualSpacing = targetSpacing * 10;
  	    }	    
  	    
  	    // ppcoin: target change every block
  	    // ppcoin: retarget with exponential moving toward target spacing	  
  	    // int64_t nInterval = nTargetTimespan / nTargetSpacing;
  	    int interval = BlackcoinMagic.targetTimespan / targetSpacing;
  	    //bnNew.SetCompact(pindexPrev->nBits);
  	    BigInteger newDifficulty = Utils.decodeCompactBits(prevBlock.getDifficultyTarget());
  	   
  	    
  	    //bnNew *= ((nInterval - 1) * nTargetSpacing + nActualSpacing + nActualSpacing);
  	    //bnNew /= ((nInterval + 1) * nTargetSpacing);
  	    int multiplier = ((interval - 1) * targetSpacing + actualSpacing + actualSpacing);
  	    int divider = ((interval + 1)  * targetSpacing);
          newDifficulty = newDifficulty.multiply(BigInteger.valueOf(multiplier));
          newDifficulty = newDifficulty.divide(BigInteger.valueOf(divider));
          
  		if (newDifficulty.compareTo(BigInteger.ZERO) <= 0 
  			|| newDifficulty.compareTo(targetLimit) > 0){
  			return targetLimit;
  		}
  	    	
  		else
  			return newDifficulty;
  	    
  	}

    @Override
    public Coin getMaxMoney() {
        return MAX_MONEY;
    }

    @Override
    public Coin getMinNonDustOutput() {
        return Transaction.MIN_NONDUST_OUTPUT;
    }

    @Override
    public MonetaryFormat getMonetaryFormat() {
        return new MonetaryFormat();
    }

    @Override
    public String getUriScheme() {
        return BITCOIN_SCHEME;
    }

    @Override
    public boolean hasMaxMoney() {
        return true;
    }
}
