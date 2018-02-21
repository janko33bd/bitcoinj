package org.blackcoinj.store;

import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.nio.ByteBuffer;
import java.security.DigestOutputStream;
import java.security.MessageDigest;
import java.util.TreeMap;

import org.bitcoinj.core.CheckpointManager;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.UTXO;
import com.google.common.base.Charsets;

public class CheckpointBlkPOSBuilder {
	private static final File PLAIN_CHECKPOINTS_FILE = new File("C:/MY/blackcoinj/blackcoinj/examples/checkpointsblk");
    private static final File TEXTUAL_CHECKPOINTS_FILE = new File("C:/MY/blackcoinj/blackcoinj/examples/checkpointsblk.txt");
    
    private static final File PLAIN_CHECKPOINTS_TX_FILE = new File("C:/MY/blackcoinj/blackcoinj/examples/checkpointstx");
    private static final File TEXTUAL_CHECKPOINTS_TX_FILE = new File("C:/MY/blackcoinj/blackcoinj/examples/checkpointstx.txt");
    
    final TreeMap<Integer, StoredBlock> checkpoints = new TreeMap<Integer, StoredBlock>();
    final TreeMap<Integer, UTXO> checkpointstx = new TreeMap<Integer, UTXO>();
    
    public void putTX(UTXO blkTx, Integer count){
    		checkpointstx.put(count, blkTx);
    }
    
    public void putBlock(int height, StoredBlock block){ 	
    	checkpoints.put(height, block);
    
    }
    
    public void saveToFile(){
    	try {
			writeBinaryCheckpoints(checkpoints, PLAIN_CHECKPOINTS_FILE);
			writeTextualCheckpoints(checkpoints, TEXTUAL_CHECKPOINTS_FILE);
			
			writeBinaryCheckpointsTx(checkpointstx, PLAIN_CHECKPOINTS_TX_FILE);
			writeTextualCheckpointsTx(checkpointstx, TEXTUAL_CHECKPOINTS_TX_FILE);
		} catch (Exception e) {
			throw new RuntimeException(e);
		}
        
    }

	private static void writeBinaryCheckpoints(TreeMap<Integer, StoredBlock> checkpoints2, File file) throws Exception {
        final FileOutputStream fileOutputStream = new FileOutputStream(file, false);
        MessageDigest digest = Sha256Hash.newDigest();
        final DigestOutputStream digestOutputStream = new DigestOutputStream(fileOutputStream, digest);
        digestOutputStream.on(false);
        final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream);
        dataOutputStream.writeBytes("CHECKPOINTS 1");
        dataOutputStream.writeInt(0);  // Number of signatures to read. Do this later.
        digestOutputStream.on(true);
        dataOutputStream.writeInt(checkpoints2.size());
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_BLK_SIZE);
        for (StoredBlock block : checkpoints2.values()) {
        	block.serializeCompact(buffer);
        	dataOutputStream.write(buffer.array());
            buffer.position(0);
        }
        dataOutputStream.close();
        Sha256Hash checkpointsHash = Sha256Hash.wrap(digest.digest());
        System.out.println("Hash of checkpoints data is " + checkpointsHash);
        digestOutputStream.close();
        fileOutputStream.close();
        System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
    }

    private static void writeTextualCheckpoints(TreeMap<Integer, StoredBlock> checkpoints2, File file) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), Charsets.US_ASCII));
        writer.println("TXT CHECKPOINTS 1");
        writer.println("0"); // Number of signatures to read. Do this later.
        writer.println(checkpoints2.size());
        ByteBuffer buffer = ByteBuffer.allocate(StoredBlock.COMPACT_SERIALIZED_BLK_SIZE);
        for (StoredBlock block : checkpoints2.values()) {
           	block.serializeCompact(buffer);
			writer.println(CheckpointManager.BASE64.encode(buffer.array()));			
        }
        writer.close();
        System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
    }
    
    private static void writeBinaryCheckpointsTx(TreeMap<Integer, UTXO> checkpointstx, File file) throws Exception {
        final FileOutputStream fileOutputStream = new FileOutputStream(file, false);
        MessageDigest digest = Sha256Hash.newDigest();
        final DigestOutputStream digestOutputStream = new DigestOutputStream(fileOutputStream, digest);
        digestOutputStream.on(false);
        final DataOutputStream dataOutputStream = new DataOutputStream(digestOutputStream);
        dataOutputStream.writeBytes("CHECKPOINTS 1");
        dataOutputStream.writeInt(0);  // Number of signatures to read. Do this later.
        digestOutputStream.on(true);
        dataOutputStream.writeInt(checkpointstx.size());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (UTXO tx : checkpointstx.values()) {
        	tx.serializeToStream(buffer);
        	dataOutputStream.writeInt(buffer.size());
            dataOutputStream.write(buffer.toByteArray());
            buffer.reset();
        }
        dataOutputStream.close();
        Sha256Hash checkpointsHash = Sha256Hash.wrap(digest.digest());
        System.out.println("Hash of checkpoints data is " + checkpointsHash);
        digestOutputStream.close();
        fileOutputStream.close();
        System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
    }

    private static void writeTextualCheckpointsTx(TreeMap<Integer, UTXO> checkpointstx2, File file) throws IOException {
        PrintWriter writer = new PrintWriter(new OutputStreamWriter(new FileOutputStream(file), Charsets.US_ASCII));
        writer.println("TXT CHECKPOINTS 1");
        writer.println("0"); // Number of signatures to read. Do this later.
        writer.println(checkpointstx2.size());
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        for (UTXO tx : checkpointstx2.values()) {
            tx.serializeToStream(buffer);
            writer.println(CheckpointManager.BASE64.encode(buffer.toByteArray()));
            buffer.reset();
        }
        writer.close();
        System.out.println("Checkpoints written to '" + file.getCanonicalPath() + "'.");
    }

	public TreeMap<Integer, StoredBlock> getBlockMap() {
		return checkpoints;
	}
}
