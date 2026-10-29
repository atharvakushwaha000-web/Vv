package com.example

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.example.ads.UnityAdManager
import com.example.ads.UnityAdsConfig
import com.example.data.VaultGoldRepository
import com.example.model.BotDifficulty
import com.example.viewmodel.VaultGoldViewModel
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        if (FirebaseApp.getApps(context).isEmpty()) {
            FirebaseApp.initializeApp(context)
        }
    }

    @Test
    fun `verify Athxo Booyah app identity, official logo resources, and UnityAdsConfig with testMode false`() {
        val appName = context.getString(R.string.app_name)
        assertEquals("Athxo Booyah", appName)

        // Verify official Athxo Booyah logo drawable and launcher mipmaps exist and resolve
        assertNotEquals(0, R.drawable.athxo_booyah_logo)
        assertNotEquals(0, R.mipmap.ic_launcher)
        assertNotEquals(0, R.mipmap.ic_launcher_round)
        assertNotNull(context.getDrawable(R.drawable.athxo_booyah_logo))

        val databaseId = context.getString(R.string.firestore_database_id)
        assertEquals("ai-studio-android-create-d8c56134-aab9-4ec3-8ffb-84a5057f8882", databaseId)

        val adManager = UnityAdManager()
        assertEquals("800370914", adManager.gameId)
        assertEquals("BP_Rewarded_Android", adManager.placementId)
        assertEquals("All ads", adManager.interstitialPlacementId)
        assertEquals("All_ads", adManager.interstitialPlacementAlias)
        assertEquals("Banner_Android", adManager.bannerPlacementId)
        assertFalse(adManager.isTestMode)
        assertFalse(UnityAdsConfig.TEST_MODE)
        assertNotEquals(adManager.gameId, adManager.placementId)
        assertNotEquals(adManager.gameId, adManager.interstitialPlacementId)
        assertEquals("0b5a3932-6f76-434b-9237-c7a2735c7372", UnityAdsConfig.unityProjectId)
    }

    @Test
    fun `guest mode earns coins only after completed ad and blocks redeem until Google sign-in`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val guestUid = "guest_test_session_1"
        val repo = VaultGoldRepository(db = db, fallbackUserId = guestUid)

        repo.ensureUserInitialized(
            uid = guestUid,
            displayName = "Guest Tester",
            email = null,
            photoUrl = null
        )

        // 1. Winning Panda game creates a PendingClaimReward and does NOT auto-credit coins
        assertEquals(0L, repo.getLocalGuestBalance())
        val pendingClaimResult = repo.recordPuzzleWinForAdClaim(
            gameId = "find_the_panda_3x3",
            category = "Panda Memory",
            difficulty = "1s",
            score = 100L,
            movesCount = 3L,
            durationSeconds = 4L,
            seedToken = "seed_panda_round_1",
            baseRewardCoins = 5L
        )
        assertTrue(pendingClaimResult.isSuccess)
        val pendingClaim = pendingClaimResult.getOrThrow()
        assertEquals(5L, pendingClaim.coins)
        // Balance is STILL 0 before completing the ad!
        assertEquals(0L, repo.getLocalGuestBalance())

        // 2. Closing the ad early (< 2 seconds) gives 0 coins
        val adSession = repo.requestAdSession("BP_Rewarded_Android", testMode = false, rewardCoins = 5L).getOrThrow()
        assertFalse(adSession.testMode)
        val earlyCloseResult = repo.completeAdAndClaimPendingReward(
            pending = pendingClaim,
            sessionId = adSession.id,
            placementId = "BP_Rewarded_Android",
            elapsedSeconds = 0,
            isTestMode = false
        )
        assertTrue(earlyCloseResult.isFailure)
        assertEquals(0L, repo.getLocalGuestBalance())

        // 3. Completing the ad credits +5 coins once
        val claimResult = repo.completeAdAndClaimPendingReward(
            pending = pendingClaim,
            sessionId = adSession.id,
            placementId = "BP_Rewarded_Android",
            elapsedSeconds = 6,
            isTestMode = false
        )
        assertTrue(claimResult.isSuccess)
        assertEquals(5L, claimResult.getOrThrow().amount)
        assertEquals(5L, repo.getLocalGuestBalance())

        // 4. Replaying the exact same eventId gives 0 coins (rejected as duplicate)
        val replayResult = repo.completeAdAndClaimPendingReward(
            pending = pendingClaim,
            sessionId = adSession.id,
            placementId = "BP_Rewarded_Android",
            elapsedSeconds = 6,
            isTestMode = false
        )
        assertTrue(replayResult.isFailure)
        assertEquals(5L, repo.getLocalGuestBalance())

        // 5. Attempting to redeem as guest is blocked with GUEST_REQUIRES_GOOGLE_SIGN_IN
        val storeItem = VaultGoldRepository.defaultRewardsCatalog().first()
        val redeemAttempt = repo.requestRedemption(storeItem, isGuestAccount = true)
        assertTrue(redeemAttempt.isFailure)
        assertEquals("GUEST_REQUIRES_GOOGLE_SIGN_IN", redeemAttempt.exceptionOrNull()?.message)
    }

    @Test
    fun `playskill proof rejects duplicate screenshot hash and credits coins on admin approval`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val guestUid = "guest_playskill_tester"
        val repo = VaultGoldRepository(db = db, fallbackUserId = guestUid)

        repo.ensureUserInitialized(
            uid = guestUid,
            displayName = "PlaySkill Tester",
            email = null,
            photoUrl = null
        )

        val freeFireCampaign = VaultGoldRepository.defaultPlaySkillCampaigns().first()
        val duplicateHash = "sha256_sample_proof_hash_999"

        val firstSubmit = repo.submitMissionClaim(
            campaign = freeFireCampaign,
            milestoneTitle = "Level 5",
            milestoneId = "ff_lvl5",
            rewardCoins = 100L,
            evidenceHash = duplicateHash
        )
        assertTrue(firstSubmit.isSuccess)
        val submittedClaim = firstSubmit.getOrThrow()
        assertEquals("in_review", submittedClaim.verificationStatus)
        assertEquals(0L, repo.getLocalGuestBalance())

        // Re-submitting the same screenshot hash for another milestone is rejected
        val secondSubmitSamePhoto = repo.submitMissionClaim(
            campaign = freeFireCampaign,
            milestoneTitle = "Level 10",
            milestoneId = "ff_lvl10",
            rewardCoins = 120L,
            evidenceHash = duplicateHash
        )
        assertTrue(secondSubmitSamePhoto.isFailure)
        assertTrue(secondSubmitSamePhoto.exceptionOrNull()?.message?.contains("Duplicate screenshot") == true)

        // Admin approves the first proof -> credits +100 coins once without any Unity ad
        val approveResult = repo.adminApprovePlaySkillClaim(submittedClaim, "Verified level 5 screenshot")
        assertTrue(approveResult.isSuccess)
        assertEquals(100L, repo.getLocalGuestBalance())
    }

    @Test
    fun `youtube watch-and-earn enforces real watch duration checkpoint and blocks duplicate claims`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val guestUid = "guest_youtube_tester"
        val repo = VaultGoldRepository(db = db, fallbackUserId = guestUid)

        repo.ensureUserInitialized(uid = guestUid, displayName = "YT Tester", email = null, photoUrl = null)
        val video = VaultGoldRepository.defaultVideoCampaigns().first { it.rewardCoins > 0L }
        assertEquals("verifiable", video.rewardMode)

        // 1. Returning early (watchedSeconds = 5 < minWatchSeconds = 45) is rejected
        val earlyClaim = repo.claimVerifiableVideoWatch(video, watchedSeconds = 5L, checkpointVerified = true)
        assertTrue(earlyClaim.isFailure)
        assertEquals(0L, repo.getLocalGuestBalance())

        // 2. Skipping the verification checkpoint is rejected even if timer elapsed
        val unverifiedCheckpoint = repo.claimVerifiableVideoWatch(video, watchedSeconds = 50L, checkpointVerified = false)
        assertTrue(unverifiedCheckpoint.isFailure)
        assertEquals(0L, repo.getLocalGuestBalance())

        // 3. Completing both watch duration AND verification checkpoint credits +3 coins once
        val validClaim = repo.claimVerifiableVideoWatch(video, watchedSeconds = 46L, checkpointVerified = true)
        assertTrue(validClaim.isSuccess)
        assertEquals(3L, validClaim.getOrThrow().amount)
        assertEquals(3L, repo.getLocalGuestBalance())

        // 4. Repeated click / duplicate claim for the same video on the same day is blocked
        val duplicateClaim = repo.claimVerifiableVideoWatch(video, watchedSeconds = 60L, checkpointVerified = true)
        assertTrue(duplicateClaim.isFailure)
        assertEquals(3L, repo.getLocalGuestBalance())
    }

    @Test
    fun `tournament match recording and Unity Ads interstitial transition work with VIP removed`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val guestUid = "guest_tournament_tester"
        val repo = VaultGoldRepository(db = db, fallbackUserId = guestUid)
        repo.ensureUserInitialized(uid = guestUid, displayName = "Tournament Tester", email = null, photoUrl = null)

        val viewModel = VaultGoldViewModel(
            repository = repo,
            currentUserId = guestUid,
            userDisplayName = "Tournament Tester",
            userEmail = null,
            userPhotoUrl = null,
            isGuestUser = true
        )

        val ludoArena = viewModel.tournamentArenas.value.first { it.gameId == "ludo_arena" }
        assertEquals("Ludo Pro Championship", ludoArena.title)
        assertEquals(5L, ludoArena.practiceRewardCoins)
        assertEquals(2, ludoArena.maxPlayers)

        // Winning a Ludo tournament match records the match history and prepares an ad-verified reward claim
        viewModel.recordTournamentMatch(
            tournament = ludoArena,
            botDifficulty = BotDifficulty.INTERMEDIATE,
            playerScore = 120,
            botScore = 65,
            roundsPlayed = 10,
            activity = null
        )

        val matches = viewModel.tournamentMatches.value
        assertEquals(1, matches.size)
        assertEquals("VICTORY", matches.first().resultStatus)
        assertEquals(120, matches.first().playerScore)
        assertEquals(65, matches.first().botScore)

        // Pending reward claim is created for the victory (+5 coins after rewarded ad completion)
        val pendingClaim = viewModel.activePendingClaim.value
        assertNotNull(pendingClaim)
        assertEquals(5L, pendingClaim!!.coins)
    }

    @Test
    fun `two-ad hint state machine enforces 2 consecutive completed rewarded ads and preserves 1 of 2 progress`() {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val repo = VaultGoldRepository(db = db, fallbackUserId = "guest_hint_tester")
        val viewModel = VaultGoldViewModel(
            repository = repo,
            currentUserId = "guest_hint_tester",
            userDisplayName = "Hint Tester",
            userEmail = null,
            userPhotoUrl = null,
            isGuestUser = true
        )

        val gameId = "panda_block_puzzle_8x8"
        val clue = "Place Gold Block at Row 2, Col 3"

        // 0/2 initially
        val initial = viewModel.getHintState(gameId)
        assertEquals(0, initial.adsCompleted)
        assertFalse(initial.isUnlocked)

        // Ad 1 completes -> 1/2 (NOT unlocked yet)
        val afterAd1 = viewModel.recordHintAdStepCompleted(gameId, "session_ad_1", clue)
        assertEquals(1, afterAd1.adsCompleted)
        assertFalse(afterAd1.isUnlocked)
        assertEquals("Hint progress: 1/2 ads completed", afterAd1.progressLabel)

        // Replaying the exact same session ID from Ad 1 does NOT advance to 2/2
        val replayAd1 = viewModel.recordHintAdStepCompleted(gameId, "session_ad_1", clue)
        assertEquals(1, replayAd1.adsCompleted)
        assertFalse(replayAd1.isUnlocked)

        // Ad 2 fails to load -> preserves 1/2 progress
        val failedAd2 = viewModel.recordHintAdStepFailedOrSkipped(gameId, "Ad not available, try again")
        assertEquals(1, failedAd2.adsCompleted)
        assertFalse(failedAd2.isUnlocked)
        assertEquals("Ad not available, try again", failedAd2.lastError)

        // Ad 2 completes with a new session ID -> 2/2 UNLOCKED
        val afterAd2 = viewModel.recordHintAdStepCompleted(gameId, "session_ad_2", clue)
        assertEquals(2, afterAd2.adsCompleted)
        assertTrue(afterAd2.isUnlocked)
        assertEquals(clue, afterAd2.hintText)
        assertEquals("Hint unlocked! (2/2 ads completed)", afterAd2.progressLabel)
    }

    @Test
    fun `1v1 tournament enforces max 2 players, atomic entry deduction, cancellation refund, and single winner credit`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val guestUid = "guest_1v1_arena_tester"
        val repo = VaultGoldRepository(db = db, fallbackUserId = guestUid)
        repo.ensureUserInitialized(uid = guestUid, displayName = "Player One", email = null, photoUrl = null)

        // Credit 30 coins via promo code PANDA100 (100 coins) so user can enter 1v1 tiers
        val promoRes = repo.redeemCodeAtomic("PANDA100")
        assertTrue(promoRes.isSuccess)
        assertEquals(100L, repo.getLocalGuestBalance())

        // 1. Create a 1/2 waiting room in 5-coin Ludo tier (opponentUid = null)
        val waitingRoom = repo.joinOrCreate1v1Match(
            gameId = "ludo_arena",
            tier = com.example.model.TournamentTierSpec.TIER_5_WIN_8,
            playerName = "Player One",
            opponentUid = null
        ).getOrThrow()
        assertEquals(1, waitingRoom.playerCount)
        assertEquals("WAITING", waitingRoom.status)
        assertEquals(95L, repo.getLocalGuestBalance())

        // 2. Cancelling a 1/2 waiting room refunds the 5-coin entry fee
        val refundEntry = repo.cancelWaiting1v1Match(waitingRoom.id).getOrThrow()
        assertEquals(5L, refundEntry.amount)
        assertEquals(100L, repo.getLocalGuestBalance())

        // 3. Join a 15-coin Carrom 1v1 match (deducts 15 coins -> 85 coins remaining, 2/2 matched)
        val activeMatch = repo.joinOrCreate1v1Match(
            gameId = "carrom_arena",
            tier = com.example.model.TournamentTierSpec.TIER_15_WIN_25,
            playerName = "Player One",
            opponentUid = "challenger_2",
            opponentName = "Player Two"
        ).getOrThrow()
        assertEquals(2, activeMatch.playerCount)
        assertEquals(2, activeMatch.maxPlayers)
        assertEquals("IN_PROGRESS", activeMatch.status)
        assertEquals(85L, repo.getLocalGuestBalance())

        // 4. Attempting to add a 3rd player to a 2/2 room is strictly rejected
        val thirdPlayerAttempt = repo.joinExisting1v1RoomAsSecondPlayer(
            roomId = activeMatch.id,
            secondPlayerUid = "challenger_3",
            secondPlayerName = "Player Three"
        )
        assertTrue(thirdPlayerAttempt.isFailure)
        assertEquals(
            "Match is already full (2/2 players). A 3rd player cannot join a 1v1 tournament.",
            thirdPlayerAttempt.exceptionOrNull()?.message
        )

        // 5. Settling the 1v1 match with Player 1 winning credits +25 coins once (85 + 25 = 110)
        val settled = repo.settle1v1MatchResult(
            roomId = activeMatch.id,
            player1Score = 80,
            player2Score = 40
        ).getOrThrow()
        assertEquals("COMPLETED", settled.status)
        assertEquals(guestUid, settled.winnerUid)
        assertTrue(settled.prizeCredited)
        assertEquals(110L, repo.getLocalGuestBalance())

        // 6. Calling settle again is idempotent and does NOT double-credit the prize
        val duplicateSettle = repo.settle1v1MatchResult(
            roomId = activeMatch.id,
            player1Score = 80,
            player2Score = 40
        ).getOrThrow()
        assertTrue(duplicateSettle.prizeCredited)
        assertEquals(110L, repo.getLocalGuestBalance())
    }

    @Test
    fun `complete redeem workflow enforces email validation, pending hidden code, staff approval, owner reveal, unauthorized block, and rejection refund`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val sqlDb = com.example.data.sql.AthxoSqlDatabase.createIsolatedTestInstance(context)

        val userUid = "auth_user_redeem_1"
        val userEmail = "player1@athxobooyah.com"
        val userRepo = VaultGoldRepository(db = db, fallbackUserId = userUid, sqlDatabase = sqlDb)
        userRepo.ensureUserInitialized(
            uid = userUid,
            displayName = "Athxo Player 1",
            email = userEmail,
            photoUrl = null,
            defaultRole = "user"
        )

        val staffUid = "staff_reviewer_1"
        val staffRepo = VaultGoldRepository(db = db, fallbackUserId = staffUid, sqlDatabase = sqlDb)
        staffRepo.ensureUserInitialized(
            uid = staffUid,
            displayName = "Athxo Staff Reviewer",
            email = "staff@athxobooyah.com",
            photoUrl = null,
            defaultRole = "staff"
        )

        val financeUid = "finance_admin_1"
        val financeRepo = VaultGoldRepository(db = db, fallbackUserId = financeUid, sqlDatabase = sqlDb)
        financeRepo.ensureUserInitialized(
            uid = financeUid,
            displayName = "Athxo Finance Admin",
            email = "finance@athxobooyah.com",
            photoUrl = null,
            defaultRole = "financeAdmin"
        )

        // Seed 2500 coins via financeAdmin balance adjustment (requires reason + logs before/after)
        val adjustRes = financeRepo.staffAdjustUserBalance(
            staffUid = financeUid,
            targetUserId = userUid,
            deltaCoins = 2500L,
            reason = "Tournament championship payout for redeem test"
        )
        assertTrue(adjustRes.isSuccess)
        assertEquals(2500L, sqlDb.getWallet(userUid).available)

        val reward10 = VaultGoldRepository.defaultRewardsCatalog().first { it.pointsCost == 1000L }

        // 1. Invalid email format is rejected
        val invalidEmailAttempt = userRepo.submitRedemptionWithEmail(
            reward = reward10,
            email = "not-an-email",
            isGuestAccount = false
        )
        assertTrue(invalidEmailAttempt.isFailure)

        // 2. Mismatched email (not matching signed-in account email) is rejected
        val wrongOwnerEmailAttempt = userRepo.submitRedemptionWithEmail(
            reward = reward10,
            email = "someone_else@gmail.com",
            isGuestAccount = false
        )
        assertTrue(wrongOwnerEmailAttempt.isFailure)

        // 3. Valid email submission creates a "Redeem Pending" request, deducts 1000 available coins -> 1000 pending, and hides redeem code
        val pendingReq = userRepo.submitRedemptionWithEmail(
            reward = reward10,
            email = userEmail,
            isGuestAccount = false
        ).getOrThrow()

        assertEquals("pending", pendingReq.status)
        assertEquals("Redeem Pending", pendingReq.displayStatusLabel)
        assertEquals(userEmail, pendingReq.userEmail)
        assertNull("Pending request must NOT expose assignedCode", pendingReq.assignedCode)
        assertNull("Pending request must NOT expose deliveredCodeMasked", pendingReq.deliveredCodeMasked)
        assertEquals(1500L, sqlDb.getWallet(userUid).available)
        assertEquals(1000L, sqlDb.getWallet(userUid).pending)

        // 4. Attempting to reveal code while still Pending is blocked
        val prematureReveal = userRepo.revealRedemptionCode(pendingReq.id, callerUid = userUid)
        assertTrue(prematureReveal.isFailure)

        // 5. Duplicate redemption request while one is already Pending is blocked
        val duplicatePendingAttempt = userRepo.submitRedemptionWithEmail(
            reward = reward10,
            email = userEmail,
            isGuestAccount = false
        )
        assertTrue(duplicatePendingAttempt.isFailure)
        assertTrue(duplicatePendingAttempt.exceptionOrNull()?.message?.contains("Redeem Pending") == true)

        // 6. Unauthorized normal user attempting to approve their own redemption is blocked with SecurityException
        val selfApproveAttempt = userRepo.staffApproveRedemption(
            staffUid = userUid,
            requestId = pendingReq.id,
            assignedRedeemCode = "GPLAY-HACK-1111-2222",
            auditReason = "Self approve attempt"
        )
        assertTrue(selfApproveAttempt.isFailure)
        assertTrue(selfApproveAttempt.exceptionOrNull() is SecurityException)

        // 7. Authorized Staff approves the pending request and assigns a valid redeem code
        val officialCode = "GPLAY-ATHXO-2026-BOOYAH"
        val approvedReq = staffRepo.staffApproveRedemption(
            staffUid = staffUid,
            requestId = pendingReq.id,
            assignedRedeemCode = officialCode,
            auditReason = "Verified legitimate gameplay and email ownership"
        ).getOrThrow()

        assertTrue(approvedReq.isSuccessful)
        assertEquals("Redeem Successful", approvedReq.displayStatusLabel)
        assertEquals(1500L, sqlDb.getWallet(userUid).available)
        assertEquals(0L, sqlDb.getWallet(userUid).pending)
        assertEquals(1000L, sqlDb.getWallet(userUid).redeemed)

        // 8. Account owner can reveal and copy the assigned redeem code
        val ownerReveal = userRepo.revealRedemptionCode(pendingReq.id, callerUid = userUid)
        assertTrue(ownerReveal.isSuccess)
        assertEquals(officialCode, ownerReveal.getOrThrow())

        // 9. Another user CANNOT reveal or read this user's redeem code (throws SecurityException)
        val attackerReveal = userRepo.revealRedemptionCode(pendingReq.id, callerUid = "other_user_999")
        assertTrue(attackerReveal.isFailure)
        assertTrue(attackerReveal.exceptionOrNull() is SecurityException)
        assertTrue(attackerReveal.exceptionOrNull()?.message?.startsWith("UNAUTHORIZED_CODE_ACCESS") == true)

        // 10. Submit a second redemption request and verify Staff Rejection refunds the reserved coins
        val secondReq = userRepo.submitRedemptionWithEmail(
            reward = reward10,
            email = userEmail,
            isGuestAccount = false,
            idempotencyToken = "second_redeem_req_token_1"
        ).getOrThrow()
        assertEquals(500L, sqlDb.getWallet(userUid).available)
        assertEquals(1000L, sqlDb.getWallet(userUid).pending)

        val rejectedReq = staffRepo.staffRejectRedemption(
            staffUid = staffUid,
            requestId = secondReq.id,
            rejectionReason = "Requested cancellation by user",
            auditReason = "User asked to cancel and refund coins"
        ).getOrThrow()

        assertEquals("rejected", rejectedReq.status)
        assertEquals("Redeem Rejected", rejectedReq.displayStatusLabel)
        assertEquals("Requested cancellation by user", rejectedReq.rejectionReason)
        // Reserved 1000 coins are refunded back to available (500 + 1000 = 1500)
        assertEquals(1500L, sqlDb.getWallet(userUid).available)
        assertEquals(0L, sqlDb.getWallet(userUid).pending)
    }

    @Test
    fun `sql migration, foreign key constraints, RBAC privilege escalation block, and local tamper resistance work`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val sqlDb = com.example.data.sql.AthxoSqlDatabase.createIsolatedTestInstance(context)

        val normalUid = "normal_player_sec_1"
        val normalRepo = VaultGoldRepository(db = db, fallbackUserId = normalUid, sqlDatabase = sqlDb)
        normalRepo.ensureUserInitialized(
            uid = normalUid,
            displayName = "Normal Player",
            email = "normal@athxobooyah.com",
            photoUrl = null,
            defaultRole = "user"
        )

        // 1. Normal user cannot self-promote to staff or superAdmin
        val selfPromote = normalRepo.assignUserRole(
            actorUid = normalUid,
            targetUserId = normalUid,
            newRole = "superAdmin",
            reason = "Trying to escalate privileges"
        )
        assertTrue(selfPromote.isFailure)
        assertTrue(selfPromote.exceptionOrNull() is SecurityException)
        assertEquals("user", sqlDb.getUserRole(normalUid))

        // 2. Normal user cannot manually adjust balances
        val unauthorizedBalanceAdjust = normalRepo.staffAdjustUserBalance(
            staffUid = normalUid,
            targetUserId = normalUid,
            deltaCoins = 50000L,
            reason = "Free coins"
        )
        assertTrue(unauthorizedBalanceAdjust.isFailure)
        assertTrue(unauthorizedBalanceAdjust.exceptionOrNull() is SecurityException)
        assertEquals(0L, sqlDb.getWallet(normalUid).available)

        // 3. Local storage / SharedPreferences tampering cannot increase authoritative SQL balance
        context.getSharedPreferences("tampered_local_prefs", Context.MODE_PRIVATE)
            .edit()
            .putLong("available_coins", 999_999L)
            .putString("role", "superAdmin")
            .commit()
        assertEquals(0L, sqlDb.getWallet(normalUid).available)
        assertEquals("user", sqlDb.getUserRole(normalUid))

        // 4. Concurrent/repeated reward claims with the same idempotency eventId credit only once
        val firstCredit = runCatching {
            sqlDb.creditCoinsTransactional(
                userId = normalUid,
                sourceType = "daily_checkin",
                sourceId = "checkin_2026-10-28",
                eventId = "2026-10-28",
                title = "Daily Bonus",
                amount = 15L
            )
        }
        assertTrue(firstCredit.isSuccess)
        assertEquals(15L, sqlDb.getWallet(normalUid).available)

        val duplicateCredit = runCatching {
            sqlDb.creditCoinsTransactional(
                userId = normalUid,
                sourceType = "daily_checkin",
                sourceId = "checkin_2026-10-28",
                eventId = "2026-10-28",
                title = "Daily Bonus",
                amount = 15L
            )
        }
        assertTrue(duplicateCredit.isFailure)
        assertEquals(15L, sqlDb.getWallet(normalUid).available)

        // 5. Verify SQL migration & Foreign Key integrity check
        val migrationReport = normalRepo.backupAndMigrateToSql().getOrThrow()
        assertTrue(migrationReport.integrityVerified)
        assertEquals(0, migrationReport.foreignKeyViolations)
        assertTrue(sqlDb.queryTableCount("schema_migrations") >= 3)
    }

    @Test
    fun `authoritative Ludo match entry checks coins, deducts entry atomically, and asserts exact insufficient coins messages`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val sqlDb = com.example.data.sql.AthxoSqlDatabase.createIsolatedTestInstance(context)
        val userUid = "ludo_tester_1"
        val repo = VaultGoldRepository(db = db, fallbackUserId = userUid, sqlDatabase = sqlDb)

        repo.ensureUserInitialized(
            uid = userUid,
            displayName = "Ludo Tester",
            email = "ludo@athxobooyah.com",
            photoUrl = null
        )

        // 1. Initial balance is 0. Entry requires 5 coins.
        // Must throw InsufficientMatchCoinsException with the EXACT message:
        // "Not enough coins to play this match. Please go back and earn more coins."
        val failEntry = repo.startAuthoritativeBoardMatch(
            gameType = "LUDO",
            matchTitle = "Ludo 1v1",
            entryFeeCoins = 5L,
            prizeCoins = 8L,
            isPlayAgain = false
        )
        assertTrue(failEntry.isFailure)
        val err = failEntry.exceptionOrNull()
        assertTrue(err is com.example.model.InsufficientMatchCoinsException)
        assertEquals("Not enough coins to play this match. Please go back and earn more coins.", err?.message)

        // 2. Play Again with insufficient coins must throw with the EXACT message:
        // "Not enough coins to play again. Please go back and earn more coins."
        val failPlayAgain = repo.startAuthoritativeBoardMatch(
            gameType = "LUDO",
            matchTitle = "Ludo 1v1",
            entryFeeCoins = 5L,
            prizeCoins = 8L,
            isPlayAgain = true
        )
        assertTrue(failPlayAgain.isFailure)
        val playAgainErr = failPlayAgain.exceptionOrNull()
        assertTrue(playAgainErr is com.example.model.InsufficientMatchCoinsException)
        assertEquals("Not enough coins to play again. Please go back and earn more coins.", playAgainErr?.message)

        // 3. Credit 20 coins to user wallet
        sqlDb.creditCoinsTransactional(
            userId = userUid,
            sourceType = "admin_grant",
            sourceId = "grant_1",
            eventId = "grant_event_1",
            title = "Test Coins",
            amount = 20L
        )
        assertEquals(20L, sqlDb.getWallet(userUid).available)

        // 4. Start authoritative match -> deducts entry fee (5 coins) atomically once -> balance = 15
        val startResult = repo.startAuthoritativeBoardMatch(
            gameType = "LUDO",
            matchTitle = "Ludo 1v1 Championship",
            entryFeeCoins = 5L,
            prizeCoins = 8L,
            idempotencyKey = "ludo_entry_key_1",
            isPlayAgain = false
        )
        assertTrue(startResult.isSuccess)
        val match = startResult.getOrThrow()
        assertEquals("IN_PROGRESS", match.status)
        assertEquals(15L, sqlDb.getWallet(userUid).available)

        // 5. Re-submitting the exact same idempotency key does NOT double charge
        val duplicateStart = repo.startAuthoritativeBoardMatch(
            gameType = "LUDO",
            matchTitle = "Ludo 1v1 Championship",
            entryFeeCoins = 5L,
            prizeCoins = 8L,
            idempotencyKey = "ludo_entry_key_1",
            isPlayAgain = false
        )
        assertTrue(duplicateStart.isSuccess)
        assertEquals(match.matchId, duplicateStart.getOrThrow().matchId)
        assertEquals(15L, sqlDb.getWallet(userUid).available) // Still 15, not 10!
    }

    @Test
    fun `authoritative Ludo move validation enforces 6 to leave base, captures, exact home roll, and victory prize credit`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val sqlDb = com.example.data.sql.AthxoSqlDatabase.createIsolatedTestInstance(context)
        val userUid = "ludo_rules_tester_1"
        val repo = VaultGoldRepository(db = db, fallbackUserId = userUid, sqlDatabase = sqlDb)

        repo.ensureUserInitialized(
            uid = userUid,
            displayName = "Ludo Rules",
            email = "ludo_rules@athxobooyah.com",
            photoUrl = null
        )
        sqlDb.creditCoinsTransactional(userUid, "bonus", "bonus_1", "ev_bonus_1", "Bonus", 25L)

        val match = repo.startAuthoritativeBoardMatch(
            gameType = "LUDO",
            matchTitle = "Ludo Pro",
            entryFeeCoins = 5L,
            prizeCoins = 8L
        ).getOrThrow()

        // 1. Moving token before rolling dice throws IllegalStateException
        val prematureMove = repo.submitAuthoritativeLudoMove(match.matchId, actor = "PLAYER", tokenIndex = 0)
        assertTrue(prematureMove.isFailure)

        // 2. Rolling a 4 does NOT allow token at base (-1) to exit (6 is strictly required)
        repo.rollAuthoritativeLudoDice(match.matchId, actor = "PLAYER", requestedDiceValue = 4).getOrThrow()
        val illegalExit = repo.submitAuthoritativeLudoMove(match.matchId, actor = "PLAYER", tokenIndex = 0)
        assertTrue(illegalExit.isFailure)

        // Turn passed to OPPONENT because player had no legal moves. Opponent rolls non-6 and turn passes back to PLAYER
        sqlDb.validateAndRecordLudoDiceRollTransactional(match.matchId, userUid, "OPPONENT", requestedDiceValue = 3)

        // 3. Roll a 6 -> legal exit from base (-1 -> 0)
        val rolledSixMatch = sqlDb.validateAndRecordLudoDiceRollTransactional(match.matchId, userUid, "PLAYER", requestedDiceValue = 6)
        val movedOut = repo.submitAuthoritativeLudoMove(match.matchId, actor = "PLAYER", tokenIndex = 0).getOrThrow()
        val boardAfterMove = sqlDb.deserializeLudoBoardState(movedOut.boardStateJson)
        assertEquals(0, boardAfterMove.playerTokens[0])
        // Rolling 6 gives a bonus turn
        assertEquals("PLAYER", boardAfterMove.currentTurn)

        // 4. Settling match victory credits 8 prize coins atomically once
        assertEquals(20L, sqlDb.getWallet(userUid).available)
        val victorySettled = repo.settleAuthoritativeBoardMatch(
            matchId = match.matchId,
            playerScore = 150,
            opponentScore = 80,
            surrendered = false
        ).getOrThrow()
        assertEquals("COMPLETED_WON", victorySettled.status)
        assertEquals(28L, sqlDb.getWallet(userUid).available) // 20 + 8 = 28

        // Re-settling does NOT award prize coins a second time
        repo.settleAuthoritativeBoardMatch(match.matchId, 150, 80)
        assertEquals(28L, sqlDb.getWallet(userUid).available)
    }

    @Test
    fun `authoritative Carrom shot validates striker baseline, physics, Queen cover, foul and victory`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val sqlDb = com.example.data.sql.AthxoSqlDatabase.createIsolatedTestInstance(context)
        val userUid = "carrom_tester_1"
        val repo = VaultGoldRepository(db = db, fallbackUserId = userUid, sqlDatabase = sqlDb)

        repo.ensureUserInitialized(
            uid = userUid,
            displayName = "Carrom Champ",
            email = "carrom@athxobooyah.com",
            photoUrl = null
        )

        // 1. Exact insufficient coins error messages for Carrom
        val failCarromEntry = repo.startAuthoritativeBoardMatch("CARROM", "Carrom Clash", 15L, 25L, isPlayAgain = false)
        assertTrue(failCarromEntry.isFailure)
        assertEquals("Not enough coins to play this match. Please go back and earn more coins.", failCarromEntry.exceptionOrNull()?.message)

        val failCarromPlayAgain = repo.startAuthoritativeBoardMatch("CARROM", "Carrom Clash", 15L, 25L, isPlayAgain = true)
        assertTrue(failCarromPlayAgain.isFailure)
        assertEquals("Not enough coins to play again. Please go back and earn more coins.", failCarromPlayAgain.exceptionOrNull()?.message)

        // 2. Fund account & enter Carrom match
        sqlDb.creditCoinsTransactional(userUid, "bonus", "bonus_c1", "ev_bonus_c1", "Bonus", 50L)
        val match = repo.startAuthoritativeBoardMatch("CARROM", "Carrom Clash", 15L, 25L).getOrThrow()
        assertEquals(35L, sqlDb.getWallet(userUid).available) // 50 - 15 = 35

        // 3. Illegal striker position (> 0.75f) rejected
        val badShot = repo.submitAuthoritativeCarromShot(
            matchId = match.matchId,
            actor = "PLAYER",
            strikerX = 1.85f,
            aimAngleDeg = 45f,
            powerFraction = 0.5f,
            updatedDiscs = emptyList(),
            strikerFoul = false
        )
        assertTrue(badShot.isFailure)

        // 4. Valid shot pocketing White carrom man (+20 pts)
        val initialDiscs = sqlDb.deserializeCarromBoardState(match.boardStateJson).discs
        val updatedDiscs = initialDiscs.map {
            if (it.type == "WHITE" && it.id == 1) it.copy(isPocketed = true, pocketedBy = "PLAYER") else it
        }
        val validShot = repo.submitAuthoritativeCarromShot(
            matchId = match.matchId,
            actor = "PLAYER",
            strikerX = 0.20f,
            aimAngleDeg = 30f,
            powerFraction = 0.70f,
            updatedDiscs = updatedDiscs,
            strikerFoul = false
        ).getOrThrow()
        assertEquals(20, validShot.playerScore)

        // 5. Striker foul penalizes -10 pts
        val foulShot = repo.submitAuthoritativeCarromShot(
            matchId = match.matchId,
            actor = "PLAYER",
            strikerX = 0.0f,
            aimAngleDeg = 0f,
            powerFraction = 0.8f,
            updatedDiscs = updatedDiscs,
            strikerFoul = true
        ).getOrThrow()
        assertEquals(10, foulShot.playerScore) // 20 - 10 = 10
    }

    @Test
    fun `video proof system validates file size, duration, prevents duplicate hash, awards zero coins on upload, and credits coins only on staff approval`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val sqlDb = com.example.data.sql.AthxoSqlDatabase.createIsolatedTestInstance(context)

        val playerUid = "video_player_1"
        val staffUid = "staff_reviewer_1"
        val playerRepo = VaultGoldRepository(db = db, fallbackUserId = playerUid, sqlDatabase = sqlDb)
        val staffRepo = VaultGoldRepository(db = db, fallbackUserId = staffUid, sqlDatabase = sqlDb)

        playerRepo.ensureUserInitialized(playerUid, "Video Gamer", "player@athxobooyah.com", null)
        staffRepo.ensureUserInitialized(staffUid, "Staff Admin", "staff@athxobooyah.com", null, defaultRole = "staff")

        // 1. Video file too small (< 10 KB) is rejected
        val smallVideo = playerRepo.submitVideoProofForTask(
            taskId = "task_reach_lvl10",
            taskTitle = "Reach Level 10",
            rewardCoins = 50L,
            videoUri = "content://media/video_small.mp4",
            mimeType = "video/mp4",
            fileSizeBytes = 2000L, // 2 KB < 10 KB
            durationSeconds = 15,
            metadataHash = "hash_unique_video_1"
        )
        assertTrue(smallVideo.isFailure)

        // 2. Video duration too short (< 3 seconds) is rejected
        val shortVideo = playerRepo.submitVideoProofForTask(
            taskId = "task_reach_lvl10",
            taskTitle = "Reach Level 10",
            rewardCoins = 50L,
            videoUri = "content://media/video_short.mp4",
            mimeType = "video/mp4",
            fileSizeBytes = 250_000L,
            durationSeconds = 1, // 1s < 3s
            metadataHash = "hash_unique_video_1"
        )
        assertTrue(shortVideo.isFailure)

        // 3. Valid submission is accepted as pending — Balance remains 0 (coins are NOT auto-awarded on upload)
        val validSubmit = playerRepo.submitVideoProofForTask(
            taskId = "task_reach_lvl10",
            taskTitle = "Reach Level 10 in Free Fire",
            rewardCoins = 50L,
            videoUri = "content://media/video_valid.mp4",
            mimeType = "video/mp4",
            fileSizeBytes = 4_500_000L,
            durationSeconds = 45,
            metadataHash = "hash_unique_video_1"
        )
        assertTrue(validSubmit.isSuccess)
        val submission = validSubmit.getOrThrow()
        assertEquals("pending", submission.status)
        assertEquals(0L, sqlDb.getWallet(playerUid).available) // 0 coins awarded on upload!

        // 4. Duplicate video hash submission is rejected
        val duplicateHashSubmit = playerRepo.submitVideoProofForTask(
            taskId = "task_another_mission",
            taskTitle = "Another Mission",
            rewardCoins = 30L,
            videoUri = "content://media/video_valid_copy.mp4",
            mimeType = "video/mp4",
            fileSizeBytes = 4_500_000L,
            durationSeconds = 45,
            metadataHash = "hash_unique_video_1" // Reused hash
        )
        assertTrue(duplicateHashSubmit.isFailure)

        // 5. Non-staff user cannot approve video proofs (throws SecurityException)
        val unauthorizedApproval = playerRepo.staffApproveVideoProof(submission.submissionId)
        assertTrue(unauthorizedApproval.isFailure)
        assertTrue(unauthorizedApproval.exceptionOrNull() is SecurityException)
        assertEquals(0L, sqlDb.getWallet(playerUid).available)

        // 6. Authorized Staff approves video proof -> credits +50 coins atomically once
        val staffApproval = staffRepo.staffApproveVideoProof(submission.submissionId, "Gameplay verified")
        assertTrue(staffApproval.isSuccess)
        assertEquals("approved", staffApproval.getOrThrow().status)
        assertEquals(50L, sqlDb.getWallet(playerUid).available)

        // 7. Double approval attempt is blocked (already approved)
        val doubleApproval = staffRepo.staffApproveVideoProof(submission.submissionId)
        assertTrue(doubleApproval.isFailure)
        assertEquals(50L, sqlDb.getWallet(playerUid).available)
    }

    @Test
    fun `mini-games enforce anti-cheat bounds, daily reward caps, and update personal bests`() = runBlocking {
        val databaseId = context.getString(R.string.firestore_database_id)
        val db = FirebaseFirestore.getInstance(databaseId)
        val sqlDb = com.example.data.sql.AthxoSqlDatabase.createIsolatedTestInstance(context)
        val userUid = "minigame_player_1"
        val repo = VaultGoldRepository(db = db, fallbackUserId = userUid, sqlDatabase = sqlDb)

        repo.ensureUserInitialized(userUid, "Mini Gamer", "minigame@athxobooyah.com", null)

        // 1. Impossible Memory Match completion (e.g. 2 moves for 8 pairs) is rejected
        val impossibleSession = repo.completeMiniGameSession(
            gameType = "MEMORY_MATCH",
            difficulty = "MEDIUM",
            score = 100,
            movesCount = 2, // Minimum is 8
            durationSeconds = 20
        )
        assertTrue(impossibleSession.isFailure)

        // 2. Realistic session awards coins & creates personal best
        val validSession = repo.completeMiniGameSession(
            gameType = "MEMORY_MATCH",
            difficulty = "MEDIUM",
            score = 80,
            movesCount = 12,
            durationSeconds = 35
        ).getOrThrow()
        assertTrue(validSession.coinsAwarded > 0L)
        assertTrue(validSession.isNewPersonalBest)
        assertEquals(validSession.coinsAwarded, sqlDb.getWallet(userUid).available)

        // 3. Panda Bubble Pop session completion
        val bubbleSession = repo.completeMiniGameSession(
            gameType = "BUBBLE_POP",
            difficulty = "MEDIUM",
            score = 240,
            movesCount = 14,
            durationSeconds = 50
        ).getOrThrow()
        assertTrue(bubbleSession.coinsAwarded > 0L)
    }
}

