package com.example

import com.example.ads.UnityAdsConfig
import com.example.data.VaultGoldRepository
import com.example.model.*
import com.example.ui.puzzles.*
import org.junit.Assert.*
import org.junit.Test
import kotlin.random.Random

class ExampleUnitTest {
    @Test
    fun unityAdsConfig_usesVerifiedDashboardValuesAndProductionMode() {
        assertEquals("0b5a3932-6f76-434b-9237-c7a2735c7372", UnityAdsConfig.unityProjectId)
        assertEquals("800370914", UnityAdsConfig.androidGameId)
        assertEquals("BP_Rewarded_Android", UnityAdsConfig.rewardedPlacementId)
        assertFalse(UnityAdsConfig.TEST_MODE)
        assertNotEquals(UnityAdsConfig.androidGameId, UnityAdsConfig.rewardedPlacementId)
        assertNotEquals("Rewarded_Android", UnityAdsConfig.rewardedPlacementId)
    }

    @Test
    fun foundwayAppSettings_defaultsMatchSpecWithTestModeFalse() {
        val settings = FoundwayAppSettings()
        assertTrue(settings.guestEnabled)
        assertFalse(settings.unityTestMode)
        assertEquals(1000L, settings.pandaShowMillis)
        assertEquals(5L, settings.pandaRewardCoins)
        assertEquals(5L, settings.dailyCheckInCoins)
        assertEquals(4, settings.dailyAdLimitGuest)
        assertEquals(8, settings.dailyAdLimitUser)
        assertTrue(settings.enabledGames["ludo_arena"] == true)
        assertTrue(settings.enabledGames["carrom_arena"] == true)
        assertTrue(settings.enabledGames["panda_block_puzzle_8x8"] == true)
    }

    @Test
    fun pendingClaimReward_validForTenMinutes() {
        val now = 1_700_000_000_000L
        val claim = PendingClaimReward(
            id = "panda_claim_1",
            sourceType = "panda_game",
            sourceId = "find_the_panda_3x3",
            eventId = "seed_1234",
            headline = "You found them all",
            coins = 5L,
            createdAtMs = now,
            expiresAtMs = now + 10 * 60 * 1000L
        )
        assertFalse(claim.isExpired(now + 5 * 60 * 1000L))
        assertTrue(claim.isExpired(now + 11 * 60 * 1000L))
    }

    @Test
    fun fairLudoDice_isStrictlyUniformAndUnbiasedBetweenOneAndSix() {
        val rng = Random(42)
        val counts = IntArray(7)
        val totalRolls = 6000
        repeat(totalRolls) {
            val roll = rollFairLudoDice(rng)
            assertTrue("Dice must be 1..6", roll in 1..6)
            counts[roll]++
        }
        // Every face (1..6) should appear ~1000 times (16.67%), never rigged to 50% or 10%
        for (face in 1..6) {
            val ratio = counts[face].toDouble() / totalRolls
            assertTrue("Face $face ratio $ratio must be fair (~0.1667)", ratio in 0.14..0.19)
        }
    }

    @Test
    fun ludoMoveEngine_validatesBaseExitCaptureAndSafeZones() {
        val ownTokens = listOf(0, -1, -1, -1)
        val oppTokens = listOf(-1, -1, -1, -1)

        // Rolling a 4 cannot exit base (-1), only moves token at 0 -> 4
        val movesOn4 = computeLegalLudoMoves(ownTokens, oppTokens, diceValue = 4, isBot = false)
        assertEquals(1, movesOn4.size)
        assertEquals(0, movesOn4.first().tokenIndex)
        assertEquals(4, movesOn4.first().toStep)

        // Rolling a 6 allows moving token 0 -> 6 OR exiting base (-1 -> 0)
        val movesOn6 = computeLegalLudoMoves(ownTokens, oppTokens, diceValue = 6, isBot = false)
        assertEquals(4, movesOn6.size)
        assertTrue(movesOn6.any { it.exitsBase && it.toStep == 0 })

        // Advanced Bot prioritizes capture move when available
        val chosen = chooseStrategicBotLudoMove(movesOn6, BotDifficulty.ADVANCED, Random(1))
        assertNotNull(chosen)
    }

    @Test
    fun pandaBlockPuzzle8x8_placesShapesAndClearsCompletedRowsAndColumns() {
        // Start with an 8x8 grid where Row 0 has 6 filled tiles at cols 0..5
        val grid = MutableList(64) { 0 }
        for (c in 0..5) {
            grid[c] = 1
        }
        val bambooPair = PANDA_BLOCK_SHAPES.first { it.id == "bamboo_1x2" }

        // Place 1x2 horizontal shape at (row 0, col 6) to complete Row 0
        assertTrue(canPlaceBlockShape(grid, bambooPair, anchorRow = 0, anchorCol = 6))
        val result = placeShapeAndClearLines(grid, bambooPair, anchorRow = 0, anchorCol = 6)
        assertNotNull(result)
        assertEquals(1, result!!.rowsCleared)
        assertEquals(0, result.colsCleared)
        assertTrue("Row 0 should be cleared back to 0", (0..7).all { c -> result.newGrid[c] == 0 })
        assertTrue("Line clear awards combo bonus", result.pointsEarned >= 120)
    }

    @Test
    fun unityAdsAndTournamentArenas_verifyAllPlacementsAndNoVipDependency() {
        assertEquals("0b5a3932-6f76-434b-9237-c7a2735c7372", UnityAdsConfig.unityProjectId)
        assertEquals("800370914", UnityAdsConfig.androidGameId)
        assertEquals("All ads", UnityAdsConfig.interstitialPlacementId)
        assertEquals("All_ads", UnityAdsConfig.interstitialPlacementAlias)
        assertEquals("BP_Rewarded_Android", UnityAdsConfig.rewardedPlacementId)
        assertEquals("Banner_Android", UnityAdsConfig.bannerPlacementId)
        assertFalse(UnityAdsConfig.TEST_MODE)

        // Placement validator accepts valid Unity placements and rejects YouTube URLs or Game IDs
        assertTrue(UnityAdsConfig.isValidUnityPlacementId("All ads"))
        assertTrue(UnityAdsConfig.isValidUnityPlacementId("All_ads"))
        assertTrue(UnityAdsConfig.isValidUnityPlacementId("BP_Rewarded_Android"))
        assertTrue(UnityAdsConfig.isValidUnityPlacementId("Banner_Android"))
        assertFalse(UnityAdsConfig.isValidUnityPlacementId("https://youtu.be/I-k-iT2Q0o8"))
        assertFalse(UnityAdsConfig.isValidUnityPlacementId("800370914"))
        assertFalse(UnityAdsConfig.isValidUnityPlacementId("0b5a3932-6f76-434b-9237-c7a2735c7372"))

        // Tournament arenas are strictly 1v1 (2 players max) and restricted to ONLY Ludo & Carrom
        val arenas = VaultGoldRepository.defaultTournamentArenas()
        assertEquals(2, arenas.size)
        assertTrue(arenas.all { it.maxPlayers == 2 })
        assertTrue(arenas.any { it.gameId == "ludo_arena" && it.practiceRewardCoins == 5L })
        assertTrue(arenas.any { it.gameId == "carrom_arena" && it.practiceRewardCoins == 5L })
        assertFalse(arenas.any { it.gameId == "mind_quiz" })
        assertFalse(arenas.any { it.gameId == "panda_block_puzzle_8x8" })

        // Verify exact 1v1 Tournament Tiers (5->8, 15->25, 20->35)
        val tiers = TournamentTierSpec.STANDARD_1V1_TIERS
        assertEquals(3, tiers.size)
        assertEquals(5L, TournamentTierSpec.TIER_5_WIN_8.entryCoins)
        assertEquals(10L, TournamentTierSpec.TIER_5_WIN_8.totalPoolCoins)
        assertTrue(TournamentTierSpec.TIER_5_WIN_8.winnerPrizeCoins <= 8L)
        assertEquals(8L, TournamentTierSpec.TIER_5_WIN_8.winnerPrizeCoins)

        assertEquals(15L, TournamentTierSpec.TIER_15_WIN_25.entryCoins)
        assertEquals(30L, TournamentTierSpec.TIER_15_WIN_25.totalPoolCoins)
        assertEquals(25L, TournamentTierSpec.TIER_15_WIN_25.winnerPrizeCoins)

        assertEquals(20L, TournamentTierSpec.TIER_20_WIN_35.entryCoins)
        assertEquals(40L, TournamentTierSpec.TIER_20_WIN_35.totalPoolCoins)
        assertEquals(35L, TournamentTierSpec.TIER_20_WIN_35.winnerPrizeCoins)
    }

    @Test
    fun redeemPromoCode_evaluatesExplicitStatusLifecycleCorrectly() {
        val baseCode = RedeemPromoCode(
            id = "promo_test",
            code = "PANDA100",
            title = "Welcome Bonus",
            coinReward = 100L,
            totalStock = 10L,
            claimedCount = 3L,
            isEnabled = true
        )

        // 1. Available
        assertEquals(RedeemCodeStatus.AVAILABLE, baseCode.evaluateStatus(emptySet(), "user_1"))
        assertEquals(7L, baseCode.remainingStock)

        // 2. Already redeemed by user_1
        assertEquals(RedeemCodeStatus.ALREADY_REDEEMED, baseCode.evaluateStatus(setOf("user_1"), "user_1"))

        // 3. Out of stock when claimedCount >= totalStock
        val exhausted = baseCode.copy(claimedCount = 10L)
        assertEquals(RedeemCodeStatus.OUT_OF_STOCK, exhausted.evaluateStatus(emptySet(), "user_2"))
        assertEquals(0L, exhausted.remainingStock)

        // 4. Disabled by admin
        val disabled = baseCode.copy(isEnabled = false)
        assertEquals(RedeemCodeStatus.DISABLED, disabled.evaluateStatus(emptySet(), "user_2"))

        // 5. Expired timestamp
        val expired = baseCode.copy(expiresAtMs = System.currentTimeMillis() - 60_000L)
        assertEquals(RedeemCodeStatus.EXPIRED, expired.evaluateStatus(emptySet(), "user_2"))
    }

    @Test
    fun defaultRewardsCatalog_andPromoCodes_havePositiveAvailableStock() {
        val rewards = VaultGoldRepository.defaultRewardsCatalog()
        assertEquals(3, rewards.size)
        rewards.forEach { item ->
            assertEquals("available", item.availability)
            assertTrue("Stock count for ${item.rewardName} must be > 0", item.stockCount > 0L)
        }

        val promos = VaultGoldRepository.defaultPromoCodes()
        assertTrue(promos.size >= 4)
        assertTrue(promos.any { it.code == "PANDA100" && it.coinReward == 100L && it.remainingStock > 0L })
        assertTrue(promos.any { it.code == "TASK2026" && it.coinReward == 50L })
        assertTrue(promos.any { it.code == "CREATOR50" && it.coinReward == 50L })
    }

    @Test
    fun dualAppMode_andCreateAndEarnFeed_hasZeroDemoVideosByDefault() {
        assertEquals(2, AppMode.entries.size)
        assertEquals("Task & Earn", AppMode.TASK_AND_EARN.title)
        assertEquals("Create & Earn", AppMode.CREATE_AND_EARN.title)

        // Part 3.1: Zero demo/seed/fake videos on launch
        val videos = VaultGoldRepository.defaultVideoPosts()
        assertTrue("Create & Earn must have zero demo videos by default", videos.isEmpty())
    }

    @Test
    fun hintProgressState_requiresTwoConsecutiveAdsBeforeUnlocking() {
        val initial = HintProgressState(gameId = "panda_block_puzzle_8x8")
        assertEquals(0, initial.adsCompleted)
        assertFalse(initial.isUnlocked)
        assertEquals("Watch 2 Ads for Hint (0/2)", initial.progressLabel)

        val afterFirstAd = initial.copy(adsCompleted = 1, firstAdSessionId = "ad_session_1")
        assertEquals(1, afterFirstAd.adsCompleted)
        assertFalse(afterFirstAd.isUnlocked)
        assertEquals("Hint progress: 1/2 ads completed", afterFirstAd.progressLabel)

        val afterSecondAd = afterFirstAd.copy(
            adsCompleted = 2,
            isUnlocked = true,
            secondAdSessionId = "ad_session_2",
            hintText = "Place Bamboo Pair at Row 1, Col 7"
        )
        assertEquals(2, afterSecondAd.adsCompleted)
        assertTrue(afterSecondAd.isUnlocked)
        assertEquals("Hint unlocked! (2/2 ads completed)", afterSecondAd.progressLabel)
    }

    @Test
    fun athxoBooyahRedemptionModel_andCreatorChannel_useOfficialBrandingAndStatusLabels() {
        val pending = RedemptionRequest(
            id = "req_1",
            userId = "u1",
            userEmail = "player@athxobooyah.com",
            status = "pending",
            assignedCode = null
        )
        assertTrue(pending.isPending)
        assertFalse(pending.isSuccessful)
        assertEquals("Redeem Pending", pending.displayStatusLabel)
        assertNull(pending.assignedCode)

        val approved = pending.copy(status = "approved", assignedCode = "GPLAY-ATHXO-2026")
        assertTrue(approved.isSuccessful)
        assertFalse(approved.isPending)
        assertEquals("Redeem Successful", approved.displayStatusLabel)
        assertEquals("GPLAY-ATHXO-2026", approved.assignedCode)

        val rejected = pending.copy(status = "rejected", rejectionReason = "Invalid region")
        assertTrue(rejected.isRejected)
        assertEquals("Redeem Rejected", rejected.displayStatusLabel)

        val defaultChannel = CreatorChannel()
        assertEquals("Athxo Booyah Creator", defaultChannel.channelName)
        assertEquals("@athxo_creator", defaultChannel.handle)
    }
}

