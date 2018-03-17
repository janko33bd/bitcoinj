/**
 * 
 */
package org.blackcoinj.store;

import static org.junit.Assert.*;

import java.io.File;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.Map;

import org.bitcoinj.core.StoredBlock;
//import org.bitcoinj.core.StoredBlockXT;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutputChanges;
import org.bitcoinj.params.MainNetParams;
import org.bitcoinj.store.BlockStoreException;
import org.iq80.leveldb.DB;
import org.junit.Test;

/**
 * @author janko33
 *
 */
public class BlackstoreTransformer {
	private final String CHAINHEAD = "CHAINHEAD";
	private final String VERIFIED_CHAINHEAD = "VERIFIED_CHAINHEAD";
	private final String THE_LAST = "THE_LAST";

	@Test
	public void copyAndReduceChain() {
		MainNetParams mainNetParams = MainNetParams.get();
		KofemeFullPrunedBlockstore kofemeStore = null;
		File chainFile = new File("C:\\Data\\Dev\\bitcoinjTree\\mia\\examples\\chain.db");
		try {
			kofemeStore = new KofemeFullPrunedBlockstore(mainNetParams, chainFile.getPath());
		} catch (BlockStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		File LevelchainFile = new File("C:\\Data\\Dev\\bitcoinjTree\\mia\\examples\\chainLevel.db");
		LevelDBStoreFullPrunedBlackstore levelDBStore = null;
		try {
			levelDBStore = new LevelDBStoreFullPrunedBlackstore(mainNetParams, LevelchainFile.getPath());
		} catch (BlockStoreException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		Map<ByteArrayWrapper, byte[]> wholeMap = kofemeStore.getWholeMap();
		DB store = levelDBStore.getStore();
		ByteArrayWrapper utxoIdentifyFlag = new ByteArrayWrapper(new byte[] { (byte)0});
		store.put(VERIFIED_CHAINHEAD.getBytes(), wholeMap.get(new ByteArrayWrapper(VERIFIED_CHAINHEAD.getBytes())));
		wholeMap.forEach((k,v)->{
			if (new ByteArrayWrapper(CHAINHEAD.getBytes()).equals(k) ||
			new ByteArrayWrapper(THE_LAST.getBytes()).equals(k) ||
			new ByteArrayWrapper(VERIFIED_CHAINHEAD.getBytes()).equals(k)) 
				return;
			
			ByteBuffer buffer = ByteBuffer.wrap(v);
			byte[] identifyFlag = new byte[1];
	    	buffer.get(identifyFlag);
			if(utxoIdentifyFlag.equals(new ByteArrayWrapper(identifyFlag))) {
				store.put(k.data, v);
			}else {
				try {
					BlackBlock storedBlackBlock = new BlackBlock(mainNetParams, v);
					StoredBlock storedBlock = storedBlackBlock.block;
//					StoredBlockXT storedBlockXT = new StoredBlockXT(storedBlock.getHeader(), storedBlock.getChainWork(), storedBlock.getHeight());
//					BlackBlockXT storedBlackBlockXT = new BlackBlockXT(storedBlockXT, storedBlackBlock.wasUndoable, storedBlackBlock.txOutChanges,
//							storedBlackBlock.transactions);
//					store.put(k.data,storedBlackBlockXT.toByteArray());
					
				} catch (BlockStoreException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}
			
		});
	}

}
