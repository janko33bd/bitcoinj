package org.blackcoinj.pos;

import java.math.BigInteger;

import org.bitcoinj.core.Utils;

public class BlackcoinMagic {
	
	///main.cpp#L2521 nBits=1e0fffff
	public static final long genesisDifficultyTarget = (0x1e0fffffL);
	
	public static final BigInteger proofOfWorkLimit = Utils.decodeCompactBits(0x1b00ffffL);
	//base58.h#L279 PUBKEY_ADDRESS = 25
	public static final int addressHeader = 25;
	//common to all coins
	public static final int bulgarianConst = 128;
	//base58.h#L280 SCRIPT_ADDRESS = 85
	public static final int p2shHeader = 85;
	//protocol.h#L21 return testnet ? 25714 : 15714;
	public static final int port = 15714;
	//main.cpp#L2822 pchMessageStart[4] = { 0x70, 0x35, 0x22, 0x05 };
	public static final long packetMagic = 0x70352205L;
	//clientmodel.cpp#L56 fromTime_t(1393221600); // Genesis block's time
	public static final long time = 1393221600;
	//main.cpp#L2521 nNonce=164482
	public static final long nonce = 164482;
	//main.cpp#L48 nCoinbaseMaturity = 500
	public static final int spendableCoinbaseDepth = 500;
	public static final String checkpoint0 = "000001faef25dec4fbcf906e6242621df2c183bf232f263d0ba5b101911e4563";
	// hardfork(872456)
	public static final String checkpoint1 = "e4fd321ced1de06213d2e246b150b4bfd8c4aa0989965dce88f2a58668c64860";
	
	
	public static final String dnsSeed0 = "rat4.blackcoin.co";
	public static final String dnsSeed1 = "seed.blackcoin.co";
	public static final String dnsSeed2 = "syllabear.tk";
	public static final String dnsSeed3 = "bcseed.syllabear.tk";
	
	//main.cpp#L988 nTargetTimespan = 16 * 60;
	public static final int targetTimespan = 16 * 60;
	//main.cpp#L43 nTargetSpacing = 1 * 60;
	public static final int targetSpacing = 1 * 60;
	public static final int targetSpacing2 = 64;
	
	//main.cpp#L46 unsigned int nModifierInterval = 10 * 60; 
	// time to elapse before new modifier is computed
	public static final int modifierInterval = 10 * 60;
	
	public static final int interval = targetTimespan / targetSpacing;
	public static final long maxMoney = Long.MAX_VALUE;
	public static final int protocolVersion = 60018;
	public static final int minProtocolVersion = 60016;
	
	//kernel.h#L18 ratio of group interval length between the last group and the first group
	public static final int modifierIntervalRatio = 3;
	
	public static final int stakeMinAge = 8 * 60 * 60; // 8 hours
	public static final int stakeMaxAge = Integer.MAX_VALUE; // unlimited
	
	public static final long sha256BlockVersion = 7;
	public static final int minimumStoreDepth = 500;

	public static final CharSequence blackAlertSigningKey = "0486bce1bac0d543f104cbff2bd23680056a3b9ea05e1137d2ff90eeb5e08472eb500322593a2cb06fbf8297d7beb6cd30cb90f98153b5b7cce1493749e41e0284";

	public static final String genesisHashString = "00012a24323020466562203230313420426974636f696e2041544d7320636f6d6520746f20555341";

	public static final String genesisMerkleRootHashString = "12630d16a97f24b287c8c2594dda5fb98c9e6c70fc61d44191931ea2aa08dc90";

	public static final long minTxFee = 10000;

	public static final int bcpv = 0x02CFBF60;

	public static final int bcpb = 0x02CFBEDE;

	public static final long txTimeProtocolV3 = 1444028400;

	public static final int protocolV1RetargetingFixed = 38423;

	public static final int stakeMinConfirmations = 500;

	public static final long dust = 546;

	public static final long blockVersion = 7;
	// To decrease granularity of timestamp
	// Supposed to be 2^n-1
	public static final int STAKE_TIMESTAMP_MASK = 15;

	public static final long futureDrift = 15;

	public static final long minerMiliSleep = 500;

	public static final int spliStakeLimitCoins = 200;

	
}
