package org.blackcoinj.pos;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigInteger;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.bitcoinj.core.Block;
import org.bitcoinj.core.Sha256Hash;
import org.bitcoinj.core.StoredBlock;
import org.bitcoinj.core.Transaction;
import org.bitcoinj.core.TransactionInput;
import org.bitcoinj.core.TransactionOutPoint;
import org.bitcoinj.core.UTXO;
import org.bitcoinj.core.UnsafeByteArrayOutputStream;
import org.bitcoinj.core.Utils;
import org.bitcoinj.core.VerificationException;
import org.bitcoinj.store.BlockStoreException;
import org.bitcoinj.store.FullPrunedBlockStore;

public class BlackcoinPOS {
	private static final Logger log = LoggerFactory.getLogger(BlackcoinPOS.class);
	private FullPrunedBlockStore blockStore;

	public BlackcoinPOS(FullPrunedBlockStore blockStore) {
		this.blockStore = blockStore;
	}

	public Sha256Hash checkAndSetPOS(StoredBlock storedPrev, Block newBlock) throws BlockStoreException {
		return checkSetBlackCoinPOS(storedPrev, newBlock);
	}

	private Sha256Hash checkSetBlackCoinPOS(StoredBlock storedPrev, Block block) throws BlockStoreException {

		// log.info("checkinng proof of stake");
		List<Transaction> transactions = block.getTransactions();
		// CheckProofOfStake(pindexPrev, vtx[1], nBits, hashProof,
		// targetProofOfStake
		Sha256Hash stakeKernelHash = checkProofOfStake(storedPrev, transactions.get(1), block.getDifficultyTarget());
		if (stakeKernelHash == null) {
			throw new VerificationException("The proof-of-stake failed");
		} else {
			return stakeKernelHash;

			// log.info("setting stake proof on height " +
			// newBlock.getHeight());
			// log.info("stake proof=" + stakeKernelHash);
		}

	}

	private Sha256Hash checkProofOfStake(StoredBlock storedPrev, Transaction stakeTx, long target)
			throws BlockStoreException {
		// Kernel (input 0) must match the stake hash target per coin age
		// (nBits)
		TransactionInput txin = stakeTx.getInputs().get(0);
		// https://github.com/rat4/blackcoin/blob/a2e518d59d8cded7c3e0acf1f4a0d9b363b46346/src/kernel.cpp#L427
		// First try finding the previous transaction in database
		UTXO txPrev = blockStore.getTransactionOutput(txin.getOutpoint().getHash(), txin.getOutpoint().getIndex());

		// CheckStakeKernelHash(pindexPrev, nBits, block, txindex.pos.nTxPos -
		// txindex.pos.nBlockPos, txPrev,
		// txin.prevout, tx.nTime, hashProofOfStake, targetProofOfStake,
		// fDebug))
		Sha256Hash stakeKernelHash = checkStakeKernelHash(storedPrev, target, txPrev, stakeTx.getnTime(),
				txin.getOutpoint());
		if (stakeKernelHash == null)
			throw new VerificationException("Check kernel failed");
		return stakeKernelHash;
	}

	public Sha256Hash checkStakeKernelHash(StoredBlock storedPrev, long target, UTXO txPrev, long stakeTxTime,
			TransactionOutPoint prevout) throws BlockStoreException {

		StoredBlock blockFrom = null;
		// nTimeTx < txPrev.nTime
		if (stakeTxTime < txPrev.getTxTime())
			throw new VerificationException("Time violation");
		// https://github.com/rat4/blackcoin/blob/e1b26651752c2a90e8cc42005e27bae7e1544622/src/kernel.cpp#L435
		if (stakeTxTime > BlackcoinMagic.txTimeProtocolV3) {
			int nDepth = 0;
			if (IsConfirmedInNPrevBlocks(txPrev, storedPrev, BlackcoinMagic.stakeMinConfirmations - 1, nDepth))
				throw new VerificationException(
						"CheckProofOfStake() : tried to stake at depth " + String.valueOf(nDepth + 1));

		} else {
			throw new VerificationException("Wrong chain!");
		}

		// Base target
		UTXO prevOut = blockStore.getTransactionOutput(txPrev.getHash(), prevout.getIndex());
		// Weighted target
		long weight = prevOut.getValue().getValue();
		// a = a * b
		// bnTarget *= bnWeight;
		BigInteger targetPerCoinDay = Utils.decodeCompactBits(target).multiply(BigInteger.valueOf(weight));

		Sha256Hash hashProofOfStake = Sha256Hash.ZERO_HASH;
		long stakeModifier = storedPrev.getHeader().getStakeModifier();
		Sha256Hash stakeModifier2 = storedPrev.getHeader().getStakeModifier2();
		byte[] arrayHashPrevout = prevout.getHash().getBytes();
		byte[] arStakeMod = new byte[8];
		Utils.uint64ToByteArrayLE(stakeModifier, arStakeMod, 0);
		ByteArrayOutputStream ssStakeStream;
		try {
			if (stakeTxTime > BlackcoinMagic.txTimeProtocolV3) {
				ssStakeStream = new UnsafeByteArrayOutputStream(32 + 4 + 32 + 4 + 4);
				// ss << bnStakeModifierV2;
				ssStakeStream.write(Utils.reverseBytes(stakeModifier2.getBytes()));

			} else {
				throw new VerificationException("Wrong chain!");
			}
			// txPrev.nTime
			Utils.uint32ToByteStreamLE(txPrev.getTxTime(), ssStakeStream);
			// prevout.hash
			ssStakeStream.write(Utils.reverseBytes(arrayHashPrevout));
			// prevout.n
			Utils.uint32ToByteStreamLE(prevout.getIndex(), ssStakeStream);
			// nTimeTx
			Utils.uint32ToByteStreamLE(stakeTxTime, ssStakeStream);
			hashProofOfStake = Sha256Hash.wrapReversed(Sha256Hash.hashTwice(ssStakeStream.toByteArray()));

		} catch (IOException e) {
			throw new VerificationException("creating hash in checkStakeKernelHashV2 failed");
		}

		BigInteger bigNumHashProofOfStake = hashProofOfStake.toBigInteger();
		if (bigNumHashProofOfStake.compareTo(targetPerCoinDay) > 0) {

			return null;

		}
		return hashProofOfStake;
	}

	private boolean IsConfirmedInNPrevBlocks(UTXO txPrev, StoredBlock storedPrev, int i, int nDepth) {
		// TODO NOT USEFULL NOW STAKING NOT IMPLEMENTED
		return false;
	}

}