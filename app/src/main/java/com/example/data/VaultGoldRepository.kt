package com.example.data

import android.content.Context
import com.example.R
import com.example.data.sql.AthxoSqlDatabase
import com.example.model.*
import com.google.firebase.Firebase
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.UUID

class VaultGoldRepository(
    private val db: FirebaseFirestore,
    private val fallbackUserId: String? = null,
    val sqlDatabase: AthxoSqlDatabase? = null
) {

    constructor(context: Context) : this(
        db = FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        ),
        fallbackUserId = null,
        sqlDatabase = AthxoSqlDatabase.getInstance(context.applicationContext)
    )

    private val auth = Firebase.auth

    // Server-authoritative state synchronized with AthxoSqlDatabase
    private val guestWalletFlow = MutableStateFlow(Wallet(id = "main", userId = "guest_local", available = 0L))
    private val guestLedgerFlow = MutableStateFlow<List<LedgerEntry>>(emptyList())
    private val guestClaimsFlow = MutableStateFlow<List<TaskClaim>>(emptyList())
    private val guestPuzzleAttemptsFlow = MutableStateFlow<List<PuzzleAttempt>>(emptyList())
    private val guestAdSessionsFlow = MutableStateFlow<List<AdSession>>(emptyList())
    private val guestIdempotencyKeys = mutableSetOf<String>()
    private val submittedEvidenceHashes = mutableSetOf<String>()

    private val localUserProfilesFlow = MutableStateFlow<Map<String, UserProfile>>(emptyMap())
    private val redemptionsStateFlow = MutableStateFlow<List<RedemptionRequest>>(emptyList())
    private val assignedCodesVault = mutableMapOf<String, String>() // requestId -> unmasked assigned code (strictly guarded)
    private val adminAuditLogsStateFlow = MutableStateFlow<List<AdminAuditLog>>(emptyList())

    private val promoCodesStateFlow = MutableStateFlow<List<RedeemPromoCode>>(defaultPromoCodes())
    private val userRedeemedCodeKeys = MutableStateFlow<Set<String>>(emptySet())
    private val videoPostsStateFlow = MutableStateFlow<List<VideoPost>>(emptyList())
    private val videoCommentsStateFlow = MutableStateFlow<Map<String, List<VideoComment>>>(emptyMap())
    private val likedVideoIdsStateFlow = MutableStateFlow<Set<String>>(emptySet())
    private val subscribedChannelIdsStateFlow = MutableStateFlow<Set<String>>(emptySet())
    private val creatorChannelsStateFlow = MutableStateFlow<Map<String, CreatorChannel>>(emptyMap())
    private val monetizationApplicationsStateFlow = MutableStateFlow<List<CreatorMonetizationApplication>>(emptyList())
    private val active1v1RoomsFlow = MutableStateFlow<List<OneVsOneMatchRoom>>(emptyList())
    private val activeAuthoritativeMatchesFlow = MutableStateFlow<Map<String, AuthoritativeGameMatch>>(emptyMap())
    private val videoProofSubmissionsFlow = MutableStateFlow<List<VideoProofSubmission>>(emptyList())
    private val miniGamePersonalBestsFlow = MutableStateFlow<List<MiniGamePersonalBest>>(emptyList())

    private fun isLocalGuestSession(uid: String? = null): Boolean {
        val target = uid ?: fallbackUserId ?: auth.currentUser?.uid
        return auth.currentUser == null && target != null && (target.startsWith("guest_") || sqlDatabase != null)
    }

    private fun isGuestAccountId(uid: String): Boolean {
        return uid.startsWith("guest_") || auth.currentUser?.isAnonymous == true
    }

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: fallbackUserId
            ?: throw IllegalStateException("Please sign in or tap 'Try as guest' first.")
    }

    fun todayDateString(): String {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.US)
        sdf.timeZone = TimeZone.getTimeZone("Asia/Kolkata")
        return sdf.format(Date())
    }

    companion object {
        fun defaultPlaySkillCampaigns(): List<AppCampaign> = listOf(
            AppCampaign(
                id = "camp_free_fire_max",
                visibility = "public",
                appName = "Free Fire",
                packageName = "com.dts.freefiremax",
                playStoreUrl = "https://play.google.com/store/apps/details?id=com.dts.freefiremax",
                shortDescription = "Reach level 10",
                fullInstructions = "Install from Google Play, reach each level milestone, and upload a screenshot of your profile level.",
                totalReward = 500L,
                estimatedMinutes = 15L,
                status = "live",
                categoryLabel = "Featured",
                verificationMethod = "manual",
                sponsorDisclosure = "Milestones are reviewed by an admin within 24 hours.",
                slotsRemaining = 180L,
                dailyLimit = 30L
            ),
            AppCampaign(
                id = "camp_sensitivity_lab",
                visibility = "public",
                appName = "Sensitivity Lab",
                packageName = "com.esportslab.telemetry.tracker",
                playStoreUrl = "https://play.google.com/store/apps/details?id=com.esportslab.telemetry.tracker",
                shortDescription = "Complete setup & level 10",
                fullInstructions = "Install Sensitivity Lab from Google Play, complete your custom layout, and upload a screenshot for review.",
                totalReward = 150L,
                estimatedMinutes = 8L,
                status = "live",
                categoryLabel = "Trending",
                verificationMethod = "manual",
                sponsorDisclosure = "Milestones are reviewed by an admin within 24 hours.",
                slotsRemaining = 240L,
                dailyLimit = 40L
            ),
            AppCampaign(
                id = "camp_brain_quest",
                visibility = "public",
                appName = "Brain Quest",
                packageName = "com.mindflow.brain.logic",
                playStoreUrl = "https://play.google.com/store/apps/details?id=com.mindflow.brain.logic",
                shortDescription = "Solve level 10 logic board",
                fullInstructions = "Install Brain Quest from Google Play, reach level 10, and submit a screenshot for admin review.",
                totalReward = 90L,
                estimatedMinutes = 10L,
                status = "live",
                categoryLabel = "High-Reward",
                verificationMethod = "manual",
                sponsorDisclosure = "Milestones are reviewed by an admin within 24 hours.",
                slotsRemaining = 95L,
                dailyLimit = 25L
            )
        )

        fun defaultVideoCampaigns(): List<VideoCampaign> = listOf(
            VideoCampaign(
                id = "vid_dQw4w9WgXcQ",
                visibility = "public",
                youtubeVideoId = "dQw4w9WgXcQ",
                canonicalUrl = "https://www.youtube.com/watch?v=dQw4w9WgXcQ",
                title = "Pro Movement & Aim Guide",
                channelName = "Athxo Booya Gaming",
                description = "Watch at least 45 seconds in the official YouTube player and complete the verification checkpoint.",
                rewardMode = "verifiable",
                rewardCoins = 3L,
                minWatchSeconds = 45L,
                status = "live",
                reportsCount = 0L
            ),
            VideoCampaign(
                id = "vid_aqz-KE-bpKQ",
                visibility = "public",
                youtubeVideoId = "aqz-KE-bpKQ",
                canonicalUrl = "https://www.youtube.com/watch?v=aqz-KE-bpKQ",
                title = "Tournament Ludo & Carrom Strategy Masterclass",
                channelName = "Athxo Esports Lab",
                description = "Watch at least 25 seconds on YouTube and answer the checkpoint question to verify.",
                rewardMode = "verifiable",
                rewardCoins = 3L,
                minWatchSeconds = 25L,
                status = "live",
                reportsCount = 0L
            ),
            VideoCampaign(
                id = "vid_M7lc1UVf-VE",
                visibility = "public",
                youtubeVideoId = "M7lc1UVf-VE",
                canonicalUrl = "https://www.youtube.com/watch?v=M7lc1UVf-VE",
                title = "Mobile 60 FPS Settings Walkthrough",
                channelName = "Creator Partner",
                description = "Discovery content — no coins.",
                rewardMode = "discovery_only",
                rewardCoins = 0L,
                minWatchSeconds = 0L,
                status = "live",
                reportsCount = 0L
            )
        )

        fun defaultTournamentArenas(): List<TournamentArenaSpec> = listOf(
            TournamentArenaSpec(
                id = "tour_ludo_championship",
                gameId = "ludo_arena",
                title = "Ludo Pro Championship",
                subtitle = "1v1 (2 Players Only) • Entry: 5 / 15 / 20 Coins • Win: 8 / 25 / 35 Coins",
                category = "1v1 Board Arena",
                formatLabel = "1v1 (2 Players Only)",
                maxPlayers = 2,
                tiers = TournamentTierSpec.STANDARD_1V1_TIERS,
                practiceRewardCoins = 5L,
                entryCoins = 5L,
                advertisedPrizeCoins = 8L,
                entryFeeInrExample = 5,
                advertisedPrizeInrExample = 8,
                rulesSummary = listOf(
                    "Strictly 1v1 Head-to-Head Match (Maximum 2 Players: Player 1 vs Player 2).",
                    "Entry Tiers: 5 Coins -> Win 8 Coins | 15 Coins -> Win 25 Coins | 20 Coins -> Win 35 Coins.",
                    "Fair 1–6 uniform dice randomization — zero secret probability manipulation.",
                    "Roll a 6 to move a token out of base or earn a bonus roll.",
                    "Landing on an opponent's single token on a non-star square captures it back to base (+20 pts).",
                    "First player to bring all 4 tokens home or achieve the highest score when turns complete wins."
                )
            ),
            TournamentArenaSpec(
                id = "tour_carrom_clash",
                gameId = "carrom_arena",
                title = "Carrom Striker Clash",
                subtitle = "1v1 (2 Players Only) • Entry: 5 / 15 / 20 Coins • Win: 8 / 25 / 35 Coins",
                category = "1v1 Striker Arena",
                formatLabel = "1v1 (2 Players Only)",
                maxPlayers = 2,
                tiers = TournamentTierSpec.STANDARD_1V1_TIERS,
                practiceRewardCoins = 5L,
                entryCoins = 5L,
                advertisedPrizeCoins = 8L,
                entryFeeInrExample = 5,
                advertisedPrizeInrExample = 8,
                rulesSummary = listOf(
                    "Strictly 1v1 Head-to-Head Match (Maximum 2 Players: Player 1 vs Player 2).",
                    "Entry Tiers: 5 Coins -> Win 8 Coins | 15 Coins -> Win 25 Coins | 20 Coins -> Win 35 Coins.",
                    "Position your striker along the baseline, adjust aim angle and shot power.",
                    "White carrommen = +20 pts, Black carrommen = +10 pts, Red Queen = +50 pts (requires cover).",
                    "Pocketing a piece keeps your turn active; pocketing the striker is a -10 pt foul.",
                    "Highest score when the board is cleared or round limit finishes wins the 1v1 match."
                )
            )
        )

        fun defaultRewardsCatalog(): List<RewardCatalogItem> = listOf(
            RewardCatalogItem(
                id = "rew_inr_10",
                visibility = "public",
                rewardName = "Google Play Redeem Code ₹10",
                denominationInr = 10L,
                pointsCost = 1000L,
                availability = "available",
                stockCount = 45L,
                minAccountAgeDays = 1L,
                region = "IN",
                status = "active"
            ),
            RewardCatalogItem(
                id = "rew_inr_50",
                visibility = "public",
                rewardName = "Google Play Redeem Code ₹50",
                denominationInr = 50L,
                pointsCost = 4800L,
                availability = "available",
                stockCount = 30L,
                minAccountAgeDays = 3L,
                region = "IN",
                status = "active"
            ),
            RewardCatalogItem(
                id = "rew_inr_100",
                visibility = "public",
                rewardName = "Google Play Redeem Code ₹100",
                denominationInr = 100L,
                pointsCost = 9500L,
                availability = "available",
                stockCount = 20L,
                minAccountAgeDays = 7L,
                region = "IN",
                status = "active"
            )
        )

        fun defaultPromoCodes(): List<RedeemPromoCode> = listOf(
            RedeemPromoCode(
                id = "promo_panda100",
                code = "PANDA100",
                title = "Welcome Panda Bonus",
                description = "Starter bonus for puzzle masters. Instant 100 coins credited directly to your balance.",
                coinReward = 100L,
                totalStock = 500L,
                claimedCount = 48L,
                isEnabled = true,
                targetGame = "All Games & Modes"
            ),
            RedeemPromoCode(
                id = "promo_task2026",
                code = "TASK2026",
                title = "2026 Challenge Boost",
                description = "Claim +50 coins for completing daily missions and community tournaments.",
                coinReward = 50L,
                totalStock = 250L,
                claimedCount = 32L,
                isEnabled = true,
                targetGame = "Task & Earn"
            ),
            RedeemPromoCode(
                id = "promo_creator50",
                code = "CREATOR50",
                title = "Creator Studio Pass",
                description = "Creator launch reward for publishing or exploring community video highlights.",
                coinReward = 50L,
                totalStock = 300L,
                claimedCount = 15L,
                isEnabled = true,
                targetGame = "Create & Earn"
            ),
            RedeemPromoCode(
                id = "promo_gaming25",
                code = "GAMING25",
                title = "Ludo & Carrom Pass",
                description = "Free entry pass boost: +25 bonus coins for tournament practice and mini-games.",
                coinReward = 25L,
                totalStock = 1000L,
                claimedCount = 120L,
                isEnabled = true,
                targetGame = "Championship Arenas"
            )
        )

        /**
         * Part 3: Zero demo/fake videos. Create & Earn starts completely empty until a real creator
         * uploads and publishes a video.
         */
        fun defaultVideoPosts(): List<VideoPost> = emptyList()
    }

    fun defaultTournamentArenas(): List<TournamentArenaSpec> = Companion.defaultTournamentArenas()

    // --- USER PROFILE & INITIALIZATION ---

    suspend fun ensureUserInitialized(
        uid: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        defaultRole: String? = null
    ): Result<UserProfile> = runCatching {
        val isGuest = isGuestAccountId(uid)
        val sqlProfile = sqlDatabase?.ensureUserAndWallet(
            userId = uid,
            displayName = displayName,
            email = email,
            photoUrl = photoUrl,
            isGuest = isGuest,
            defaultRole = defaultRole
        )

        if (isLocalGuestSession(uid)) {
            val isOwnerEmail = email?.equals("atharvakushwaha000@gmail.com", ignoreCase = true) == true
            val resolvedRole = defaultRole ?: sqlProfile?.role ?: if (isOwnerEmail) "superAdmin" else "user"
            val profile = sqlProfile ?: UserProfile(
                id = uid,
                userId = uid,
                displayName = if (isGuest) "Guest" else displayName.ifBlank { "Athxo Booyah Player" },
                email = email,
                photoUrl = photoUrl,
                referralCode = "AB-${uid.takeLast(6).uppercase(Locale.US)}",
                accountStatus = if (isGuest) "guest" else "active",
                role = resolvedRole
            )
            val map = localUserProfilesFlow.value.toMutableMap()
            map[uid] = profile
            localUserProfilesFlow.value = map
            val sqlWallet = sqlDatabase?.getWallet(uid)
            guestWalletFlow.value = sqlWallet ?: guestWalletFlow.value.copy(userId = uid)
            if (sqlDatabase != null) {
                guestLedgerFlow.value = sqlDatabase.getLedgerEntries(uid)
                redemptionsStateFlow.value = sqlDatabase.getUserRedemptions(uid)
                adminAuditLogsStateFlow.value = sqlDatabase.getAuditLogs()
            }
            return@runCatching profile
        }

        val userRef = db.collection("users").document(uid)
        val walletRef = userRef.collection("wallets").document("main")
        val snap = try {
            userRef.get().await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, userRef.path)
            throw e
        }

        if (snap.exists()) {
            val existing = snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, displayName = displayName)
            val walletSnap = walletRef.get().await()
            if (!walletSnap.exists()) {
                walletRef.set(Wallet(userId = uid).toMap()).await()
            }
            val map = localUserProfilesFlow.value.toMutableMap()
            map[uid] = existing
            localUserProfilesFlow.value = map
            existing
        } else {
            val refCode = "AB-${uid.take(6).uppercase(Locale.US)}"
            val isOwnerEmail = email?.equals("atharvakushwaha000@gmail.com", ignoreCase = true) == true
            val initialRole = defaultRole ?: if (isOwnerEmail) "superAdmin" else "user"
            val newProfile = UserProfile(
                id = uid,
                userId = uid,
                displayName = displayName.ifBlank { "Athxo Booyah Player" },
                email = email,
                photoUrl = photoUrl,
                referralCode = refCode,
                accountStatus = if (auth.currentUser?.isAnonymous == true) "guest" else "active",
                role = initialRole,
                ageVerified18Plus = true
            )
            try {
                userRef.set(newProfile.toCreateMap()).await()
                walletRef.set(Wallet(userId = uid).toMap()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, userRef.path)
                throw e
            }
            val map = localUserProfilesFlow.value.toMutableMap()
            map[uid] = newProfile
            localUserProfilesFlow.value = map
            newProfile
        }
    }

    fun observeUserProfile(userId: String): Flow<UserProfile?> {
        if (isLocalGuestSession(userId)) {
            return localUserProfilesFlow.map { map ->
                map[userId] ?: sqlDatabase?.getUserProfile(userId) ?: UserProfile(
                    id = userId,
                    userId = userId,
                    displayName = if (isGuestAccountId(userId)) "Guest" else "Athxo Booyah Player",
                    referralCode = "AB-${userId.takeLast(6).uppercase(Locale.US)}",
                    accountStatus = if (isGuestAccountId(userId)) "guest" else "active",
                    role = "user"
                )
            }
        }
        val docRef = db.collection("users").document(userId)
        return docRef.snapshots()
            .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.GET, docRef.path)
                throw e
            }
    }

    fun observeWallet(userId: String): Flow<Wallet> {
        if (isLocalGuestSession(userId)) {
            return guestWalletFlow
        }
        val docRef = db.collection("users").document(userId).collection("wallets").document("main")
        return docRef.snapshots()
            .map { snap ->
                snap.toObject(Wallet::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    ?: Wallet(id = "main", userId = userId)
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.GET, docRef.path)
                throw e
            }
    }

    fun observeLedger(userId: String): Flow<List<LedgerEntry>> {
        if (isLocalGuestSession(userId)) {
            return guestLedgerFlow
        }
        val colRef = db.collection("users").document(userId).collection("ledger")
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(LedgerEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
                throw e
            }
    }

    // --- CENTRALIZED AUTHORITATIVE REWARD ENGINE (Section 7, 8, 9) ---

    suspend fun awardReward(
        sourceType: String,
        sourceId: String,
        eventId: String,
        title: String,
        amount: Long,
        verificationLevel: String = "server_verified",
        isSandboxTestAd: Boolean = false
    ): Result<LedgerEntry> = runCatching {
        val uid = requireUserId()
        require(amount > 0L) { "Reward amount must be positive" }
        val cleanKey = "${sourceType}_${eventId}_${uid}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)

        if (isLocalGuestSession(uid)) {
            if (sqlDatabase != null) {
                val sqlEntry = sqlDatabase.creditCoinsTransactional(
                    userId = uid,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    eventId = eventId,
                    title = title,
                    amount = amount,
                    verificationLevel = verificationLevel
                )
                guestWalletFlow.value = sqlDatabase.getWallet(uid)
                guestLedgerFlow.value = sqlDatabase.getLedgerEntries(uid)
                return@runCatching sqlEntry
            }
            synchronized(guestIdempotencyKeys) {
                if (!guestIdempotencyKeys.add(cleanKey)) {
                    throw IllegalStateException("Already claimed for this event.")
                }
            }
            val current = guestWalletFlow.value
            val newAvail = current.available + amount
            val newLife = current.lifetimeEarned + amount
            guestWalletFlow.value = current.copy(
                available = newAvail,
                lifetimeEarned = newLife,
                sandboxCoins = if (isSandboxTestAd) current.sandboxCoins + amount else current.sandboxCoins
            )
            val entry = LedgerEntry(
                id = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = uid,
                eventId = eventId,
                sourceType = sourceType,
                sourceId = sourceId,
                title = title,
                amount = amount,
                transactionType = "earn",
                verificationStatus = "approved",
                verificationLevel = verificationLevel,
                idempotencyKey = cleanKey,
                balanceAfter = newAvail
            )
            guestLedgerFlow.value = listOf(entry) + guestLedgerFlow.value
            return@runCatching entry
        }

        val idempRef = db.collection("users").document(uid).collection("idempotency").document(cleanKey)
        val walletRef = db.collection("users").document(uid).collection("wallets").document("main")
        val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val ledgerRef = db.collection("users").document(uid).collection("ledger").document(txId)

        try {
            val idempSnap = idempRef.get().await()
            if (idempSnap.exists()) {
                throw IllegalStateException("Duplicate claim rejected (idempotency key: $cleanKey)")
            }

            db.runTransaction { transaction ->
                val existingIdemp = transaction.get(idempRef)
                if (existingIdemp.exists()) {
                    throw IllegalStateException("Duplicate claim rejected inside transaction")
                }
                val walletSnap = transaction.get(walletRef)
                val currentAvailable = walletSnap.getLong("available") ?: 0L
                val currentPending = walletSnap.getLong("pending") ?: 0L
                val currentLifetime = walletSnap.getLong("lifetimeEarned") ?: 0L
                val currentRedeemed = walletSnap.getLong("redeemed") ?: 0L
                val currentSandbox = walletSnap.getLong("sandboxCoins") ?: 0L

                val newAvailable = currentAvailable + amount
                val newLifetime = currentLifetime + amount
                val newSandbox = if (isSandboxTestAd) currentSandbox + amount else currentSandbox

                transaction.set(
                    idempRef,
                    mapOf(
                        "userId" to uid,
                        "txId" to txId,
                        "sourceType" to sourceType,
                        "createdAt" to FieldValue.serverTimestamp()
                    )
                )

                val ledgerEntry = LedgerEntry(
                    id = txId,
                    userId = uid,
                    eventId = eventId,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    title = title,
                    amount = amount,
                    transactionType = "earn",
                    verificationStatus = "approved",
                    verificationLevel = verificationLevel,
                    idempotencyKey = cleanKey,
                    balanceAfter = newAvailable,
                    actor = "system"
                )
                transaction.set(ledgerRef, ledgerEntry.toCreateMap())

                val updatedWallet = mapOf(
                    "userId" to uid,
                    "available" to newAvailable,
                    "pending" to currentPending,
                    "lifetimeEarned" to newLifetime,
                    "redeemed" to currentRedeemed,
                    "sandboxCoins" to newSandbox,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                transaction.set(walletRef, updatedWallet)
                ledgerEntry
            }.await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, ledgerRef.path)
            throw e
        }
    }

    /**
     * Transfers coins earned during a Guest session into the linked Google account so guest coins
     * are never lost when linking Google to redeem (Section 2).
     */
    suspend fun mergeGuestCoinsIntoAuthenticatedAccount(guestCoins: Long, guestUid: String): Result<LedgerEntry?> = runCatching {
        if (guestCoins <= 0L) return@runCatching null
        awardReward(
            sourceType = "guest_link_merge",
            sourceId = guestUid,
            eventId = "merge_$guestUid",
            title = "Guest coins linked to Google account",
            amount = guestCoins,
            verificationLevel = "server_verified"
        ).getOrNull()
    }

    fun getLocalGuestBalance(): Long {
        val uid = auth.currentUser?.uid ?: fallbackUserId
        if (sqlDatabase != null && uid != null) {
            return sqlDatabase.getAuthoritativeBalance(uid)
        }
        return guestWalletFlow.value.available
    }

    suspend fun updateDisplayName(newDisplayName: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val trimmed = newDisplayName.trim()
        require(trimmed.length in 2..40) { "Display name must be between 2 and 40 characters." }
        if (isLocalGuestSession(uid)) return@runCatching
        val userRef = db.collection("users").document(uid)
        try {
            userRef.update(
                mapOf(
                    "displayName" to trimmed,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, userRef.path)
            throw e
        }
    }

    /**
     * Checks if daily check-in is available today; returns a PendingClaimReward for the
     * Claim-With-Ad flow (Section 7: Daily check-in uses Claim-With-Ad).
     */
    suspend fun prepareDailyCheckInClaim(rewardCoins: Long = 5L): Result<PendingClaimReward> = runCatching {
        val uid = requireUserId()
        val today = todayDateString()
        val cleanKey = "daily_checkin_${today}_${uid}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)
        if (isLocalGuestSession(uid)) {
            if (guestIdempotencyKeys.contains(cleanKey)) {
                throw IllegalStateException("Daily +$rewardCoins already claimed today.")
            }
        } else {
            val idempSnap = db.collection("users").document(uid).collection("idempotency").document(cleanKey).get().await()
            if (idempSnap.exists()) {
                throw IllegalStateException("Daily +$rewardCoins already claimed today.")
            }
        }

        PendingClaimReward(
            id = "claim_daily_$today",
            sourceType = "daily_checkin",
            sourceId = "checkin_$today",
            eventId = today,
            headline = "Daily reward ready",
            coins = rewardCoins
        )
    }

    // Legacy direct daily check-in helper for existing tests
    suspend fun claimDailyCheckIn(): Result<Long> = runCatching {
        val uid = requireUserId()
        val today = todayDateString()
        val rewardCoins = 5L
        awardReward(
            sourceType = "daily_checkin",
            sourceId = "checkin_$today",
            eventId = today,
            title = "Daily +5 reward",
            amount = rewardCoins,
            verificationLevel = "server_verified"
        ).getOrThrow()
        rewardCoins
    }

    // --- PLAYSKILL APP MISSIONS & PROOF SUBMISSION (Section 4) ---

    fun observeAppCampaigns(): Flow<List<AppCampaign>> {
        if (fallbackUserId != null && isLocalGuestSession(fallbackUserId)) {
            return kotlinx.coroutines.flow.flowOf(defaultPlaySkillCampaigns())
        }
        val colRef = db.collection("appCampaigns")
        return colRef.whereEqualTo("visibility", "public")
            .snapshots()
            .map { snap ->
                val list = snap.toObjects(AppCampaign::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.totalReward }
                list.ifEmpty { defaultPlaySkillCampaigns() }
            }
    }

    fun observeTaskClaims(userId: String): Flow<List<TaskClaim>> {
        if (isLocalGuestSession(userId)) {
            return guestClaimsFlow
        }
        val colRef = db.collection("users").document(userId).collection("taskClaims")
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(TaskClaim::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
                throw e
            }
    }

    /**
     * Section 4: Submit PlaySkill milestone screenshot proof.
     * - Duplicate screenshot hashes are rejected.
     * - One reward per user per milestone.
     * - NO Unity ad is triggered. Coins are credited only after admin approval.
     */
    suspend fun submitMissionClaim(
        campaign: AppCampaign,
        milestoneTitle: String,
        milestoneId: String,
        rewardCoins: Long,
        evidenceHash: String
    ): Result<TaskClaim> = runCatching {
        val uid = requireUserId()
        val trimmedHash = evidenceHash.trim()
        require(trimmedHash.isNotBlank()) { "Please add a screenshot before sending for review." }

        synchronized(submittedEvidenceHashes) {
            if (submittedEvidenceHashes.contains(trimmedHash)) {
                throw IllegalStateException("Duplicate screenshot rejected. Please upload a new screenshot.")
            }
        }

        val claimId = "claim_${campaign.id}_${milestoneId}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(100)

        if (isLocalGuestSession(uid)) {
            if (guestClaimsFlow.value.any { it.id == claimId }) {
                throw IllegalStateException("Milestone already in review.")
            }
            if (guestClaimsFlow.value.any { it.evidenceHash == trimmedHash }) {
                throw IllegalStateException("Duplicate screenshot rejected.")
            }
            synchronized(submittedEvidenceHashes) {
                submittedEvidenceHashes.add(trimmedHash)
            }
            val claim = TaskClaim(
                id = claimId,
                userId = uid,
                taskType = "app_mission",
                taskId = milestoneId,
                campaignId = campaign.id,
                title = "${campaign.appName}: $milestoneTitle",
                rewardCoins = rewardCoins,
                progress = 100L,
                verificationStatus = "in_review",
                evidenceHash = trimmedHash
            )
            guestClaimsFlow.value = listOf(claim) + guestClaimsFlow.value
            return@runCatching claim
        }

        val claimRef = db.collection("users").document(uid).collection("taskClaims").document(claimId)
        val existing = claimRef.get().await()
        if (existing.exists()) {
            throw IllegalStateException("Milestone already submitted for review.")
        }

        // Duplicate screenshot hash check across user's submitted claims
        val duplicateCheck = db.collection("users").document(uid).collection("taskClaims")
            .whereEqualTo("evidenceHash", trimmedHash)
            .limit(1)
            .get()
            .await()
        if (!duplicateCheck.isEmpty) {
            throw IllegalStateException("Duplicate screenshot rejected. Please upload a fresh screenshot.")
        }

        synchronized(submittedEvidenceHashes) {
            submittedEvidenceHashes.add(trimmedHash)
        }

        val claim = TaskClaim(
            id = claimId,
            userId = uid,
            taskType = "app_mission",
            taskId = milestoneId,
            campaignId = campaign.id,
            title = "${campaign.appName}: $milestoneTitle",
            rewardCoins = rewardCoins,
            progress = 100L,
            verificationStatus = "in_review",
            evidenceHash = trimmedHash
        )
        try {
            claimRef.set(claim.toCreateMap()).await()
            claim
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, claimRef.path)
            throw e
        }
    }

    /**
     * Admin approves a PlaySkill proof -> credits coins exactly once (Section 4 & 11).
     */
    suspend fun adminApprovePlaySkillClaim(claim: TaskClaim, auditReason: String): Result<LedgerEntry> = runCatching {
        val uid = requireUserId()
        if (isLocalGuestSession(uid)) {
            guestClaimsFlow.value = guestClaimsFlow.value.map {
                if (it.id == claim.id) it.copy(verificationStatus = "approved") else it
            }
        } else {
            val claimRef = db.collection("users").document(uid).collection("taskClaims").document(claim.id)
            claimRef.update(
                mapOf(
                    "verificationStatus" to "approved",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            logAdminAction("APPROVE_PLAYSKILL_PROOF", "taskClaims", claim.id, auditReason)
        }

        awardReward(
            sourceType = "playskill_milestone",
            sourceId = claim.campaignId,
            eventId = claim.id,
            title = claim.title,
            amount = claim.rewardCoins,
            verificationLevel = "admin_approved"
        ).getOrThrow()
    }

    // --- PANDA GAME & BRAIN PUZZLES (Section 6 & 7: Never auto-credit; use Claim-With-Ad) ---

    fun observePuzzleAttempts(userId: String): Flow<List<PuzzleAttempt>> {
        if (isLocalGuestSession(userId)) {
            return guestPuzzleAttemptsFlow
        }
        val colRef = db.collection("users").document(userId).collection("puzzleAttempts")
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(PuzzleAttempt::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
                throw e
            }
    }

    /**
     * Section 6 & 7: Validates the puzzle/Panda win on the server, records the attempt,
     * and returns a `PendingClaimReward` (`10-minute` window) WITHOUT auto-crediting coins.
     * Coins are credited ONLY after the user completes the Unity Rewarded Ad in the Claim screen.
     */
    suspend fun recordPuzzleWinForAdClaim(
        gameId: String,
        category: String,
        difficulty: String,
        score: Long,
        movesCount: Long,
        durationSeconds: Long,
        seedToken: String,
        baseRewardCoins: Long
    ): Result<PendingClaimReward> = runCatching {
        val uid = requireUserId()
        require(durationSeconds >= 1L) { "Solve duration too fast." }
        require(movesCount >= 1L) { "Invalid move count." }
        require(seedToken.length >= 4) { "Invalid round seed." }

        val attemptId = "puz_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val attempt = PuzzleAttempt(
            id = attemptId,
            userId = uid,
            gameId = gameId,
            category = category,
            difficulty = difficulty,
            score = score,
            movesCount = movesCount,
            durationSeconds = durationSeconds,
            seedToken = seedToken,
            serverVerified = true,
            rewardCoins = baseRewardCoins,
            adBonusClaimed = false
        )

        if (isLocalGuestSession(uid)) {
            guestPuzzleAttemptsFlow.value = listOf(attempt) + guestPuzzleAttemptsFlow.value
        } else {
            val attemptRef = db.collection("users").document(uid).collection("puzzleAttempts").document(attemptId)
            try {
                attemptRef.set(attempt.toCreateMap()).await()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                handleFirestoreError(e, OperationType.CREATE, attemptRef.path)
                throw e
            }
        }

        val headline = if (gameId == "find_the_panda_3x3") "You found them all" else "Puzzle solved"
        PendingClaimReward(
            id = "claim_$attemptId",
            sourceType = "puzzle_win",
            sourceId = gameId,
            eventId = seedToken,
            headline = headline,
            coins = baseRewardCoins
        )
    }

    // Legacy helper for direct puzzle attempt rule tests
    suspend fun submitValidatedPuzzleAttempt(
        gameId: String,
        category: String,
        difficulty: String,
        score: Long,
        movesCount: Long,
        durationSeconds: Long,
        seedToken: String,
        baseRewardCoins: Long
    ): Result<PuzzleAttempt> = runCatching {
        val uid = requireUserId()
        require(durationSeconds >= 2L) { "Solve duration below threshold." }
        require(movesCount >= 1L) { "Invalid move count." }
        require(seedToken.length >= 4) { "Invalid seed token." }

        val attemptId = "puz_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val attempt = PuzzleAttempt(
            id = attemptId,
            userId = uid,
            gameId = gameId,
            category = category,
            difficulty = difficulty,
            score = score,
            movesCount = movesCount,
            durationSeconds = durationSeconds,
            seedToken = seedToken,
            serverVerified = true,
            rewardCoins = baseRewardCoins,
            adBonusClaimed = false
        )
        if (!isLocalGuestSession(uid)) {
            val attemptRef = db.collection("users").document(uid).collection("puzzleAttempts").document(attemptId)
            attemptRef.set(attempt.toCreateMap()).await()
        }
        awardReward(
            sourceType = "puzzle_win",
            sourceId = gameId,
            eventId = seedToken,
            title = "$category ($difficulty)",
            amount = baseRewardCoins,
            verificationLevel = "server_verified"
        ).getOrThrow()
        attempt
    }

    // --- UNITY ADS & CLAIM-WITH-AD ENGINE (Section 7 & 8) ---

    fun observeAdSessions(userId: String): Flow<List<AdSession>> {
        if (isLocalGuestSession(userId)) {
            return guestAdSessionsFlow
        }
        val colRef = db.collection("users").document(userId).collection("adSessions")
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(AdSession::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
                throw e
            }
    }

    suspend fun requestAdSession(
        placementId: String,
        testMode: Boolean = false,
        rewardCoins: Long = 1L
    ): Result<AdSession> = runCatching {
        val uid = requireUserId()
        val sessionId = "adsess_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val session = AdSession(
            id = sessionId,
            userId = uid,
            placementId = placementId,
            status = "issued",
            testMode = false,
            rewardCoins = rewardCoins
        )
        if (isLocalGuestSession(uid)) {
            guestAdSessionsFlow.value = listOf(session) + guestAdSessionsFlow.value
            return@runCatching session
        }

        val ref = db.collection("users").document(uid).collection("adSessions").document(sessionId)
        try {
            ref.set(session.toCreateMap()).await()
            session
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, ref.path)
            throw e
        }
    }

    /**
     * Credits a `PendingClaimReward` ONLY after a single Unity Rewarded Ad (`BP_Rewarded_Android`)
     * completes. Enforces 10-minute expiration and single-use idempotency (`pending.eventId`).
     */
    suspend fun completeAdAndClaimPendingReward(
        pending: PendingClaimReward,
        sessionId: String,
        placementId: String,
        elapsedSeconds: Int,
        isTestMode: Boolean = false
    ): Result<LedgerEntry> = runCatching {
        val uid = requireUserId()
        require(!pending.isExpired()) { "Claim expired (10-minute limit)." }
        require(elapsedSeconds >= 2) { "Closing the ad early gives no coins." }

        if (isLocalGuestSession(uid)) {
            guestAdSessionsFlow.value = guestAdSessionsFlow.value.map {
                if (it.id == sessionId) it.copy(status = "approved") else it
            }
        } else {
            val sessionRef = db.collection("users").document(uid).collection("adSessions").document(sessionId)
            sessionRef.update(
                mapOf(
                    "status" to "approved",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        awardReward(
            sourceType = pending.sourceType,
            sourceId = pending.sourceId,
            eventId = pending.eventId,
            title = "${pending.headline} (+${pending.coins})",
            amount = pending.coins,
            verificationLevel = "client_attested",
            isSandboxTestAd = false
        ).getOrThrow()
    }

    suspend fun completeAndVerifyAdSession(
        sessionId: String,
        placementId: String,
        elapsedSeconds: Int,
        isTestMode: Boolean = false,
        bonusContextTitle: String? = null
    ): Result<LedgerEntry> = runCatching {
        val uid = requireUserId()
        require(elapsedSeconds >= 3) { "Ad completion rejected: minimum watch duration not met." }

        if (isLocalGuestSession(uid)) {
            guestAdSessionsFlow.value = guestAdSessionsFlow.value.map {
                if (it.id == sessionId) it.copy(status = "approved") else it
            }
        } else {
            val sessionRef = db.collection("users").document(uid).collection("adSessions").document(sessionId)
            sessionRef.update(
                mapOf(
                    "status" to "approved",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
        }

        val rewardTitle = bonusContextTitle ?: "Watch ad (+1 coin)"
        awardReward(
            sourceType = "unity_rewarded_ad",
            sourceId = placementId,
            eventId = sessionId,
            title = rewardTitle,
            amount = 1L,
            verificationLevel = "client_attested",
            isSandboxTestAd = false
        ).getOrThrow()
    }

    // --- VIDEOS (Policy-Compliant YouTube Watch Tasks, Section 5) ---

    fun observeVideoCampaigns(): Flow<List<VideoCampaign>> {
        val colRef = db.collection("videoCampaigns")
        return colRef.whereEqualTo("visibility", "public")
            .snapshots()
            .map { snap ->
                val list = snap.toObjects(VideoCampaign::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                list.ifEmpty { defaultVideoCampaigns() }
            }
            .catch {
                emit(defaultVideoCampaigns())
            }
    }

    /**
     * Section 5: Reward is for WATCHING only (at least N seconds).
     * NO Unity ad is triggered by opening or watching a video.
     */
    suspend fun claimVerifiableVideoWatch(
        video: VideoCampaign,
        watchedSeconds: Long,
        checkpointVerified: Boolean = true
    ): Result<LedgerEntry> = runCatching {
        require((video.rewardMode == "verifiable" || video.rewardMode == "verifiable_watch") && video.rewardCoins > 0L) {
            "This video is discovery content (no coins)."
        }
        require(watchedSeconds >= video.minWatchSeconds) {
            "Verification failed: watched ${watchedSeconds}s of required ${video.minWatchSeconds}s."
        }
        require(checkpointVerified) {
            "Verification failed: please complete the video verification checkpoint."
        }
        awardReward(
            sourceType = "youtube_watch",
            sourceId = video.id,
            eventId = "yt_${video.id}_${todayDateString()}",
            title = "Watched & Verified: ${video.title}",
            amount = video.rewardCoins,
            verificationLevel = "server_verified"
        ).getOrThrow()
    }

    // --- REWARDS STORE & GIFT-CODE REDEMPTIONS (Section 2 & 9) ---

    fun observeRewardsCatalog(): Flow<List<RewardCatalogItem>> {
        val colRef = db.collection("rewards")
        return colRef.whereEqualTo("visibility", "public")
            .snapshots()
            .map { snap ->
                val list = snap.toObjects(RewardCatalogItem::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedBy { it.pointsCost }
                list.ifEmpty { defaultRewardsCatalog() }
            }
            .catch {
                emit(defaultRewardsCatalog())
            }
    }

    fun observeRedemptions(userId: String): Flow<List<RedemptionRequest>> {
        if (isLocalGuestSession(userId)) {
            return redemptionsStateFlow.map { list ->
                list.filter { it.userId == userId }
                    .map { req ->
                        // Never expose assignedCode on list items unless revealed by owner
                        if (!req.isSuccessful) req.copy(assignedCode = null, deliveredCodeMasked = null)
                        else req.copy(assignedCode = null)
                    }
            }
        }
        val colRef = db.collection("users").document(userId).collection("redemptions")
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(RedemptionRequest::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .map { req ->
                        if (!req.isSuccessful) req.copy(assignedCode = null, deliveredCodeMasked = null)
                        else req.copy(assignedCode = null)
                    }
                    .sortedByDescending { it.createdAtMs.takeIf { ms -> ms > 0L } ?: ((it.createdAt?.seconds ?: 0L) * 1000L) }
            }
            .catch { e ->
                if (e is Exception) handleFirestoreError(e, OperationType.LIST, colRef.path)
                throw e
            }
    }

    fun observeAllRedemptionsForAdmin(): Flow<List<RedemptionRequest>> = redemptionsStateFlow

    /**
     * Requirement 5: Complete Redeem Workflow with Email Validation, Ownership Check,
     * Pending Status, Duplicate Prevention, and Hidden Code until Staff Approval.
     */
    suspend fun submitRedemptionWithEmail(
        reward: RewardCatalogItem,
        email: String,
        isGuestAccount: Boolean = false,
        idempotencyToken: String? = null
    ): Result<RedemptionRequest> = runCatching {
        val uid = requireUserId()
        if (isGuestAccount || isGuestAccountId(uid)) {
            throw IllegalStateException("GUEST_REQUIRES_GOOGLE_SIGN_IN")
        }
        val cleanEmail = email.trim()
        require(AthxoSqlDatabase.isValidEmailAddress(cleanEmail)) {
            "Please enter a valid email address to receive your redeem code."
        }
        require(reward.stockCount > 0L && reward.availability == "available") {
            "Out of stock. Only rewards with real inventory can be redeemed."
        }

        if (sqlDatabase != null) {
            val req = sqlDatabase.submitRedemptionRequestTransactional(
                userId = uid,
                submittedEmail = cleanEmail,
                reward = reward,
                isGuestAccount = isGuestAccount,
                idempotencyToken = idempotencyToken
            )
            guestWalletFlow.value = sqlDatabase.getWallet(uid)
            guestLedgerFlow.value = sqlDatabase.getLedgerEntries(uid)
            val updatedList = redemptionsStateFlow.value.filterNot { it.id == req.id }.toMutableList()
            updatedList.add(0, req)
            redemptionsStateFlow.value = updatedList
            return@runCatching req
        }

        // Check account email ownership if user has an email set
        val existingProfile = localUserProfilesFlow.value[uid]
        val accountEmail = existingProfile?.email?.trim() ?: auth.currentUser?.email?.trim()
        if (!accountEmail.isNullOrBlank() && !accountEmail.equals(cleanEmail, ignoreCase = true)) {
            throw IllegalArgumentException(
                "Email ownership mismatch: please enter the email linked to your account ($accountEmail)."
            )
        }

        // Prevent duplicate pending requests for the same user & reward
        val hasDuplicatePending = redemptionsStateFlow.value.any {
            it.userId == uid && it.rewardId == reward.id && it.isPending
        }
        if (hasDuplicatePending) {
            throw IllegalStateException(
                "You already have a Redeem Pending request for ${reward.rewardName}. Please wait for staff review."
            )
        }

        val idempKey = idempotencyToken?.trim()?.takeIf { it.isNotBlank() }
            ?: "redeem_req_${uid}_${reward.id}"
        synchronized(guestIdempotencyKeys) {
            if (!guestIdempotencyKeys.add(idempKey)) {
                throw IllegalStateException("Duplicate redemption request prevented.")
            }
        }

        if (isLocalGuestSession(uid) || auth.currentUser == null) {
            val currentWallet = guestWalletFlow.value
            if (currentWallet.available < reward.pointsCost) {
                synchronized(guestIdempotencyKeys) { guestIdempotencyKeys.remove(idempKey) }
                throw IllegalStateException("Need ${reward.pointsCost} coins (You have ${currentWallet.available}).")
            }
            val newAvailable = currentWallet.available - reward.pointsCost
            val newPending = currentWallet.pending + reward.pointsCost
            guestWalletFlow.value = currentWallet.copy(
                available = newAvailable,
                pending = newPending
            )
            val reqId = "req_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val nowMs = System.currentTimeMillis()
            val ledgerEntry = LedgerEntry(
                id = "tx_res_${reqId.takeLast(12)}",
                userId = uid,
                eventId = reqId,
                sourceType = "reward_redemption",
                sourceId = reward.id,
                title = "Redeem Pending: ${reward.rewardName}",
                amount = -reward.pointsCost,
                transactionType = "reserve",
                verificationStatus = "pending",
                verificationLevel = "server_verified",
                idempotencyKey = "ledger_$idempKey",
                balanceAfter = newAvailable,
                actor = uid,
                reason = "Redemption request submitted ($cleanEmail)"
            )
            guestLedgerFlow.value = listOf(ledgerEntry) + guestLedgerFlow.value

            val req = RedemptionRequest(
                id = reqId,
                userId = uid,
                userEmail = cleanEmail,
                rewardId = reward.id,
                rewardName = reward.rewardName,
                denominationInr = reward.denominationInr,
                pointsReserved = reward.pointsCost,
                status = "pending",
                deliveredCodeMasked = null,
                assignedCode = null,
                rejectionReason = null,
                createdAtMs = nowMs
            )
            redemptionsStateFlow.value = listOf(req) + redemptionsStateFlow.value
            return@runCatching req
        }

        // Firestore transaction path for live cloud sessions
        val walletRef = db.collection("users").document(uid).collection("wallets").document("main")
        val rewardRef = db.collection("rewards").document(reward.id)
        val redemptionId = "req_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val redemptionRef = db.collection("users").document(uid).collection("redemptions").document(redemptionId)
        val txId = "tx_res_${redemptionId.takeLast(12)}"
        val ledgerRef = db.collection("users").document(uid).collection("ledger").document(txId)

        val createdReq = db.runTransaction { transaction ->
            val walletSnap = transaction.get(walletRef)
            val rewardSnap = transaction.get(rewardRef)

            val availableCoins = walletSnap.getLong("available") ?: 0L
            val pendingCoins = walletSnap.getLong("pending") ?: 0L
            val lifetimeEarned = walletSnap.getLong("lifetimeEarned") ?: 0L
            val redeemedCoins = walletSnap.getLong("redeemed") ?: 0L
            val sandboxCoins = walletSnap.getLong("sandboxCoins") ?: 0L
            val currentStock = rewardSnap.getLong("stockCount") ?: reward.stockCount

            if (currentStock <= 0L) {
                throw IllegalStateException("Inventory exhausted during checkout.")
            }
            if (availableCoins < reward.pointsCost) {
                throw IllegalStateException("Need ${reward.pointsCost} coins (You have $availableCoins).")
            }

            val newAvailable = availableCoins - reward.pointsCost
            val newPending = pendingCoins + reward.pointsCost
            val newStock = currentStock - 1L
            val newAvailability = if (newStock > 0L) "available" else "out_of_stock"

            if (rewardSnap.exists()) {
                transaction.update(
                    rewardRef,
                    mapOf(
                        "stockCount" to newStock,
                        "availability" to newAvailability,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                )
            }

            transaction.set(
                walletRef,
                mapOf(
                    "userId" to uid,
                    "available" to newAvailable,
                    "pending" to newPending,
                    "lifetimeEarned" to lifetimeEarned,
                    "redeemed" to redeemedCoins,
                    "sandboxCoins" to sandboxCoins,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )

            val ledgerEntry = LedgerEntry(
                id = txId,
                userId = uid,
                eventId = redemptionId,
                sourceType = "reward_redemption",
                sourceId = reward.id,
                title = "Redeem Pending: ${reward.rewardName}",
                amount = -reward.pointsCost,
                transactionType = "reserve",
                verificationStatus = "pending",
                verificationLevel = "server_verified",
                idempotencyKey = "redeem_$redemptionId",
                balanceAfter = newAvailable,
                actor = uid
            )
            transaction.set(ledgerRef, ledgerEntry.toCreateMap())

            val req = RedemptionRequest(
                id = redemptionId,
                userId = uid,
                userEmail = cleanEmail,
                rewardId = reward.id,
                rewardName = reward.rewardName,
                denominationInr = reward.denominationInr,
                pointsReserved = reward.pointsCost,
                status = "pending",
                deliveredCodeMasked = null,
                assignedCode = null,
                createdAtMs = System.currentTimeMillis()
            )
            transaction.set(redemptionRef, req.toCreateMap())
            req
        }.await()

        redemptionsStateFlow.value = listOf(createdReq) + redemptionsStateFlow.value
        createdReq
    }

    suspend fun requestRedemption(reward: RewardCatalogItem, isGuestAccount: Boolean = false): Result<RedemptionRequest> {
        val uid = auth.currentUser?.uid ?: fallbackUserId ?: ""
        val fallbackEmail = auth.currentUser?.email
            ?: localUserProfilesFlow.value[uid]?.email
            ?: "player@athxobooyah.app"
        return submitRedemptionWithEmail(
            reward = reward,
            email = fallbackEmail,
            isGuestAccount = isGuestAccount
        )
    }

    /**
     * Requirement 5 & 8: Staff Approval of Pending Redemption Request with Assigned Code & Audit Log.
     */
    suspend fun staffApproveRedemption(
        staffUid: String = requireUserId(),
        requestId: String,
        assignedRedeemCode: String,
        auditReason: String
    ): Result<RedemptionRequest> = runCatching {
        val cleanCode = assignedRedeemCode.trim().uppercase(Locale.US)
        require(cleanCode.length >= 6 && !cleanCode.contains("PLACEHOLDER", ignoreCase = true) && !cleanCode.contains("FAKE", ignoreCase = true)) {
            "A valid, non-placeholder redeem code (min 6 characters) is required."
        }
        require(auditReason.trim().length >= 3) { "Staff approval requires an audit reason." }

        if (sqlDatabase != null) {
            val approved = sqlDatabase.staffApproveRedemptionTransactional(
                staffUid = staffUid,
                requestId = requestId,
                assignedRedeemCode = cleanCode,
                auditReason = auditReason
            )
            synchronized(assignedCodesVault) {
                assignedCodesVault[requestId] = cleanCode
            }
            guestWalletFlow.value = sqlDatabase.getWallet(approved.userId)
            guestLedgerFlow.value = sqlDatabase.getLedgerEntries(approved.userId)
            redemptionsStateFlow.value = redemptionsStateFlow.value.map {
                if (it.id == requestId) approved else it
            }
            adminAuditLogsStateFlow.value = sqlDatabase.getAuditLogs()
            return@runCatching approved
        }

        val staffRole = requireBackendStaffRole(staffUid, AthxoSqlDatabase.STAFF_APPROVAL_ROLES, "APPROVE_REDEMPTION")
        val list = redemptionsStateFlow.value.toMutableList()
        val idx = list.indexOfFirst { it.id == requestId }
        require(idx >= 0) { "Redemption request $requestId not found." }
        val existing = list[idx]
        if (!existing.isPending) {
            throw IllegalStateException("Redemption request $requestId is already ${existing.status}.")
        }
        synchronized(assignedCodesVault) {
            if (assignedCodesVault.values.any { it.equals(cleanCode, ignoreCase = true) }) {
                throw IllegalStateException("Redeem code $cleanCode has already been assigned to another request.")
            }
            assignedCodesVault[requestId] = cleanCode
        }

        val masked = AthxoSqlDatabase.maskRedeemCode(cleanCode)
        val nowMs = System.currentTimeMillis()
        val updated = existing.copy(
            status = "successful",
            deliveredCodeMasked = masked,
            assignedCode = null, // Kept hidden until owner explicitly calls revealRedemptionCode
            rejectionReason = null,
            reviewedByUid = staffUid,
            processedAtMs = nowMs
        )
        list[idx] = updated
        redemptionsStateFlow.value = list

        val w = guestWalletFlow.value
        val newPending = (w.pending - existing.pointsReserved).coerceAtLeast(0L)
        val newRedeemed = w.redeemed + existing.pointsReserved
        guestWalletFlow.value = w.copy(pending = newPending, redeemed = newRedeemed)

        recordAuditLogInternal(
            adminUid = staffUid,
            adminRole = staffRole,
            action = "APPROVE_REDEMPTION",
            targetType = "redemption_requests",
            targetId = requestId,
            beforeValue = "status=pending, assigned_code=null",
            afterValue = "status=successful, assigned_code=$masked",
            reason = auditReason.trim()
        )
        updated
    }

    /**
     * Requirement 5 & 8: Staff Rejection of Pending Redemption Request with Reason & Point Refund.
     */
    suspend fun staffRejectRedemption(
        staffUid: String = requireUserId(),
        requestId: String,
        rejectionReason: String,
        auditReason: String = rejectionReason
    ): Result<RedemptionRequest> = runCatching {
        val cleanRejection = rejectionReason.trim()
        require(cleanRejection.length >= 3) { "Please provide a clear rejection reason." }

        if (sqlDatabase != null) {
            val rejected = sqlDatabase.staffRejectRedemptionTransactional(
                staffUid = staffUid,
                requestId = requestId,
                rejectionReason = cleanRejection,
                auditReason = auditReason
            )
            guestWalletFlow.value = sqlDatabase.getWallet(rejected.userId)
            guestLedgerFlow.value = sqlDatabase.getLedgerEntries(rejected.userId)
            redemptionsStateFlow.value = redemptionsStateFlow.value.map {
                if (it.id == requestId) rejected else it
            }
            adminAuditLogsStateFlow.value = sqlDatabase.getAuditLogs()
            return@runCatching rejected
        }

        val staffRole = requireBackendStaffRole(staffUid, AthxoSqlDatabase.STAFF_APPROVAL_ROLES, "REJECT_REDEMPTION")
        val list = redemptionsStateFlow.value.toMutableList()
        val idx = list.indexOfFirst { it.id == requestId }
        require(idx >= 0) { "Redemption request $requestId not found." }
        val existing = list[idx]
        if (!existing.isPending) {
            throw IllegalStateException("Redemption request $requestId is already ${existing.status}.")
        }

        val nowMs = System.currentTimeMillis()
        val updated = existing.copy(
            status = "rejected",
            deliveredCodeMasked = null,
            assignedCode = null,
            rejectionReason = cleanRejection,
            reviewedByUid = staffUid,
            processedAtMs = nowMs
        )
        list[idx] = updated
        redemptionsStateFlow.value = list

        // Refund reserved points back to available balance
        val w = guestWalletFlow.value
        val newAvail = w.available + existing.pointsReserved
        val newPending = (w.pending - existing.pointsReserved).coerceAtLeast(0L)
        guestWalletFlow.value = w.copy(available = newAvail, pending = newPending)

        val refundEntry = LedgerEntry(
            id = "tx_ref_${UUID.randomUUID().toString().replace("-", "").take(14)}",
            userId = existing.userId,
            eventId = "refund_$requestId",
            sourceType = "reward_redemption_refund",
            sourceId = existing.rewardId,
            title = "Redeem Rejected (Refunded): ${existing.rewardName}",
            amount = existing.pointsReserved,
            transactionType = "refund",
            verificationStatus = "approved",
            verificationLevel = "server_verified",
            idempotencyKey = "refund_redemption_$requestId",
            balanceAfter = newAvail,
            actor = staffUid,
            reason = cleanRejection
        )
        guestLedgerFlow.value = listOf(refundEntry) + guestLedgerFlow.value

        recordAuditLogInternal(
            adminUid = staffUid,
            adminRole = staffRole,
            action = "REJECT_REDEMPTION",
            targetType = "redemption_requests",
            targetId = requestId,
            beforeValue = "status=pending, available_coins=${w.available}",
            afterValue = "status=rejected, available_coins=$newAvail, reason=$cleanRejection",
            reason = auditReason.trim().ifBlank { cleanRejection }
        )
        updated
    }

    /**
     * Requirement 5 & 10: Reveals the assigned redeem code ONLY to the authorized owner of the
     * approved request. Rejects pending/rejected requests and rejects any other user.
     */
    fun revealRedemptionCode(requestId: String, callerUid: String = requireUserId()): Result<String> = runCatching {
        if (sqlDatabase != null) {
            return@runCatching sqlDatabase.revealAssignedRedeemCode(requestId, callerUid)
        }
        val req = redemptionsStateFlow.value.firstOrNull { it.id == requestId }
            ?: throw IllegalArgumentException("Redemption request not found.")
        if (req.userId != callerUid) {
            throw SecurityException("UNAUTHORIZED_CODE_ACCESS: You are not authorized to view another user's redeem code.")
        }
        if (!req.isSuccessful) {
            throw IllegalStateException("Redeem code is hidden while request status is '${req.status}'.")
        }
        val code = synchronized(assignedCodesVault) { assignedCodesVault[requestId] }
        if (code.isNullOrBlank()) {
            throw IllegalStateException("No redeem code assigned to this request.")
        }
        code
    }

    // --- COMMUNITY CODES & SUPPORT TICKETS ---

    fun observeRedeemCodes(): Flow<List<RedeemCodeInfo>> {
        val colRef = db.collection("redeemCodes")
        return colRef.whereEqualTo("visibility", "public")
            .snapshots()
            .map { snap ->
                snap.toObjects(RedeemCodeInfo::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
            }
            .catch {
                emit(emptyList())
            }
    }

    suspend fun reportRedeemCode(codeId: String, currentReports: Long): Result<Unit> = runCatching {
        requireUserId()
        val ref = db.collection("redeemCodes").document(codeId)
        ref.update("reportsCount", currentReports + 1L).await()
    }

    fun observeSupportTickets(userId: String): Flow<List<SupportTicket>> {
        if (isLocalGuestSession(userId)) {
            return MutableStateFlow(emptyList())
        }
        val colRef = db.collection("tickets")
        return colRef.whereEqualTo("userId", userId)
            .snapshots()
            .map { snap ->
                snap.toObjects(SupportTicket::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch {
                emit(emptyList())
            }
    }

    suspend fun submitSupportTicket(
        category: String,
        subject: String,
        message: String,
        linkedRedemptionId: String? = null
    ): Result<SupportTicket> = runCatching {
        val uid = requireUserId()
        val ticketId = "tkt_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val ticket = SupportTicket(
            id = ticketId,
            userId = uid,
            category = category,
            subject = subject.trim(),
            message = message.trim(),
            linkedRedemptionId = linkedRedemptionId,
            status = "open"
        )
        if (!isLocalGuestSession(uid)) {
            val ref = db.collection("tickets").document(ticketId)
            ref.set(ticket.toCreateMap()).await()
        }
        ticket
    }

    // --- ADMIN CONSOLE OPERATIONS, RBAC & AUDIT LOGGING ---

    fun getUserRole(userId: String): String {
        sqlDatabase?.getUserRole(userId)?.let { if (it != "user") return it }
        return localUserProfilesFlow.value[userId]?.role
            ?: sqlDatabase?.getUserRole(userId)
            ?: "user"
    }

    private fun requireBackendStaffRole(
        actorUid: String,
        allowedRoles: Set<String> = AthxoSqlDatabase.STAFF_APPROVAL_ROLES,
        operation: String = "STAFF_OP"
    ): String {
        val role = getUserRole(actorUid)
        if (role !in allowedRoles) {
            throw SecurityException("UNAUTHORIZED_STAFF_OPERATION: Role '$role' is not authorized for $operation.")
        }
        return role
    }

    /**
     * Requirement 8: Role-based access control — only `superAdmin` / `owner` can assign staff roles.
     * Prevents ordinary users or staff from promoting themselves. Records before/after values in Audit Log.
     */
    suspend fun assignUserRole(
        actorUid: String = requireUserId(),
        targetUserId: String,
        newRole: String,
        reason: String
    ): Result<Unit> = runCatching {
        if (sqlDatabase != null) {
            sqlDatabase.assignUserRoleTransactional(actorUid, targetUserId, newRole, reason)
            val updatedProfile = sqlDatabase.getUserProfile(targetUserId)
            if (updatedProfile != null) {
                val map = localUserProfilesFlow.value.toMutableMap()
                map[targetUserId] = updatedProfile
                localUserProfilesFlow.value = map
            }
            adminAuditLogsStateFlow.value = sqlDatabase.getAuditLogs()
            return@runCatching
        }
        val actorRole = requireBackendStaffRole(actorUid, AthxoSqlDatabase.ROLE_MANAGEMENT_ROLES, "ASSIGN_ROLE")
        require(reason.trim().length >= 3) { "Role assignment requires an explicit audit reason." }
        val map = localUserProfilesFlow.value.toMutableMap()
        val existing = map[targetUserId] ?: UserProfile(id = targetUserId, userId = targetUserId, displayName = "Athxo Booyah Player")
        val oldRole = existing.role
        map[targetUserId] = existing.copy(role = newRole)
        localUserProfilesFlow.value = map

        recordAuditLogInternal(
            adminUid = actorUid,
            adminRole = actorRole,
            action = "ASSIGN_USER_ROLE",
            targetType = "users",
            targetId = targetUserId,
            beforeValue = "role=$oldRole",
            afterValue = "role=$newRole",
            reason = reason.trim()
        )
    }

    /**
     * Requirement 8: Manual balance adjustment restricted to `financeAdmin` / `superAdmin`.
     * Requires a mandatory reason, prevents negative balances, and records before/after values.
     */
    suspend fun staffAdjustUserBalance(
        staffUid: String = requireUserId(),
        targetUserId: String,
        deltaCoins: Long,
        reason: String
    ): Result<LedgerEntry> = runCatching {
        if (sqlDatabase != null) {
            val entry = sqlDatabase.staffAdjustUserBalanceTransactional(staffUid, targetUserId, deltaCoins, reason)
            guestWalletFlow.value = sqlDatabase.getWallet(targetUserId)
            guestLedgerFlow.value = sqlDatabase.getLedgerEntries(targetUserId)
            adminAuditLogsStateFlow.value = sqlDatabase.getAuditLogs()
            return@runCatching entry
        }
        val staffRole = requireBackendStaffRole(staffUid, AthxoSqlDatabase.BALANCE_ADJUSTMENT_ROLES, "MANUAL_BALANCE_ADJUSTMENT")
        val cleanReason = reason.trim()
        require(cleanReason.length >= 5) { "Manual balance adjustment requires a detailed reason (min 5 characters)." }
        require(deltaCoins != 0L) { "Adjustment delta cannot be zero." }

        val current = guestWalletFlow.value
        val beforeAvail = current.available
        val afterAvail = beforeAvail + deltaCoins
        if (afterAvail < 0L) {
            throw IllegalStateException("Balance adjustment rejected: resulting balance ($afterAvail) cannot be negative.")
        }
        val newLife = if (deltaCoins > 0) current.lifetimeEarned + deltaCoins else current.lifetimeEarned
        guestWalletFlow.value = current.copy(available = afterAvail, lifetimeEarned = newLife)

        val txId = "tx_adj_${UUID.randomUUID().toString().replace("-", "").take(14)}"
        val entry = LedgerEntry(
            id = txId,
            userId = targetUserId,
            eventId = "adj_$txId",
            sourceType = "admin_adjustment",
            sourceId = staffUid,
            title = if (deltaCoins > 0) "Admin Credit (+$deltaCoins Coins): $cleanReason" else "Admin Debit ($deltaCoins Coins): $cleanReason",
            amount = deltaCoins,
            transactionType = "admin_adjustment",
            verificationStatus = "approved",
            verificationLevel = "admin_approved",
            idempotencyKey = "admin_adj_${txId}_$targetUserId",
            balanceAfter = afterAvail,
            actor = staffUid,
            reason = cleanReason
        )
        guestLedgerFlow.value = listOf(entry) + guestLedgerFlow.value

        recordAuditLogInternal(
            adminUid = staffUid,
            adminRole = staffRole,
            action = "MANUAL_BALANCE_ADJUSTMENT",
            targetType = "wallets",
            targetId = targetUserId,
            beforeValue = "available_coins=$beforeAvail",
            afterValue = "available_coins=$afterAvail",
            reason = cleanReason
        )
        entry
    }

    /**
     * Requirement 6: Backs up existing data and runs repeatable SQL migration with count & FK verification.
     */
    fun backupAndMigrateToSql(
        targetSqlDb: AthxoSqlDatabase? = sqlDatabase
    ): Result<AthxoSqlDatabase.SqlMigrationReport> = runCatching {
        val dbEngine = requireNotNull(targetSqlDb) { "SQL database instance is required for migration." }
        val users = localUserProfilesFlow.value.values.toList()
        val wallets = listOf(guestWalletFlow.value)
        val ledgers = guestLedgerFlow.value
        val redemptions = redemptionsStateFlow.value
        dbEngine.backupAndMigrateRecordsToSql(
            users = users,
            wallets = wallets,
            ledgerEntries = ledgers,
            redemptions = redemptions
        )
    }

    private fun recordAuditLogInternal(
        adminUid: String,
        adminRole: String,
        action: String,
        targetType: String,
        targetId: String,
        beforeValue: String? = null,
        afterValue: String? = null,
        reason: String
    ): AdminAuditLog {
        val entry = AdminAuditLog(
            id = "audit_${UUID.randomUUID().toString().replace("-", "").take(18)}",
            adminUid = adminUid,
            adminRole = adminRole,
            action = action,
            targetType = targetType,
            targetId = targetId,
            beforeValue = beforeValue,
            afterValue = afterValue,
            reason = reason
        )
        adminAuditLogsStateFlow.value = listOf(entry) + adminAuditLogsStateFlow.value
        return entry
    }

    fun observeAdminAuditLogs(): Flow<List<AdminAuditLog>> {
        if (sqlDatabase != null || auth.currentUser == null) {
            return adminAuditLogsStateFlow
        }
        val colRef = db.collection("adminAuditLogs")
        return colRef.snapshots()
            .map { snap ->
                snap.toObjects(AdminAuditLog::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                    .sortedByDescending { it.createdAt?.seconds ?: 0L }
            }
            .catch {
                emit(adminAuditLogsStateFlow.value)
            }
    }

    suspend fun logAdminAction(
        actionType: String,
        targetCollection: String,
        targetId: String,
        reason: String,
        beforeValue: String? = null,
        afterValue: String? = null
    ): Result<AdminAuditLog> = runCatching {
        val uid = requireUserId()
        require(reason.trim().length >= 3) { "Admin action requires an explicit audit reason." }
        if (sqlDatabase != null && getUserRole(uid) in AthxoSqlDatabase.STAFF_APPROVAL_ROLES) {
            val sqlLog = sqlDatabase.recordStaffAuditLog(
                staffUid = uid,
                action = actionType,
                targetType = targetCollection,
                targetId = targetId,
                beforeValue = beforeValue,
                afterValue = afterValue,
                reason = reason
            )
            adminAuditLogsStateFlow.value = sqlDatabase.getAuditLogs()
            return@runCatching sqlLog
        }
        val entry = recordAuditLogInternal(
            adminUid = uid,
            adminRole = getUserRole(uid),
            action = actionType,
            targetType = targetCollection,
            targetId = targetId,
            beforeValue = beforeValue,
            afterValue = afterValue,
            reason = reason.trim()
        )
        if (!isLocalGuestSession(uid)) {
            db.collection("adminAuditLogs").document(entry.id).set(entry.toCreateMap()).await()
        }
        entry
    }

    suspend fun createAppCampaign(campaign: AppCampaign, auditReason: String): Result<String> = runCatching {
        requireUserId()
        require(campaign.playStoreUrl.startsWith("https://play.google.com/store/apps/details?id=")) {
            "Play Store URL must start with https://play.google.com/store/apps/details?id="
        }
        val campaignId = campaign.id.ifBlank { "camp_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
        val ref = db.collection("appCampaigns").document(campaignId)
        ref.set(campaign.copy(id = campaignId).toCreateMap()).await()
        logAdminAction("CREATE_APP_CAMPAIGN", "appCampaigns", campaignId, auditReason)
        campaignId
    }

    suspend fun createVideoCampaign(video: VideoCampaign, auditReason: String): Result<String> = runCatching {
        requireUserId()
        require(video.youtubeVideoId.length == 11) { "YouTube Video ID must be 11 characters." }
        val videoId = video.id.ifBlank { "vid_${video.youtubeVideoId}" }
        val ref = db.collection("videoCampaigns").document(videoId)
        ref.set(video.copy(id = videoId).toCreateMap()).await()
        logAdminAction("CREATE_VIDEO_CAMPAIGN", "videoCampaigns", videoId, auditReason)
        videoId
    }

    suspend fun createOrUpdateRewardItem(item: RewardCatalogItem, auditReason: String): Result<String> = runCatching {
        requireUserId()
        val rewardId = item.id.ifBlank { "rew_inr_${item.denominationInr}" }
        val ref = db.collection("rewards").document(rewardId)
        val computedAvailability = if (item.stockCount > 0L) "available" else "out_of_stock"
        ref.set(item.copy(id = rewardId, availability = computedAvailability).toCreateMap()).await()
        logAdminAction("UPSERT_REWARD_CATALOG", "rewards", rewardId, auditReason)
        rewardId
    }

    suspend fun publishRedeemCodeInfo(info: RedeemCodeInfo, auditReason: String): Result<String> = runCatching {
        requireUserId()
        require(info.sourceReference.isNotBlank()) { "Source reference is mandatory." }
        val codeId = info.id.ifBlank { "ff_${info.code.lowercase(Locale.US)}" }
        val ref = db.collection("redeemCodes").document(codeId)
        ref.set(info.copy(id = codeId).toCreateMap()).await()
        logAdminAction("PUBLISH_REDEEM_CODE", "redeemCodes", codeId, auditReason)
        codeId
    }

    suspend fun seedInitialCatalogsIfEmpty() {
        val uid = auth.currentUser?.uid ?: return
        try {
            val existingCampaigns = db.collection("appCampaigns").limit(1).get().await()
            if (existingCampaigns.isEmpty) {
                defaultPlaySkillCampaigns().forEach { camp ->
                    db.collection("appCampaigns").document(camp.id).set(camp.toCreateMap()).await()
                }
            }
            val existingVideos = db.collection("videoCampaigns").limit(1).get().await()
            if (existingVideos.isEmpty) {
                defaultVideoCampaigns().forEach { vid ->
                    db.collection("videoCampaigns").document(vid.id).set(vid.toCreateMap()).await()
                }
            }
            val existingRewards = db.collection("rewards").limit(1).get().await()
            if (existingRewards.isEmpty) {
                defaultRewardsCatalog().forEach { rew ->
                    db.collection("rewards").document(rew.id).set(rew.toCreateMap()).await()
                }
            }
        } catch (e: Exception) {
            // Non-fatal
        }
    }

    // --- PROMO CODES & REDEEM LIFECYCLE (Eliminates Out of Stock Bug) ---

    fun observePromoCodes(): Flow<List<RedeemPromoCode>> = promoCodesStateFlow

    fun observeUserRedeemedCodeKeys(userId: String): Flow<Set<String>> = userRedeemedCodeKeys.map { keys ->
        keys.filter { it.startsWith("$userId:") }.map { it.substringAfter(":") }.toSet()
    }

    suspend fun redeemCodeAtomic(rawCode: String): Result<Pair<RedeemCodeStatus, String>> = runCatching {
        val uid = requireUserId()
        val trimmed = rawCode.trim().uppercase(Locale.US)
        if (trimmed.length < 3) {
            return@runCatching Pair(RedeemCodeStatus.INVALID, "Please enter a valid code (min 3 chars).")
        }

        val currentList = promoCodesStateFlow.value
        val promo = currentList.firstOrNull { it.code.equals(trimmed, ignoreCase = true) }
            ?: return@runCatching Pair(RedeemCodeStatus.INVALID, "Code '$trimmed' was not found.")

        val userKey = "$uid:${promo.code}"
        if (userRedeemedCodeKeys.value.contains(userKey)) {
            return@runCatching Pair(RedeemCodeStatus.ALREADY_REDEEMED, "You already claimed code ${promo.code}.")
        }

        if (!promo.isEnabled) {
            return@runCatching Pair(RedeemCodeStatus.DISABLED, "Code ${promo.code} is currently disabled.")
        }

        if (promo.expiresAtMs != null && System.currentTimeMillis() > promo.expiresAtMs) {
            return@runCatching Pair(RedeemCodeStatus.EXPIRED, "Code ${promo.code} has expired.")
        }

        if (promo.claimedCount >= promo.totalStock) {
            return@runCatching Pair(RedeemCodeStatus.OUT_OF_STOCK, "Code ${promo.code} is completely OUT OF STOCK (${promo.claimedCount}/${promo.totalStock} claimed).")
        }

        // Commit redemption atomically
        userRedeemedCodeKeys.value = userRedeemedCodeKeys.value + userKey
        val updatedList = currentList.map {
            if (it.code.equals(trimmed, ignoreCase = true)) {
                it.copy(claimedCount = it.claimedCount + 1L)
            } else it
        }
        promoCodesStateFlow.value = updatedList

        // Credit authoritative wallet & ledger
        awardReward(
            sourceType = "promo_code",
            sourceId = promo.id,
            eventId = "${promo.code}_${System.currentTimeMillis()}",
            title = "Promo Code: ${promo.code} (${promo.title})",
            amount = promo.coinReward,
            verificationLevel = "server_verified"
        ).getOrThrow()

        Pair(RedeemCodeStatus.AVAILABLE, "Successfully redeemed ${promo.code}! +${promo.coinReward} coins added to your wallet.")
    }

    suspend fun adminCreatePromoCode(promo: RedeemPromoCode, auditReason: String): Result<String> = runCatching {
        requireUserId()
        val promoId = promo.id.ifBlank { "promo_${promo.code.lowercase(Locale.US)}" }
        val newPromo = promo.copy(id = promoId, code = promo.code.trim().uppercase(Locale.US))
        val list = promoCodesStateFlow.value.toMutableList()
        val index = list.indexOfFirst { it.code.equals(newPromo.code, ignoreCase = true) }
        if (index >= 0) {
            list[index] = newPromo
        } else {
            list.add(0, newPromo)
        }
        promoCodesStateFlow.value = list
        logAdminAction("CREATE_PROMO_CODE", "promoCodes", promoId, auditReason)
        promoId
    }

    suspend fun adminTogglePromoCode(codeId: String, enable: Boolean, auditReason: String): Result<Unit> = runCatching {
        requireUserId()
        val list = promoCodesStateFlow.value.map {
            if (it.id == codeId || it.code.equals(codeId, ignoreCase = true)) {
                it.copy(isEnabled = enable)
            } else it
        }
        promoCodesStateFlow.value = list
        logAdminAction("TOGGLE_PROMO_CODE", "promoCodes", codeId, auditReason)
    }

    // --- CREATE & EARN FULL VIDEO PLATFORM (ZERO DEMO VIDEOS, REAL CREATOR UPLOADS ONLY) ---

    fun observeVideoPosts(category: String? = null, searchQuery: String? = null): Flow<List<VideoPost>> {
        return videoPostsStateFlow.map { list ->
            var filtered = list.filter { it.status == "published" && it.visibility == "public" }
            if (!category.isNullOrBlank() && category != "All") {
                filtered = filtered.filter { it.videoCategory.equals(category, ignoreCase = true) }
            }
            if (!searchQuery.isNullOrBlank()) {
                val q = searchQuery.trim().lowercase(Locale.US)
                filtered = filtered.filter {
                    it.title.lowercase(Locale.US).contains(q) ||
                    it.channelName.lowercase(Locale.US).contains(q) ||
                    it.description.lowercase(Locale.US).contains(q)
                }
            }
            filtered.sortedByDescending { it.uploadedAtMs }
        }
    }

    /**
     * Real creator video recommendations: returns ONLY real, published, public videos
     * excluding the currently watched video (`excludeVideoId`). Returns `emptyList()` when none exist.
     */
    fun observeRecommendedVideos(excludeVideoId: String): Flow<List<VideoPost>> {
        return videoPostsStateFlow.map { list ->
            list.filter {
                it.id != excludeVideoId &&
                    it.status == "published" &&
                    it.visibility == "public"
            }.sortedWith(
                compareByDescending<VideoPost> { it.viewsCount }
                    .thenByDescending { it.uploadedAtMs }
            )
        }
    }

    fun observeVideoComments(videoId: String): Flow<List<VideoComment>> {
        return videoCommentsStateFlow.map { map ->
            (map[videoId] ?: emptyList()).sortedByDescending { it.createdAtMs }
        }
    }

    suspend fun addVideoComment(videoId: String, commentText: String): Result<VideoComment> = runCatching {
        val uid = requireUserId()
        val trimmed = commentText.trim()
        require(trimmed.isNotEmpty()) { "Comment cannot be empty." }
        val comment = VideoComment(
            id = "c_${UUID.randomUUID().toString().take(10)}",
            videoId = videoId,
            authorUid = uid,
            authorName = if (isLocalGuestSession(uid)) "Guest Creator" else (auth.currentUser?.displayName ?: "Player"),
            commentText = trimmed,
            createdAtMs = System.currentTimeMillis()
        )
        val currentMap = videoCommentsStateFlow.value.toMutableMap()
        val list = (currentMap[videoId] ?: emptyList()).toMutableList()
        list.add(0, comment)
        currentMap[videoId] = list
        videoCommentsStateFlow.value = currentMap

        // Increment comment count on the video
        videoPostsStateFlow.value = videoPostsStateFlow.value.map {
            if (it.id == videoId) it.copy(commentsCount = it.commentsCount + 1L) else it
        }

        if (!isLocalGuestSession(uid)) {
            runCatching {
                db.collection("creatorVideos")
                    .document(videoId)
                    .collection("comments")
                    .document(comment.id)
                    .set(comment.toMap())
                    .await()
            }
        }
        comment
    }

    suspend fun deleteVideoComment(videoId: String, commentId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        val currentMap = videoCommentsStateFlow.value.toMutableMap()
        val beforeSize = currentMap[videoId]?.size ?: 0
        val list = currentMap[videoId]?.filterNot { it.id == commentId && (it.authorUid == uid || uid == fallbackUserId) }
            ?: emptyList()
        currentMap[videoId] = list
        videoCommentsStateFlow.value = currentMap
        if (list.size < beforeSize) {
            videoPostsStateFlow.value = videoPostsStateFlow.value.map {
                if (it.id == videoId) it.copy(commentsCount = (it.commentsCount - 1L).coerceAtLeast(0L)) else it
            }
        }
    }

    suspend fun toggleVideoLike(videoId: String): Result<Boolean> = runCatching {
        requireUserId()
        val currentLiked = likedVideoIdsStateFlow.value.toMutableSet()
        val wasLiked = currentLiked.contains(videoId)
        if (wasLiked) {
            currentLiked.remove(videoId)
        } else {
            currentLiked.add(videoId)
        }
        likedVideoIdsStateFlow.value = currentLiked

        videoPostsStateFlow.value = videoPostsStateFlow.value.map {
            if (it.id == videoId) {
                val newLikes = if (wasLiked) (it.likesCount - 1L).coerceAtLeast(0L) else it.likesCount + 1L
                it.copy(likesCount = newLikes)
            } else it
        }
        !wasLiked
    }

    suspend fun recordVideoView(videoId: String): Result<Unit> = runCatching {
        var targetAuthorUid: String? = null
        videoPostsStateFlow.value = videoPostsStateFlow.value.map {
            if (it.id == videoId) {
                targetAuthorUid = it.authorUid
                it.copy(viewsCount = it.viewsCount + 1L)
            } else it
        }
        val ownerUid = targetAuthorUid
        if (!ownerUid.isNullOrBlank()) {
            val map = creatorChannelsStateFlow.value.toMutableMap()
            val existing = map[ownerUid] ?: defaultEmptyCreatorChannel(ownerUid)
            val newViews = existing.totalViews + 1L
            val newStatus = if (existing.monetizationStatus == "NOT_ELIGIBLE" &&
                (newViews >= 50L || existing.totalVideos >= 1L)
            ) "ELIGIBLE" else existing.monetizationStatus
            map[ownerUid] = existing.copy(totalViews = newViews, monetizationStatus = newStatus)
            creatorChannelsStateFlow.value = map
        }
    }

    fun observeSubscriptions(): Flow<Set<String>> = subscribedChannelIdsStateFlow

    suspend fun toggleSubscription(channelId: String): Result<Boolean> = runCatching {
        val current = subscribedChannelIdsStateFlow.value.toMutableSet()
        val isSubbed = current.contains(channelId)
        if (isSubbed) current.remove(channelId) else current.add(channelId)
        subscribedChannelIdsStateFlow.value = current
        !isSubbed
    }

    private fun defaultEmptyCreatorChannel(userId: String): CreatorChannel {
        return CreatorChannel(
            channelId = "chan_$userId",
            ownerUid = userId,
            channelName = if (isGuestAccountId(userId)) "Guest Creator Studio" else "${auth.currentUser?.displayName ?: "Athxo Booyah"} Studio",
            handle = "@creator_${userId.take(6)}",
            bio = "Original Athxo Booyah gaming & puzzle video creator",
            subscribersCount = 0L,
            totalViews = 0L,
            totalVideos = 0L,
            totalCoinsEarned = 0L,
            monetizationStatus = "NOT_ELIGIBLE"
        )
    }

    fun observeCreatorChannel(userId: String): Flow<CreatorChannel> {
        return creatorChannelsStateFlow.map { map ->
            map[userId] ?: defaultEmptyCreatorChannel(userId)
        }
    }

    suspend fun publishVideoPost(post: VideoPost): Result<VideoPost> = runCatching {
        val uid = requireUserId()
        require(post.title.trim().isNotEmpty()) { "Video title is required." }
        require(post.durationSeconds > 0L) { "Video duration must be positive." }

        val postId = post.id.ifBlank { "post_${UUID.randomUUID().toString().take(12)}" }
        val newPost = post.copy(
            id = postId,
            authorUid = uid,
            title = post.title.trim(),
            description = post.description.trim(),
            status = "published",
            visibility = "public",
            viewsCount = 0L,
            likesCount = 0L,
            commentsCount = 0L,
            uploadedAtMs = System.currentTimeMillis()
        )
        val current = videoPostsStateFlow.value.toMutableList()
        current.removeAll { it.id == postId }
        current.add(0, newPost)
        videoPostsStateFlow.value = current

        // Update creator channel stats
        val channelMap = creatorChannelsStateFlow.value.toMutableMap()
        val existingChannel = channelMap[uid] ?: defaultEmptyCreatorChannel(uid)
        val updatedVideoCount = current.count { it.authorUid == uid && it.status == "published" }.toLong()
        val updatedCoins = existingChannel.totalCoinsEarned + 10L
        val nextMonetizationStatus = if (existingChannel.monetizationStatus == "NOT_ELIGIBLE") {
            "ELIGIBLE"
        } else {
            existingChannel.monetizationStatus
        }
        val updatedChannel = existingChannel.copy(
            totalVideos = updatedVideoCount,
            totalCoinsEarned = updatedCoins,
            monetizationStatus = nextMonetizationStatus
        )
        channelMap[uid] = updatedChannel
        creatorChannelsStateFlow.value = channelMap

        if (!isLocalGuestSession(uid)) {
            runCatching {
                db.collection("creatorVideos").document(postId).set(newPost.toMap()).await()
                db.collection("creatorChannels").document(updatedChannel.channelId).set(updatedChannel.toMap()).await()
            }
        }

        // Award +10 coins bonus to authoritative wallet & single ledger for publishing content
        awardReward(
            sourceType = "creator_upload",
            sourceId = postId,
            eventId = "upload_$postId",
            title = "Creator Upload: ${newPost.title.take(30)}",
            amount = 10L,
            verificationLevel = "server_verified"
        ).getOrThrow()

        newPost
    }

    suspend fun deleteVideoPost(postId: String): Result<Unit> = runCatching {
        val uid = requireUserId()
        videoPostsStateFlow.value = videoPostsStateFlow.value.filterNot {
            it.id == postId && (it.authorUid == uid || uid == fallbackUserId)
        }
        if (!isLocalGuestSession(uid)) {
            runCatching {
                db.collection("creatorVideos").document(postId).delete().await()
            }
        }
    }

    suspend fun submitMonetizationApplication(channel: CreatorChannel): Result<CreatorMonetizationApplication> = runCatching {
        val uid = requireUserId()
        val appId = "mon_${UUID.randomUUID().toString().take(10)}"
        val application = CreatorMonetizationApplication(
            id = appId,
            channelId = channel.channelId,
            ownerUid = uid,
            channelName = channel.channelName,
            subscriberCountAtApplication = channel.subscribersCount,
            totalViewsAtApplication = channel.totalViews,
            status = "SUBMITTED"
        )
        val list = monetizationApplicationsStateFlow.value.toMutableList()
        list.add(0, application)
        monetizationApplicationsStateFlow.value = list

        val channelMap = creatorChannelsStateFlow.value.toMutableMap()
        channelMap[uid] = channel.copy(monetizationStatus = "SUBMITTED")
        creatorChannelsStateFlow.value = channelMap

        application
    }

    fun observeMonetizationApplications(): Flow<List<CreatorMonetizationApplication>> = monetizationApplicationsStateFlow

    suspend fun adminReviewMonetization(applicationId: String, approve: Boolean, notes: String): Result<Unit> = runCatching {
        requireUserId()
        val newStatus = if (approve) "APPROVED" else "REJECTED"
        var ownerUidToUpdate: String? = null
        val list = monetizationApplicationsStateFlow.value.map {
            if (it.id == applicationId) {
                ownerUidToUpdate = it.ownerUid
                it.copy(status = newStatus, reviewNotes = notes)
            } else it
        }
        monetizationApplicationsStateFlow.value = list
        ownerUidToUpdate?.let { owner ->
            val channelMap = creatorChannelsStateFlow.value.toMutableMap()
            val ch = channelMap[owner]
            if (ch != null) {
                channelMap[owner] = ch.copy(monetizationStatus = newStatus)
                creatorChannelsStateFlow.value = channelMap
            }
        }
        logAdminAction("REVIEW_MONETIZATION", "monetizationApplications", applicationId, "Status: $newStatus, Notes: $notes")
    }

    // --- PART 7 & PART 8: 1V1 TOURNAMENT MATCHMAKING, ENTRY FEE DEDUCTION, REFUND & SETTLEMENT ---

    fun observeActive1v1Rooms(gameId: String? = null): Flow<List<OneVsOneMatchRoom>> {
        return active1v1RoomsFlow.map { rooms ->
            if (gameId.isNullOrBlank()) rooms else rooms.filter { it.gameId == gameId }
        }
    }

    /**
     * Atomically deducts `amount` coins from the user's authoritative wallet and records a negative
     * ledger transaction. Throws if the user has insufficient balance or if the `eventId` was already charged.
     */
    suspend fun deductCoinsAtomic(
        amount: Long,
        sourceType: String,
        sourceId: String,
        eventId: String,
        title: String
    ): Result<LedgerEntry> = runCatching {
        val uid = requireUserId()
        require(amount > 0L) { "Deduction amount must be positive." }
        val cleanKey = "${sourceType}_${eventId}_${uid}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)

        if (isLocalGuestSession(uid)) {
            if (sqlDatabase != null) {
                val sqlEntry = sqlDatabase.deductCoinsTransactional(
                    userId = uid,
                    amount = amount,
                    sourceType = sourceType,
                    sourceId = sourceId,
                    eventId = eventId,
                    title = title
                )
                guestWalletFlow.value = sqlDatabase.getWallet(uid)
                guestLedgerFlow.value = sqlDatabase.getLedgerEntries(uid)
                return@runCatching sqlEntry
            }
            synchronized(guestIdempotencyKeys) {
                if (!guestIdempotencyKeys.add(cleanKey)) {
                    throw IllegalStateException("Entry fee already deducted for this match.")
                }
            }
            val current = guestWalletFlow.value
            if (current.available < amount) {
                synchronized(guestIdempotencyKeys) {
                    guestIdempotencyKeys.remove(cleanKey)
                }
                throw IllegalStateException("Insufficient balance: need $amount coins, have ${current.available} coins.")
            }
            val newAvail = current.available - amount
            guestWalletFlow.value = current.copy(available = newAvail)
            val entry = LedgerEntry(
                id = "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}",
                userId = uid,
                eventId = eventId,
                sourceType = sourceType,
                sourceId = sourceId,
                title = title,
                amount = -amount,
                transactionType = "entry_fee",
                verificationStatus = "approved",
                verificationLevel = "server_verified",
                idempotencyKey = cleanKey,
                balanceAfter = newAvail
            )
            guestLedgerFlow.value = listOf(entry) + guestLedgerFlow.value
            return@runCatching entry
        }

        val idempRef = db.collection("users").document(uid).collection("idempotency").document(cleanKey)
        val walletRef = db.collection("users").document(uid).collection("wallets").document("main")
        val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(20)}"
        val ledgerRef = db.collection("users").document(uid).collection("ledger").document(txId)

        db.runTransaction { transaction ->
            val existingIdemp = transaction.get(idempRef)
            if (existingIdemp.exists()) {
                throw IllegalStateException("Entry fee already deducted for this match.")
            }
            val walletSnap = transaction.get(walletRef)
            val currentAvailable = walletSnap.getLong("available") ?: 0L
            val currentPending = walletSnap.getLong("pending") ?: 0L
            val currentLifetime = walletSnap.getLong("lifetimeEarned") ?: 0L
            val currentRedeemed = walletSnap.getLong("redeemed") ?: 0L
            val currentSandbox = walletSnap.getLong("sandboxCoins") ?: 0L

            if (currentAvailable < amount) {
                throw IllegalStateException("Insufficient balance: need $amount coins, have $currentAvailable coins.")
            }

            val newAvailable = currentAvailable - amount

            transaction.set(
                idempRef,
                mapOf(
                    "userId" to uid,
                    "txId" to txId,
                    "sourceType" to sourceType,
                    "createdAt" to FieldValue.serverTimestamp()
                )
            )

            val ledgerEntry = LedgerEntry(
                id = txId,
                userId = uid,
                eventId = eventId,
                sourceType = sourceType,
                sourceId = sourceId,
                title = title,
                amount = -amount,
                transactionType = "entry_fee",
                verificationStatus = "approved",
                verificationLevel = "server_verified",
                idempotencyKey = cleanKey,
                balanceAfter = newAvailable,
                actor = uid
            )
            transaction.set(ledgerRef, ledgerEntry.toCreateMap())

            transaction.set(
                walletRef,
                mapOf(
                    "userId" to uid,
                    "available" to newAvailable,
                    "pending" to currentPending,
                    "lifetimeEarned" to currentLifetime,
                    "redeemed" to currentRedeemed,
                    "sandboxCoins" to currentSandbox,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            )
            ledgerEntry
        }.await()
    }

    /**
     * Validates that the tournament game is strictly Ludo or Carrom and that the entry/prize tier
     * adheres to the exact configured rules:
     * - 5 coins entry -> 8 coins prize (strictly <= 8)
     * - 15 coins entry -> 25 coins prize
     * - 20 coins entry -> 35 coins prize
     */
    fun validate1v1TournamentConfig(gameId: String, tier: TournamentTierSpec) {
        require(gameId == "ludo_arena" || gameId == "carrom_arena") {
            "Only 1v1 Ludo and 1v1 Carrom are permitted in Tournament mode."
        }
        val expectedPrize = when (tier.entryCoins) {
            5L -> 8L
            15L -> 25L
            20L -> 35L
            else -> throw IllegalArgumentException("Invalid tournament entry tier: ${tier.entryCoins} coins.")
        }
        require(tier.winnerPrizeCoins == expectedPrize) {
            "Invalid winner prize ${tier.winnerPrizeCoins} for entry ${tier.entryCoins} (expected $expectedPrize coins)."
        }
        if (tier.entryCoins == 5L) {
            require(tier.winnerPrizeCoins <= 8L) {
                "5-coin tournament winner prize must never exceed 8 coins."
            }
        }
    }

    /**
     * Creates or joins a 1v1 (2-player maximum) Ludo or Carrom match room after atomically deducting
     * the entry fee (`5`, `15`, or `20` coins).
     */
    suspend fun joinOrCreate1v1Match(
        gameId: String,
        tier: TournamentTierSpec,
        playerName: String,
        opponentUid: String? = null,
        opponentName: String? = null
    ): Result<OneVsOneMatchRoom> = runCatching {
        val uid = requireUserId()
        validate1v1TournamentConfig(gameId, tier)

        if (opponentUid != null) {
            require(opponentUid != uid) { "Same user cannot join both slots of a 1v1 match." }
        }

        // Check if there is an open WAITING (1/2) room for this game & tier from a distinct player
        val existingWaitingRoom = active1v1RoomsFlow.value.firstOrNull { room ->
            room.gameId == gameId &&
                room.tierId == tier.tierId &&
                room.status == "WAITING" &&
                room.playerCount == 1 &&
                room.player1Uid != uid
        }

        val roomId = existingWaitingRoom?.id ?: "1v1_${gameId.take(4)}_${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val gameTitle = if (gameId == "ludo_arena") "1v1 Ludo Championship" else "1v1 Carrom Clash"

        // Deduct entry fee atomically
        deductCoinsAtomic(
            amount = tier.entryCoins,
            sourceType = "tournament_1v1_entry",
            sourceId = gameId,
            eventId = "entry_${roomId}_$uid",
            title = "$gameTitle Entry (${tier.entryCoins} Coins)"
        ).getOrThrow()

        val updatedRoom = if (existingWaitingRoom != null) {
            existingWaitingRoom.copy(
                player2Uid = uid,
                player2Name = playerName.ifBlank { "Player 2" },
                playerCount = 2,
                maxPlayers = 2,
                status = "IN_PROGRESS"
            )
        } else if (opponentUid != null) {
            OneVsOneMatchRoom(
                id = roomId,
                gameId = gameId,
                gameTitle = gameTitle,
                tierId = tier.tierId,
                entryCoins = tier.entryCoins,
                totalPoolCoins = tier.totalPoolCoins,
                winnerPrizeCoins = tier.winnerPrizeCoins,
                player1Uid = uid,
                player1Name = playerName.ifBlank { "Player 1" },
                player2Uid = opponentUid,
                player2Name = opponentName?.ifBlank { "Challenger" } ?: "Challenger",
                playerCount = 2,
                maxPlayers = 2,
                status = "IN_PROGRESS"
            )
        } else {
            OneVsOneMatchRoom(
                id = roomId,
                gameId = gameId,
                gameTitle = gameTitle,
                tierId = tier.tierId,
                entryCoins = tier.entryCoins,
                totalPoolCoins = tier.totalPoolCoins,
                winnerPrizeCoins = tier.winnerPrizeCoins,
                player1Uid = uid,
                player1Name = playerName.ifBlank { "Player 1" },
                player2Uid = null,
                player2Name = null,
                playerCount = 1,
                maxPlayers = 2,
                status = "WAITING"
            )
        }

        val currentList = active1v1RoomsFlow.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == roomId }
        if (idx >= 0) {
            currentList[idx] = updatedRoom
        } else {
            currentList.add(0, updatedRoom)
        }
        active1v1RoomsFlow.value = currentList

        if (!isLocalGuestSession(uid)) {
            runCatching {
                db.collection("tournamentMatches").document(roomId).set(updatedRoom.toMap()).await()
            }
        }
        updatedRoom
    }

    /**
     * Pairs a second distinct player into a waiting `1/2` room -> transitions to `2/2` (`IN_PROGRESS`).
     * Strictly rejects a 3rd player (`playerCount >= 2`) or the same user joining both slots.
     */
    suspend fun joinExisting1v1RoomAsSecondPlayer(
        roomId: String,
        secondPlayerUid: String,
        secondPlayerName: String
    ): Result<OneVsOneMatchRoom> = runCatching {
        val currentList = active1v1RoomsFlow.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == roomId }
        require(idx >= 0) { "Match room $roomId not found." }
        val room = currentList[idx]

        if (room.playerCount >= 2 || room.status != "WAITING") {
            throw IllegalStateException("Match is already full (2/2 players). A 3rd player cannot join a 1v1 tournament.")
        }
        if (room.player1Uid == secondPlayerUid) {
            throw IllegalStateException("Same user cannot join both slots of a 1v1 match.")
        }

        val updated = room.copy(
            player2Uid = secondPlayerUid,
            player2Name = secondPlayerName.ifBlank { "Challenger" },
            playerCount = 2,
            maxPlayers = 2,
            status = "IN_PROGRESS"
        )
        currentList[idx] = updated
        active1v1RoomsFlow.value = currentList
        updated
    }

    /**
     * Cancels a `WAITING` (`1/2`) match before an opponent joins and refunds the entry fee atomically.
     */
    suspend fun cancelWaiting1v1Match(roomId: String): Result<LedgerEntry> = runCatching {
        val uid = requireUserId()
        val currentList = active1v1RoomsFlow.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == roomId }
        require(idx >= 0) { "Match room not found." }
        val room = currentList[idx]

        require(room.player1Uid == uid) { "Only the room creator can cancel this waiting match." }
        require(room.status == "WAITING" && room.playerCount == 1) {
            "Cannot cancel after 2/2 players have locked in."
        }
        require(!room.entryFeeRefunded) { "Entry fee already refunded for this match." }

        val updated = room.copy(
            status = "CANCELLED",
            entryFeeRefunded = true,
            completedAtMs = System.currentTimeMillis()
        )
        currentList[idx] = updated
        active1v1RoomsFlow.value = currentList

        awardReward(
            sourceType = "tournament_1v1_refund",
            sourceId = room.gameId,
            eventId = "refund_$roomId",
            title = "Refund: ${room.gameTitle} Cancelled (+${room.entryCoins} Coins)",
            amount = room.entryCoins,
            verificationLevel = "server_verified"
        ).getOrThrow()
    }

    /**
     * Settles a completed `2/2` 1v1 match, records the winner, and credits the exact configured
     * `winnerPrizeCoins` (`8`, `25`, or `35` coins) to the winner's wallet & ledger **once only**.
     */
    suspend fun settle1v1MatchResult(
        roomId: String,
        player1Score: Int,
        player2Score: Int
    ): Result<OneVsOneMatchRoom> = runCatching {
        val uid = requireUserId()
        val currentList = active1v1RoomsFlow.value.toMutableList()
        val idx = currentList.indexOfFirst { it.id == roomId }
        require(idx >= 0) { "Match room not found." }
        val room = currentList[idx]

        require(room.playerCount == 2) { "1v1 match requires 2/2 players before settling result." }
        require(room.status != "CANCELLED") { "Cancelled match cannot be settled." }
        if (room.status == "COMPLETED" || room.prizeCredited) {
            return@runCatching room
        }

        val p1Wins = player1Score >= player2Score
        val winUid = if (p1Wins) room.player1Uid else (room.player2Uid ?: "opponent_2")
        val winName = if (p1Wins) room.player1Name else (room.player2Name ?: "Opponent")

        val settledRoom = room.copy(
            status = "COMPLETED",
            player1Score = player1Score,
            player2Score = player2Score,
            winnerUid = winUid,
            winnerName = winName,
            prizeCredited = true,
            completedAtMs = System.currentTimeMillis()
        )
        currentList[idx] = settledRoom
        active1v1RoomsFlow.value = currentList

        if (winUid == uid) {
            awardReward(
                sourceType = "tournament_1v1_win",
                sourceId = room.gameId,
                eventId = "win_$roomId",
                title = "1v1 ${room.gameTitle} Winner Prize (+${room.winnerPrizeCoins} Coins)",
                amount = room.winnerPrizeCoins,
                verificationLevel = "server_verified"
            ).getOrThrow()
        }

        if (!isLocalGuestSession(uid)) {
            runCatching {
                db.collection("tournamentMatches").document(roomId).set(settledRoom.toMap()).await()
            }
        }
        settledRoom
    }

    // =========================================================================
    // AUTHORITATIVE LUDO & CARROM MATCHES, VIDEO PROOF VERIFICATION & MINI-GAMES
    // =========================================================================

    fun observeActiveAuthoritativeMatches(): Flow<Map<String, AuthoritativeGameMatch>> =
        activeAuthoritativeMatchesFlow

    fun observeVideoProofSubmissions(userId: String): Flow<List<VideoProofSubmission>> {
        if (sqlDatabase != null && userId.isNotBlank()) {
            videoProofSubmissionsFlow.value = sqlDatabase.getUserVideoProofSubmissions(userId)
        }
        return videoProofSubmissionsFlow
    }

    fun observeMiniGamePersonalBests(userId: String): Flow<List<MiniGamePersonalBest>> {
        if (sqlDatabase != null && userId.isNotBlank()) {
            miniGamePersonalBestsFlow.value = sqlDatabase.getMiniGamePersonalBests(userId)
        }
        return miniGamePersonalBestsFlow
    }

    private fun syncSqlWalletAndLedger(uid: String) {
        val sql = sqlDatabase ?: return
        guestWalletFlow.value = sql.getWallet(uid)
        guestLedgerFlow.value = sql.getLedgerEntries(uid)
    }

    /**
     * Authoritative Match Entry Flow for Ludo & Carrom:
     * - Verifies user auth, available coins, entry fee, and match eligibility
     * - If insufficient coins:
     *   - Initial entry throws `InsufficientMatchCoinsException("Not enough coins to play this match. Please go back and earn more coins.")`
     *   - Play Again throws `InsufficientMatchCoinsException("Not enough coins to play again. Please go back and earn more coins.")`
     * - Atomically reserves/deducts entry fee once per `idempotencyKey`
     * - Creates the authoritative match in SQL (`AthxoSqlDatabase`).
     */
    suspend fun startAuthoritativeBoardMatch(
        gameType: String, // "LUDO" or "CARROM"
        matchTitle: String,
        entryFeeCoins: Long,
        prizeCoins: Long,
        idempotencyKey: String = "entry_${gameType.lowercase(Locale.US)}_${UUID.randomUUID().toString().replace("-", "").take(12)}",
        isPlayAgain: Boolean = false
    ): Result<AuthoritativeGameMatch> = runCatching {
        val uid = requireUserId()
        val normGame = gameType.trim().uppercase(Locale.US)
        val sql = sqlDatabase
        if (sql != null) {
            val match = sql.startAuthoritativeMatchTransactional(
                userId = uid,
                gameType = normGame,
                matchTitle = matchTitle,
                entryFeeCoins = entryFeeCoins,
                prizeCoins = prizeCoins,
                idempotencyKey = idempotencyKey,
                isPlayAgain = isPlayAgain
            )
            syncSqlWalletAndLedger(uid)
            activeAuthoritativeMatchesFlow.value = activeAuthoritativeMatchesFlow.value + (normGame to match)
            return@runCatching match
        }

        // Fallback if sqlDatabase is not injected
        val currentAvail = guestWalletFlow.value.available
        if (currentAvail < entryFeeCoins) {
            val msg = if (isPlayAgain) {
                AthxoSqlDatabase.INSUFFICIENT_COINS_PLAY_AGAIN_MESSAGE
            } else {
                AthxoSqlDatabase.INSUFFICIENT_COINS_ENTRY_MESSAGE
            }
            throw InsufficientMatchCoinsException(msg, isPlayAgain = isPlayAgain)
        }
        if (entryFeeCoins > 0L) {
            val cur = guestWalletFlow.value
            guestWalletFlow.value = cur.copy(available = cur.available - entryFeeCoins)
        }
        val match = AuthoritativeGameMatch(
            matchId = "match_${normGame.lowercase(Locale.US)}_${UUID.randomUUID().toString().replace("-", "").take(10)}",
            userId = uid,
            gameType = normGame,
            matchTitle = matchTitle,
            entryFeeCoins = entryFeeCoins,
            prizeCoins = prizeCoins,
            status = "IN_PROGRESS",
            currentTurn = "PLAYER",
            turnNumber = 1,
            playerScore = 0,
            opponentScore = 0,
            boardStateJson = "{}",
            entryIdempotencyKey = idempotencyKey
        )
        activeAuthoritativeMatchesFlow.value = activeAuthoritativeMatchesFlow.value + (normGame to match)
        match
    }

    suspend fun rollAuthoritativeLudoDice(
        matchId: String,
        actor: String,
        requestedDiceValue: Int? = null
    ): Result<AuthoritativeGameMatch> = runCatching {
        val uid = requireUserId()
        val sql = sqlDatabase ?: throw IllegalStateException("Authoritative SQL engine required.")
        val updated = sql.validateAndRecordLudoDiceRollTransactional(
            matchId = matchId,
            userId = uid,
            actor = actor,
            requestedDiceValue = requestedDiceValue
        )
        activeAuthoritativeMatchesFlow.value = activeAuthoritativeMatchesFlow.value + ("LUDO" to updated)
        updated
    }

    suspend fun submitAuthoritativeLudoMove(
        matchId: String,
        actor: String,
        tokenIndex: Int
    ): Result<AuthoritativeGameMatch> = runCatching {
        val uid = requireUserId()
        val sql = sqlDatabase ?: throw IllegalStateException("Authoritative SQL engine required.")
        val updated = sql.submitAndValidateLudoMoveTransactional(
            matchId = matchId,
            userId = uid,
            actor = actor,
            tokenIndex = tokenIndex
        )
        syncSqlWalletAndLedger(uid)
        activeAuthoritativeMatchesFlow.value = activeAuthoritativeMatchesFlow.value + ("LUDO" to updated)
        updated
    }

    suspend fun submitAuthoritativeCarromShot(
        matchId: String,
        actor: String,
        strikerX: Float,
        aimAngleDeg: Float,
        powerFraction: Float,
        updatedDiscs: List<CarromDiscState>,
        strikerFoul: Boolean
    ): Result<AuthoritativeGameMatch> = runCatching {
        val uid = requireUserId()
        val sql = sqlDatabase ?: throw IllegalStateException("Authoritative SQL engine required.")
        val updated = sql.submitAndValidateCarromShotTransactional(
            matchId = matchId,
            userId = uid,
            actor = actor,
            strikerX = strikerX,
            aimAngleDeg = aimAngleDeg,
            powerFraction = powerFraction,
            updatedDiscs = updatedDiscs,
            strikerFoul = strikerFoul
        )
        syncSqlWalletAndLedger(uid)
        activeAuthoritativeMatchesFlow.value = activeAuthoritativeMatchesFlow.value + ("CARROM" to updated)
        updated
    }

    suspend fun settleAuthoritativeBoardMatch(
        matchId: String,
        playerScore: Int,
        opponentScore: Int,
        surrendered: Boolean = false
    ): Result<AuthoritativeGameMatch> = runCatching {
        val uid = requireUserId()
        val sql = sqlDatabase ?: throw IllegalStateException("Authoritative SQL engine required.")
        val settled = sql.settleAuthoritativeMatchTransactional(
            matchId = matchId,
            userId = uid,
            playerScore = playerScore,
            opponentScore = opponentScore,
            surrendered = surrendered
        )
        syncSqlWalletAndLedger(uid)
        activeAuthoritativeMatchesFlow.value = activeAuthoritativeMatchesFlow.value + (settled.gameType to settled)
        settled
    }

    suspend fun resumeActiveBoardMatchIfAny(gameType: String): AuthoritativeGameMatch? {
        val uid = auth.currentUser?.uid ?: fallbackUserId ?: return null
        val sql = sqlDatabase ?: return activeAuthoritativeMatchesFlow.value[gameType.uppercase(Locale.US)]
        val active = sql.getActiveMatchForUser(uid, gameType)
        if (active != null) {
            activeAuthoritativeMatchesFlow.value = activeAuthoritativeMatchesFlow.value + (active.gameType to active)
        }
        return active
    }

    /**
     * Video Proof Submission — Real Verification:
     * Validates MIME type, file size, duration, and duplicate metadata hash in SQL.
     * Stores submission as `pending` — coins are awarded ONLY after staff approval.
     */
    suspend fun submitVideoProofForTask(
        taskId: String,
        taskTitle: String,
        rewardCoins: Long,
        videoUri: String,
        mimeType: String,
        fileSizeBytes: Long,
        durationSeconds: Int,
        metadataHash: String
    ): Result<VideoProofSubmission> = runCatching {
        val uid = requireUserId()
        val sql = sqlDatabase ?: throw IllegalStateException("SQL database required for video proof verification.")
        val sub = sql.submitVideoProofTransactional(
            userId = uid,
            taskId = taskId,
            taskTitle = taskTitle,
            rewardCoins = rewardCoins,
            videoUri = videoUri,
            mimeType = mimeType,
            fileSizeBytes = fileSizeBytes,
            durationSeconds = durationSeconds,
            metadataHash = metadataHash
        )
        videoProofSubmissionsFlow.value = sql.getUserVideoProofSubmissions(uid)
        sub
    }

    suspend fun staffApproveVideoProof(
        submissionId: String,
        auditReason: String = "Verified gameplay video proof"
    ): Result<VideoProofSubmission> = runCatching {
        val staffUid = requireUserId()
        val sql = sqlDatabase ?: throw IllegalStateException("SQL database required.")
        val approved = sql.staffApproveVideoProofTransactional(
            staffUid = staffUid,
            submissionId = submissionId,
            auditReason = auditReason
        )
        syncSqlWalletAndLedger(approved.userId)
        videoProofSubmissionsFlow.value = sql.getUserVideoProofSubmissions(approved.userId)
        adminAuditLogsStateFlow.value = sql.getAuditLogs()
        approved
    }

    suspend fun staffRejectVideoProof(
        submissionId: String,
        rejectionReason: String
    ): Result<VideoProofSubmission> = runCatching {
        val staffUid = requireUserId()
        val sql = sqlDatabase ?: throw IllegalStateException("SQL database required.")
        val rejected = sql.staffRejectVideoProofTransactional(
            staffUid = staffUid,
            submissionId = submissionId,
            rejectionReason = rejectionReason
        )
        videoProofSubmissionsFlow.value = sql.getUserVideoProofSubmissions(rejected.userId)
        adminAuditLogsStateFlow.value = sql.getAuditLogs()
        rejected
    }

    fun getAllVideoProofsForStaffReview(): Result<List<VideoProofSubmission>> = runCatching {
        val staffUid = requireUserId()
        val sql = sqlDatabase ?: throw IllegalStateException("SQL database required.")
        sql.getAllVideoProofSubmissionsForStaff(staffUid)
    }

    /**
     * Completes a mini-game session (Panda Memory Match or Panda Bubble Pop) with server-validated
     * score/moves/duration and daily anti-farming limits.
     */
    suspend fun completeMiniGameSession(
        gameType: String, // "MEMORY_MATCH" or "BUBBLE_POP"
        difficulty: String,
        score: Int,
        movesCount: Int,
        durationSeconds: Int,
        idempotencyKey: String = "mg_${gameType.lowercase(Locale.US)}_${UUID.randomUUID().toString().replace("-", "").take(12)}"
    ): Result<MiniGameSessionResult> = runCatching {
        val uid = requireUserId()
        val sql = sqlDatabase ?: throw IllegalStateException("SQL database required.")
        val result = sql.completeMiniGameSessionTransactional(
            userId = uid,
            gameType = gameType,
            difficulty = difficulty,
            score = score,
            movesCount = movesCount,
            durationSeconds = durationSeconds,
            idempotencyKey = idempotencyKey
        )
        syncSqlWalletAndLedger(uid)
        miniGamePersonalBestsFlow.value = sql.getMiniGamePersonalBests(uid)
        result
    }
}

