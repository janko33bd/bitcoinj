package org.blackcoinj.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import org.bitcoinj.core.Address;
import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.StoredUndoableBlock;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.UTXOProviderException;
import org.bitcoinj.core.Utils;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;
import org.blackcoinj.pos.BlackcoinMagic;
import org.fusesource.leveldbjni.JniDBFactory;
import org.iq80.leveldb.DB;
import org.iq80.leveldb.DBIterator;
import org.iq80.leveldb.Options;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class LevelDBStoreFullPrunedBlackstore implements FullPrunedBlockStore {
	private static final Logger log = LoggerFactory.getLogger(LevelDBStoreFullPrunedBlackstore.class);
	private DB store;
	private StoredBlock chainHead;
	private NetworkParameters params;
	private StoredBlock verifiedChainHead;

	private final String VERIFIED_CHAINHEAD = "VERIFIED_CHAINHEAD";
	private final long started;
	

	public LevelDBStoreFullPrunedBlackstore(NetworkParameters params, String dbName) throws BlockStoreException {
		this.params = params;
		Options options = new Options();
		options.createIfMissing(true);
		try {
			store = JniDBFactory.factory.open(new File(dbName), options);
		} catch (IOException e) {
			throw new BlockStoreException(e);
		}
		
		byte[] verifiedChainHeadbytes = store.get(VERIFIED_CHAINHEAD.getBytes());

		if (verifiedChainHeadbytes != null) {
			Sha256Hash hash = Sha256Hash.wrap(verifiedChainHeadbytes);
			StoredBlock headBlock = get(hash);
			this.chainHead = headBlock;
			this.verifiedChainHead = headBlock;
		}
		
		started = Utils.currentTimeSeconds();

	}

	@Override
	public void put(StoredBlock block) throws BlockStoreException {
		insertOrUpdate(block, null);
	}

	private void insertOrUpdate(StoredBlock block, StoredUndoableBlock undoableBlock) throws BlockStoreException {
		Sha256Hash hash = block.getHeader().getHash();
		byte[] byteBlack = store.get(hash.getBytes());
		BlackBlock blackBlock = new BlackBlock(block, false, null, null);
		if (byteBlack != null) {
			blackBlock = new BlackBlock(params, byteBlack);			
		}
		if(undoableBlock != null){
			blackBlock.wasUndoable = true;
			blackBlock.txOutChanges = undoableBlock.getTxOutChanges();
			blackBlock.transactions = undoableBlock.getTransactions();
		}else{
			blackBlock.wasUndoable = false;
		}
		store.put(hash.getBytes(), blackBlock.toByteArray());
	}

	@Override
	public StoredBlock get(Sha256Hash hash) throws BlockStoreException {
		byte[] bytesBlack = store.get(hash.getBytes());
		if (bytesBlack == null) {
			return null;
		}

		BlackBlock storedBlock = new BlackBlock(params, bytesBlack);
		return storedBlock.block;
	}

	@Override
	public StoredBlock getChainHead() throws BlockStoreException {
		return chainHead;
	}

	@Override
	public void setChainHead(StoredBlock chainHead) throws BlockStoreException {
		this.chainHead = chainHead;
	}

	@Override
	public void close() throws BlockStoreException {
		if(Utils.currentTimeSeconds() - started > BlackcoinMagic.blockTime)
			removeAll();

		try {
			store.close();
		} catch (IOException e) {
			throw new BlockStoreException(e);
		}
	}

	@Override
	public NetworkParameters getParams() {
		return params;
	}

	@Override
	public List<UTXO> getOpenTransactionOutputs(List<Address> addresses) throws UTXOProviderException {
		List<UTXO> foundOutputs = new ArrayList<UTXO>();
		
		DBIterator iterator = store.iterator();
		try {
		  for(iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
		    
			  byte[] output = iterator.peekNext().getValue();
			  if(output[0] == 1){
					continue;
				}	
				for (Address address : addresses) {
					ByteArrayInputStream in = new ByteArrayInputStream(output);
					UTXO outUTXO;
					try {
						outUTXO = new UTXO(in);
					} catch (IOException e) {
						throw new UTXOProviderException(e);
					}
					if (outUTXO.getAddress().equals(address.toString())) {
						foundOutputs.add(outUTXO);
					}
				}
		  }
		} finally {
		  // Make sure you close the iterator to avoid resource leaks.
		  try {
			iterator.close();
		} catch (IOException e) {
			throw new UTXOProviderException(e);
		}
		}
		return foundOutputs;
	}

	@Override
	public int getChainHeadHeight() throws UTXOProviderException {
		try {
			return getVerifiedChainHead().getHeight();
		} catch (BlockStoreException e) {
			throw new UTXOProviderException(e);
		}
	}

	@Override
	public void put(StoredBlock storedBlock, StoredUndoableBlock undoableBlock) throws BlockStoreException {
		
		insertOrUpdate(storedBlock, undoableBlock);
	}

	@Override
	public StoredBlock getOnceUndoableStoredBlock(Sha256Hash hash) throws BlockStoreException {
		byte[] byteBlock = store.get(hash.getBytes());
		if (byteBlock == null) {
			return null;
		}
		BlackBlock storedBlock = new BlackBlock(params, byteBlock);
		return storedBlock.wasUndoable ? storedBlock.block : null;
	}

	@Override
	public StoredUndoableBlock getUndoBlock(Sha256Hash hash) throws BlockStoreException {
		byte[] bytes = store.get(hash.getBytes());
		BlackBlock recoveredObj = new BlackBlock(params, bytes);
		if (recoveredObj.transactions == null) {
			return new StoredUndoableBlock(hash, recoveredObj.txOutChanges);
		}
		return new StoredUndoableBlock(hash, recoveredObj.transactions);
	}

	@Override
	public UTXO getTransactionOutput(Sha256Hash hash, long index) throws BlockStoreException {
		String point = new String(hash.toString() + ":" + String.valueOf(index));
		byte[] byteUTXO = store.get(point.getBytes());
		if (byteUTXO == null) {
			return null;
		}
		ByteArrayInputStream in = new ByteArrayInputStream(byteUTXO);
		try {
			return new UTXO(in);
		} catch (IOException e) {
			throw new BlockStoreException(e);
		}
	}

	@Override
	public void addUnspentTransactionOutput(UTXO out) throws BlockStoreException {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		try {
			out.serializeToStream(bos);
		} catch (IOException e) {
			throw new BlockStoreException(e);
		}
		String point = new String(out.getHash().toString() + ":" + String.valueOf(out.getIndex()));
		store.put(point.getBytes(), bos.toByteArray());
	}

	@Override
	public void removeUnspentTransactionOutput(UTXO out) throws BlockStoreException {
		String point = new String(out.getHash().toString() + ":" + String.valueOf(out.getIndex()));
		if (store.get(point.getBytes()) == null)
			throw new BlockStoreException(
					"Tried to remove a UTXO from MemoryFullPrunedBlockStore that it didn't have!");
		store.delete(point.getBytes());

	}

	@Override
	public boolean hasUnspentOutputs(Sha256Hash hash, int numOutputs) throws BlockStoreException {
		for (int i = 0; i < numOutputs; i++)
			if (getTransactionOutput(hash, i) != null)
				return true;
		return false;
	}

	@Override
	public StoredBlock getVerifiedChainHead() throws BlockStoreException {
		return verifiedChainHead;
	}

	@Override
	public void setVerifiedChainHead(StoredBlock chainHead) throws BlockStoreException {
		this.verifiedChainHead = chainHead;
		if (this.chainHead.getHeight() < chainHead.getHeight())
			setChainHead(chainHead);
		store.put(VERIFIED_CHAINHEAD.getBytes(), getChainHead().getHeader().getHash().getBytes());
	}

	private void removeAll() throws BlockStoreException {
		log.info("removing all");
		int theHeight = chainHead.getHeight() - BlackcoinMagic.minimumStoreDepth;
		DBIterator iterator = store.iterator();
		try {
		  for(iterator.seekToFirst(); iterator.hasNext(); iterator.next()) {
		    
			  byte[] output = iterator.peekNext().getValue();
			  byte[] key = iterator.peekNext().getKey();
			  if(output[0] == 1){
				  try {
						BlackBlock storedBlock = new BlackBlock(params, output);
						
						if(storedBlock.block.getHeight() < theHeight)
							store.delete(key);
					} catch (BlockStoreException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
				}else{
					continue;
				}
			}
		} finally {
		  // Make sure you close the iterator to avoid resource leaks.
		  try {
			iterator.close();
		} catch (IOException e) {
			throw new BlockStoreException(e);
		}
		}
		
	}

	@Override
	public void beginDatabaseBatchWrite() throws BlockStoreException {
		// TODO Auto-generated method stub

	}

	@Override
	public void commitDatabaseBatchWrite() throws BlockStoreException {
		// TODO Auto-generated method stub

	}

	@Override
	public void abortDatabaseBatchWrite() throws BlockStoreException {
		// TODO Auto-generated method stub

	}

	@Override
	public void setTheLast(Sha256Hash lastThird) {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void putCheckPointed(StoredBlock block) throws BlockStoreException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void compact() {
		// TODO Auto-generated method stub
		
	}

}
