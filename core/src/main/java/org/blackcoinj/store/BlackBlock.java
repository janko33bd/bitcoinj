package org.blackcoinj.store;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.BufferUnderflowException;
import java.nio.ByteBuffer;
import java.util.LinkedList;
import java.util.List;

import org.bitcoinj.core.NetworkParameters;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionOutputChanges;
import org.bitcoinj.store.BlockStoreException;

public class BlackBlock {
	public StoredBlock block;
	public boolean wasUndoable;
	// Only one of either txOutChanges or transactions will be set
	public TransactionOutputChanges txOutChanges;
	public List<Transaction> transactions;
	
	public BlackBlock(StoredBlock block, boolean wasUndoable, TransactionOutputChanges txOutChanges,
			List<Transaction> transactions) {
		super();
		this.block = block;
		this.wasUndoable = wasUndoable;
		this.txOutChanges = txOutChanges;
		this.transactions = transactions;
	}
	public BlackBlock(NetworkParameters params, byte[] byteArray) throws BlockStoreException{
		ByteBuffer buffer = ByteBuffer.wrap(byteArray);
		byte[] identifyFlag = new byte[1];
    	buffer.get(identifyFlag);
    	this.block = StoredBlock.deserializeBlkCompact(params, buffer);
    	
    	byte undo = buffer.get();
		if (undo == 1)
			this.wasUndoable = true;
		else
			this.wasUndoable = false;
		byte isTxOut = 0;
		try{
			isTxOut = buffer.get();
		}catch (BufferUnderflowException e){
			return;
		}
		byte[] txBytes = new byte[buffer.remaining()];
		buffer.get(txBytes);
		
		if (isTxOut == 1){			
			try {
				this.txOutChanges =  new TransactionOutputChanges(new ByteArrayInputStream(txBytes));
			} catch (IOException e) {
				throw new BlockStoreException(e);
			}
		}else if(txBytes.length>0){
            this.transactions = extractTxList(params, txBytes);
		}
	}

	private List<Transaction> extractTxList(NetworkParameters params, byte[] txBytes) {
		int offset = 0;
		int numTxn = ((txBytes[offset++] & 0xFF)) |
		        ((txBytes[offset++] & 0xFF) << 8) |
		        ((txBytes[offset++] & 0xFF) << 16) |
		        ((txBytes[offset++] & 0xFF) << 24);
		List<Transaction> transactionList = new LinkedList<Transaction>();
		for (int i = 0; i < numTxn; i++) {
		    Transaction tx = new Transaction(params, txBytes, offset);
		    transactionList.add(tx);
		    offset += tx.getMessageSize();
		}
		return transactionList;
	}

	public byte[] toByteArray() throws BlockStoreException {
		//tx or txOUT?		
		byte[] txOutBytes = null;
		boolean txOut = true;
		try {
			ByteArrayOutputStream bos = new ByteArrayOutputStream();
			if (txOutChanges != null) {
				txOutChanges.serializeToStream(bos);
				txOutBytes = bos.toByteArray();
			} else if(transactions != null){
				txOut = false;
				int numTxn = transactions.size();
				bos.write(0xFF & numTxn);
				bos.write(0xFF & (numTxn >> 8));
				bos.write(0xFF & (numTxn >> 16));
				bos.write(0xFF & (numTxn >> 24));
				for (Transaction tx : transactions)
					tx.bitcoinSerialize(bos);
				txOutBytes = bos.toByteArray();
			}
			bos.close();
		} catch (IOException e) {
			throw new BlockStoreException(e);
		}
		//tx or txOUT?
		// now we know
		ByteBuffer buffer = ByteBuffer.allocate(1 + StoredBlock.COMPACT_SERIALIZED_BLK_SIZE + 1 + 1 +(txOutBytes!=null?txOutBytes.length:0) );
		
		//identifyFlag(1) + storedBlock(218) + wasUndoable(1) + txOrTxOut(1) + txOutBytes(variable)
		//identify flag 1
		byte[] identifyFlag = new byte[] { (byte)1};        
    	buffer.put(identifyFlag);
		// stored block
    	block.serializeBlkCompact(buffer);
		// undoable
    	if (this.wasUndoable)
			buffer.put((byte) 1);
		else
			buffer.put((byte) 0);
		// tx or txOUT
		if(txOutBytes!=null){
			if(txOut){
				buffer.put((byte) 1);
			}else{
				buffer.put((byte) 0);
			}
			buffer.put(txOutBytes);
		}
		// return whole
		return buffer.array();
	}

}
