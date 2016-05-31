/**
 * Copyright 2013 Google Inc.
 * Copyright 2014 Andreas Schildbach
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

package org.bitcoinj.examples;

import org.bitcoinj.core.*;
import org.bitcoinj.kits.WalletAppKit;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.bitcoinj.utils.BriefLogFormatter;
import org.blackcoinj.pos.Staker;

import com.google.common.util.concurrent.FutureCallback;
import com.google.common.util.concurrent.Futures;
import java.io.File;
import java.util.List;

/**
 * ForwardingService demonstrates basic usage of the library. It sits on the network and when it receives coins, simply
 * sends them onwards to an address given on the command line.
 */
public class StakingService {
    private static WalletAppKit kit;
	

    public static void main(String[] args) throws Exception {
    	
        // This line makes the log output more compact and easily read, especially when using the JDK log adapter.
        BriefLogFormatter.init();

        // Figure out which network we should connect to. Each one gets its own set of files.
        NetworkParameters params;
        String filePrefix;
            params = MainNetParams.get();
            filePrefix = "forwarding-service";
      
        kit = new WalletAppKit(params, new File("."), filePrefix);
        //TODO Remove
        kit.connectToLocalHost();

        // Download the block chain and wait until it's done.
        kit.startAsync();
        kit.awaitRunning();
        
        // We want to know when we receive money.
        kit.wallet().addEventListener(new AbstractWalletEventListener() {
            @Override
            public void onCoinsReceived(Wallet w, Transaction tx, Coin prevBalance, Coin newBalance) {
                // Runs in the dedicated "user thread" (see bitcoinj docs for more info on this).
                //
                // The transaction "tx" can either be pending, or included into a block (we didn't see the broadcast).
                Coin value = tx.getValueSentToMe(w);
                System.out.println("Received tx for " + value.toFriendlyString() + ": " + tx);
                System.out.println("Transaction will be forwarded after it confirms.");
                // Wait until it's made it into the block chain (may run immediately if it's already there).
                //
                // For this dummy app of course, we could just forward the unconfirmed transaction. If it were
                // to be double spent, no harm done. Wallet.allowSpendingUnconfirmedTransactions() would have to
                // be called in onSetupCompleted() above. But we don't do that here to demonstrate the more common
                // case of waiting for a block.
                Futures.addCallback(tx.getConfidence().getDepthFuture(1), new FutureCallback<TransactionConfidence>() {
                    @Override
                    public void onSuccess(TransactionConfidence result) {
                    	System.out.println("got it, printing private keys for importprivkey");
                    	List<ECKey> issuedReceiveKeys = kit.wallet().getIssuedReceiveKeys();
                    	for (ECKey code : issuedReceiveKeys) {
                    		System.out.println("private keys:" + code.getPrivateKeyEncoded(params).toString());
						}
                        //stakeCoins(params, kit.peerGroup(),kit.wallet(),kit.store(),kit.chain());
                    }

                    @Override
                    public void onFailure(Throwable t) {
                        // This kind of future can't fail, just rethrow in case something weird happens.
                        throw new RuntimeException(t);
                    }
                });
            }
        });
        
        Address sendToAddress = kit.wallet().currentReceiveKey().toAddress(params);
        System.out.println("Send coins to: " + sendToAddress);
    	List<ECKey> issuedReceiveKeys = kit.wallet().getIssuedReceiveKeys();
    	for (ECKey code : issuedReceiveKeys) {
    		System.out.println("private keys:" + code.getPrivateKeyEncoded(params).toString());
		}
    	
        System.out.println("staking..");
        List<TransactionOutput> calculateAllSpendCandidates = kit.wallet().calculateAllSpendCandidates();
        for (TransactionOutput coin : calculateAllSpendCandidates) {
    		System.out.println("coins:" + coin.getValue());
		}
        stakeCoins(params, kit.peerGroup(),kit.wallet(),kit.store(),kit.chain());
    }

    private static void stakeCoins(NetworkParameters params, PeerGroup peers, Wallet wallet, FullPrunedBlockStore store, AbstractBlockChain chain) {
       Staker staker = new Staker(params, peers, wallet, store, chain);
       try {
		staker.startAsync();
	} catch (Exception e) {
		// TODO Auto-generated catch block
		e.printStackTrace();
	}
    }
}
