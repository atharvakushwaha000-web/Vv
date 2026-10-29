package com.example.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.ServerTimestamp

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String? = null,
    val photoUrl: String? = null,
    val referralCode: String = "",
    val referredBy: String? = null,
    val accountStatus: String = "active",
    val role: String = "user",
    val ageVerified18Plus: Boolean = true,
    val country: String? = "IN",
    val notificationsEnabled: Boolean = true,
    val quietHoursEnabled: Boolean = false,
    val checkInStreak: Long = 0L,
    val lastCheckInDate: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "displayName" to displayName,
        "email" to email,
        "photoUrl" to photoUrl,
        "referralCode" to referralCode,
        "referredBy" to referredBy,
        "accountStatus" to accountStatus,
        "role" to role,
        "ageVerified18Plus" to ageVerified18Plus,
        "country" to country,
        "notificationsEnabled" to notificationsEnabled,
        "quietHoursEnabled" to quietHoursEnabled,
        "checkInStreak" to checkInStreak,
        "lastCheckInDate" to lastCheckInDate,
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class Wallet(
    @DocumentId val id: String = "",
    val userId: String = "",
    val available: Long = 0L,
    val pending: Long = 0L,
    val lifetimeEarned: Long = 0L,
    val redeemed: Long = 0L,
    val sandboxCoins: Long = 0L,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "available" to available,
        "pending" to pending,
        "lifetimeEarned" to lifetimeEarned,
        "redeemed" to redeemed,
        "sandboxCoins" to sandboxCoins,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class LedgerEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val eventId: String = "",
    val sourceType: String = "",
    val sourceId: String = "",
    val title: String = "",
    val amount: Long = 0L,
    val transactionType: String = "earn",
    val verificationStatus: String = "approved",
    val verificationLevel: String = "server_verified",
    val idempotencyKey: String = "",
    val balanceAfter: Long = 0L,
    val actor: String = "system",
    val reason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "eventId" to eventId,
        "sourceType" to sourceType,
        "sourceId" to sourceId,
        "title" to title,
        "amount" to amount,
        "transactionType" to transactionType,
        "verificationStatus" to verificationStatus,
        "verificationLevel" to verificationLevel,
        "idempotencyKey" to idempotencyKey,
        "balanceAfter" to balanceAfter,
        "actor" to actor,
        "reason" to reason,
        "createdAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class AppCampaign(
    @DocumentId val id: String = "",
    val visibility: String = "public",
    val appName: String = "",
    val packageName: String = "",
    val playStoreUrl: String = "",
    val shortDescription: String = "",
    val fullInstructions: String = "",
    val totalReward: Long = 0L,
    val estimatedMinutes: Long = 10L,
    val status: String = "live",
    val categoryLabel: String = "Featured",
    val verificationMethod: String = "manual",
    val sponsorDisclosure: String = "Sponsored mission — manual evidence verification required",
    val slotsRemaining: Long = 100L,
    val dailyLimit: Long = 20L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "visibility" to visibility,
        "appName" to appName,
        "packageName" to packageName,
        "playStoreUrl" to playStoreUrl,
        "shortDescription" to shortDescription,
        "fullInstructions" to fullInstructions,
        "totalReward" to totalReward,
        "estimatedMinutes" to estimatedMinutes,
        "status" to status,
        "categoryLabel" to categoryLabel,
        "verificationMethod" to verificationMethod,
        "sponsorDisclosure" to sponsorDisclosure,
        "slotsRemaining" to slotsRemaining,
        "dailyLimit" to dailyLimit,
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class TaskClaim(
    @DocumentId val id: String = "",
    val userId: String = "",
    val taskType: String = "",
    val taskId: String = "",
    val campaignId: String = "",
    val title: String = "",
    val rewardCoins: Long = 0L,
    val progress: Long = 0L,
    val verificationStatus: String = "submitted",
    val evidenceHash: String? = null,
    val rejectionReason: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "taskType" to taskType,
        "taskId" to taskId,
        "campaignId" to campaignId,
        "title" to title,
        "rewardCoins" to rewardCoins,
        "progress" to progress,
        "verificationStatus" to verificationStatus,
        "evidenceHash" to evidenceHash,
        "rejectionReason" to rejectionReason,
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class VideoCampaign(
    @DocumentId val id: String = "",
    val visibility: String = "public",
    val youtubeVideoId: String = "",
    val canonicalUrl: String = "",
    val title: String = "",
    val channelName: String = "",
    val description: String = "",
    val rewardMode: String = "discovery_only",
    val rewardCoins: Long = 0L,
    val minWatchSeconds: Long = 0L,
    val status: String = "live",
    val reportsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "visibility" to visibility,
        "youtubeVideoId" to youtubeVideoId,
        "canonicalUrl" to canonicalUrl,
        "title" to title,
        "channelName" to channelName,
        "description" to description,
        "rewardMode" to rewardMode,
        "rewardCoins" to rewardCoins,
        "minWatchSeconds" to minWatchSeconds,
        "status" to status,
        "reportsCount" to reportsCount,
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp())
    )
}

@IgnoreExtraProperties
data class PuzzleAttempt(
    @DocumentId val id: String = "",
    val userId: String = "",
    val gameId: String = "",
    val category: String = "",
    val difficulty: String = "Medium",
    val score: Long = 0L,
    val movesCount: Long = 1L,
    val durationSeconds: Long = 10L,
    val seedToken: String = "",
    val serverVerified: Boolean = true,
    val rewardCoins: Long = 5L,
    val adBonusClaimed: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "gameId" to gameId,
        "category" to category,
        "difficulty" to difficulty,
        "score" to score,
        "movesCount" to movesCount,
        "durationSeconds" to durationSeconds,
        "seedToken" to seedToken,
        "serverVerified" to serverVerified,
        "rewardCoins" to rewardCoins,
        "adBonusClaimed" to adBonusClaimed,
        "createdAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class AdSession(
    @DocumentId val id: String = "",
    val userId: String = "",
    val placementId: String = "BP_Rewarded_Android",
    val status: String = "issued",
    val testMode: Boolean = false,
    val rewardCoins: Long = 1L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "placementId" to placementId,
        "status" to status,
        "testMode" to testMode,
        "rewardCoins" to rewardCoins,
        "createdAt" to FieldValue.serverTimestamp(),
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class RewardCatalogItem(
    @DocumentId val id: String = "",
    val visibility: String = "public",
    val rewardName: String = "",
    val denominationInr: Long = 10L,
    val pointsCost: Long = 1000L,
    val availability: String = "available",
    val stockCount: Long = 50L,
    val minAccountAgeDays: Long = 1L,
    val region: String = "IN",
    val status: String = "active",
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "visibility" to visibility,
        "rewardName" to rewardName,
        "denominationInr" to denominationInr,
        "pointsCost" to pointsCost,
        "availability" to availability,
        "stockCount" to stockCount,
        "minAccountAgeDays" to minAccountAgeDays,
        "region" to region,
        "status" to status,
        "updatedAt" to FieldValue.serverTimestamp()
    )
}

@IgnoreExtraProperties
data class RedemptionRequest(
    @DocumentId val id: String = "",
    val userId: String = "",
    val userEmail: String = "",
    val rewardId: String = "",
    val rewardName: String = "",
    val denominationInr: Long = 10L,
    val pointsReserved: Long = 1000L,
    val status: String = "pending", // pending, successful, rejected
    val deliveredCodeMasked: String? = null,
    val assignedCode: String? = null,
    val rejectionReason: String? = null,
    val reviewedByUid: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val processedAtMs: Long? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    val displayStatusLabel: String
        get() = when (status.lowercase()) {
            "successful", "approved" -> "Redeem Successful"
            "rejected" -> "Redeem Rejected"
            else -> "Redeem Pending"
        }

    val isSuccessful: Boolean
        get() = status.equals("successful", ignoreCase = true) || status.equals("approved", ignoreCase = true)

    val isRejected: Boolean
        get() = status.equals("rejected", ignoreCase = true)

    val isPending: Boolean
        get() = !isSuccessful && !isRejected

    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "userEmail" to userEmail,
        "rewardId" to rewardId,
        "rewardName" to rewardName,
        "denominationInr" to denominationInr,
        "pointsReserved" to pointsReserved,
        "status" to status,
        "deliveredCodeMasked" to if (isSuccessful) deliveredCodeMasked else null,
        "rejectionReason" to rejectionReason,
        "reviewedByUid" to reviewedByUid,
        "createdAtMs" to createdAtMs,
        "processedAtMs" to processedAtMs,
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class RedeemCodeInfo(
    @DocumentId val id: String = "",
    val visibility: String = "public",
    val code: String = "",
    val title: String = "",
    val rewardDescription: String = "",
    val region: String = "IND",
    val game: String = "FF_MAX",
    val expiryLabel: String = "",
    val sourceReference: String = "",
    val verificationStatus: String = "verified",
    val reportsCount: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "visibility" to visibility,
        "code" to code,
        "title" to title,
        "rewardDescription" to rewardDescription,
        "region" to region,
        "game" to game,
        "expiryLabel" to expiryLabel,
        "sourceReference" to sourceReference,
        "verificationStatus" to verificationStatus,
        "reportsCount" to reportsCount,
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp())
    )
}

@IgnoreExtraProperties
data class SupportTicket(
    @DocumentId val id: String = "",
    val userId: String = "",
    val category: String = "General",
    val subject: String = "",
    val message: String = "",
    val linkedRedemptionId: String? = null,
    val status: String = "open",
    val adminReply: String? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "userId" to userId,
        "category" to category,
        "subject" to subject,
        "message" to message,
        "linkedRedemptionId" to linkedRedemptionId,
        "status" to status,
        "adminReply" to adminReply,
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp()),
        "updatedAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

@IgnoreExtraProperties
data class AdminAuditLog(
    @DocumentId val id: String = "",
    val adminUid: String = "",
    val adminRole: String = "superAdmin",
    val action: String = "",
    val targetType: String = "",
    val targetId: String = "",
    val beforeValue: String? = null,
    val afterValue: String? = null,
    val reason: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    fun toCreateMap(): Map<String, Any> = mapOf(
        "adminUid" to adminUid,
        "adminRole" to adminRole,
        "action" to action,
        "targetType" to targetType,
        "targetId" to targetId,
        "beforeValue" to beforeValue,
        "afterValue" to afterValue,
        "reason" to reason,
        "createdAt" to FieldValue.serverTimestamp()
    ).filterValues { it != null } as Map<String, Any>
}

/**
 * Claim-With-Ad Pending Reward (remains claimable for 10 minutes after a win/task).
 * Coins are NEVER auto-credited on win; they are only credited after the user completes one
 * Unity Rewarded Ad (`BP_Rewarded_Android`).
 */
data class PendingClaimReward(
    val id: String,
    val sourceType: String,
    val sourceId: String,
    val eventId: String,
    val headline: String,
    val coins: Long,
    val createdAtMs: Long = System.currentTimeMillis(),
    val expiresAtMs: Long = System.currentTimeMillis() + 10 * 60 * 1000L,
    val errorMessage: String? = null
) {
    fun isExpired(nowMs: Long = System.currentTimeMillis()): Boolean = nowMs > expiresAtMs
}

/**
 * Remote/Admin configurable settings (`settings/guestEnabled`, Panda 1000ms timer, daily limits,
 * game enable/disable flags, tournament configuration, and VIP payment configuration).
 */
data class FoundwayAppSettings(
    val guestEnabled: Boolean = true,
    val pandaShowMillis: Long = 1000L,
    val pandaRewardCoins: Long = 5L,
    val pandaDailyLimit: Int = 5,
    val dailyCheckInCoins: Long = 5L,
    val quizRewardCoins: Long = 3L,
    val unityTestMode: Boolean = false,
    val dailyAdLimitUser: Int = 8,
    val dailyAdLimitGuest: Int = 4,
    val enabledGames: Map<String, Boolean> = mapOf(
        "ludo_arena" to true,
        "carrom_arena" to true,
        "mind_quiz" to true,
        "panda_block_puzzle_8x8" to true,
        "panda_block_puzzle" to true,
        "find_the_panda_3x3" to true,
        "memory_match_4x4" to true,
        "logic_challenge" to true,
        "puz_number_grid" to true,
        "puz_pattern_match" to true,
        "brain_teaser" to true,
        "timed_trivia" to true,
        "daily_challenge" to true,
        "puz_odd_one_out" to true
    )
)

/**
 * Explicit YouTube Watch-and-Earn status lifecycle:
 * - PENDING: Video opened / watch session in progress, not yet verified.
 * - VERIFIED: Watch duration + content checkpoint verified; ready to claim or claiming.
 * - REWARDED: Coins credited to task reward ledger (idempotent, once per video per day).
 * - FAILED: Closed too early, failed verification question, or duplicate attempt blocked.
 */
enum class VideoWatchStatus(val displayLabel: String) {
    PENDING("Pending"),
    VERIFIED("Verified"),
    REWARDED("Rewarded"),
    FAILED("Failed")
}

data class VideoWatchRecord(
    val videoId: String,
    val youtubeVideoId: String,
    val title: String,
    val rewardCoins: Long = 0L,
    val minWatchSeconds: Long = 30L,
    val openedAtMs: Long = System.currentTimeMillis(),
    val watchedSeconds: Long = 0L,
    val checkpointPassed: Boolean = false,
    val status: VideoWatchStatus = VideoWatchStatus.PENDING,
    val verifiedAtMs: Long? = null,
    val transactionId: String? = null,
    val failureReason: String? = null
)

enum class BotDifficulty(val label: String, val description: String) {
    BEGINNER("Beginner Bot", "Makes legal moves with casual strategy"),
    INTERMEDIATE("Intermediate Bot", "Balances offense and safe tiles"),
    ADVANCED("Advanced Pro Bot", "Expert tactical AI: prioritizes captures, safe stars & home entry")
}

data class TournamentTierSpec(
    val tierId: String,
    val entryCoins: Long,
    val totalPoolCoins: Long,
    val winnerPrizeCoins: Long
) {
    val entryFeeCoins: Long
        get() = entryCoins

    val label: String
        get() = "Entry $entryCoins Coins • Win $winnerPrizeCoins Coins"

    companion object {
        val TIER_5_WIN_8 = TournamentTierSpec(
            tierId = "tier_5_8",
            entryCoins = 5L,
            totalPoolCoins = 10L,
            winnerPrizeCoins = 8L
        )
        val TIER_15_WIN_25 = TournamentTierSpec(
            tierId = "tier_15_25",
            entryCoins = 15L,
            totalPoolCoins = 30L,
            winnerPrizeCoins = 25L
        )
        val TIER_20_WIN_35 = TournamentTierSpec(
            tierId = "tier_20_35",
            entryCoins = 20L,
            totalPoolCoins = 40L,
            winnerPrizeCoins = 35L
        )

        val STANDARD_1V1_TIERS: List<TournamentTierSpec> = listOf(
            TIER_5_WIN_8,
            TIER_15_WIN_25,
            TIER_20_WIN_35
        )
    }
}

@IgnoreExtraProperties
data class OneVsOneMatchRoom(
    @DocumentId val id: String = "",
    val gameId: String = "ludo_arena",
    val gameTitle: String = "1v1 Ludo Championship",
    val tierId: String = "tier_5_8",
    val entryCoins: Long = 5L,
    val totalPoolCoins: Long = 10L,
    val winnerPrizeCoins: Long = 8L,
    val player1Uid: String = "",
    val player1Name: String = "",
    val player2Uid: String? = null,
    val player2Name: String? = null,
    val playerCount: Int = 1,
    val maxPlayers: Int = 2,
    val status: String = "WAITING", // WAITING (1/2), READY (2/2), IN_PROGRESS (2/2), COMPLETED, CANCELLED
    val winnerUid: String? = null,
    val winnerName: String? = null,
    val player1Score: Int = 0,
    val player2Score: Int = 0,
    val prizeCredited: Boolean = false,
    val entryFeeRefunded: Boolean = false,
    val createdAtMs: Long = System.currentTimeMillis(),
    val completedAtMs: Long? = null
) {
    val roomId: String
        get() = id

    val entryFeeCoins: Long
        get() = entryCoins

    val playerSlotLabel: String
        get() = "${playerCount.coerceIn(1, 2)}/2 Players"

    fun toMap(): Map<String, Any> = mapOf(
        "gameId" to gameId,
        "gameTitle" to gameTitle,
        "tierId" to tierId,
        "entryCoins" to entryCoins,
        "entryFeeCoins" to entryCoins,
        "totalPoolCoins" to totalPoolCoins,
        "winnerPrizeCoins" to winnerPrizeCoins,
        "player1Uid" to player1Uid,
        "player1Name" to player1Name,
        "player2Uid" to (player2Uid ?: ""),
        "player2Name" to (player2Name ?: ""),
        "playerCount" to playerCount,
        "maxPlayers" to 2,
        "status" to status,
        "winnerUid" to (winnerUid ?: ""),
        "winnerName" to (winnerName ?: ""),
        "player1Score" to player1Score,
        "player2Score" to player2Score,
        "prizeCredited" to prizeCredited,
        "entryFeeRefunded" to entryFeeRefunded,
        "createdAtMs" to createdAtMs,
        "completedAtMs" to (completedAtMs ?: 0L)
    )
}

data class HintProgressState(
    val gameId: String,
    val adsCompleted: Int = 0,
    val adsRequired: Int = 2,
    val isUnlocked: Boolean = false,
    val hintText: String? = null,
    val lastError: String? = null,
    val firstAdSessionId: String? = null,
    val secondAdSessionId: String? = null
) {
    val progressLabel: String
        get() = when {
            isUnlocked || adsCompleted >= 2 -> "Hint unlocked! (2/2 ads completed)"
            adsCompleted == 1 -> "Hint progress: 1/2 ads completed"
            else -> "Watch 2 Ads for Hint (0/2)"
        }
}

data class TournamentArenaSpec(
    val id: String,
    val gameId: String,
    val title: String,
    val subtitle: String,
    val category: String = "1v1 Championship",
    val formatLabel: String = "1v1 (2 Players Only)",
    val maxPlayers: Int = 2,
    val tiers: List<TournamentTierSpec> = TournamentTierSpec.STANDARD_1V1_TIERS,
    val practiceRewardCoins: Long = 5L,
    val entryCoins: Long = 5L,
    val advertisedPrizeCoins: Long = 8L,
    val entryFeeInrExample: Int = 5,
    val advertisedPrizeInrExample: Int = 8,
    val rulesSummary: List<String> = emptyList(),
    val complianceNotice: String = "Strictly 1v1 (2 players max). Choose 5/15/20 coin entry tier or play free practice.",
    val isRealPrizeActive: Boolean = true,
    val isEnabled: Boolean = true
) {
    val tournamentId: String
        get() = id
}

data class TournamentMatchRecord(
    val id: String,
    val tournamentId: String,
    val gameTitle: String,
    val opponentLabel: String,
    val botDifficulty: BotDifficulty,
    val playerScore: Int,
    val botScore: Int,
    val resultStatus: String,
    val practiceCoinsEarned: Long = 0L,
    val entryCoinsPaid: Long = 0L,
    val winnerPrizeCoins: Long = 0L,
    val playerCountLabel: String = "2/2",
    val roundsPlayed: Int = 1,
    val playedAtMs: Long = System.currentTimeMillis()
) {
    val prizeCoinsWon: Long
        get() = winnerPrizeCoins
}

/**
 * Explicit Redeem Promo Code status lifecycle:
 * - AVAILABLE: Code is active, within validity window, and has stock remaining.
 * - OUT_OF_STOCK: Code has reached its maximum claim limit.
 * - EXPIRED: Code has passed its expiration timestamp.
 * - DISABLED: Code was temporarily deactivated by an admin.
 * - ALREADY_REDEEMED: Current user has already claimed this single-use code.
 * - INVALID: Code string does not exist or was malformed.
 */
enum class RedeemCodeStatus(val label: String, val isRedeemable: Boolean) {
    AVAILABLE("Available", true),
    OUT_OF_STOCK("Out of Stock", false),
    EXPIRED("Expired", false),
    DISABLED("Disabled", false),
    ALREADY_REDEEMED("Already Claimed", false),
    INVALID("Invalid Code", false)
}

@IgnoreExtraProperties
data class RedeemPromoCode(
    @DocumentId val id: String = "",
    val code: String = "",
    val title: String = "",
    val description: String = "",
    val coinReward: Long = 50L,
    val totalStock: Long = 100L,
    val claimedCount: Long = 0L,
    val isEnabled: Boolean = true,
    val expiresAtMs: Long? = null,
    val targetGame: String = "All Features",
    @ServerTimestamp val createdAt: Timestamp? = null
) {
    val remainingStock: Long
        get() = (totalStock - claimedCount).coerceAtLeast(0L)

    val rewardCoins: Long
        get() = coinReward

    val maxRedemptions: Long
        get() = totalStock

    val active: Boolean
        get() = isEnabled

    fun evaluateStatus(claimedByUsers: Set<String>, currentUid: String): RedeemCodeStatus {
        if (!isEnabled) return RedeemCodeStatus.DISABLED
        if (expiresAtMs != null && System.currentTimeMillis() > expiresAtMs) return RedeemCodeStatus.EXPIRED
        if (claimedByUsers.contains(currentUid)) return RedeemCodeStatus.ALREADY_REDEEMED
        if (claimedCount >= totalStock) return RedeemCodeStatus.OUT_OF_STOCK
        return RedeemCodeStatus.AVAILABLE
    }

    fun toMap(): Map<String, Any> = mapOf(
        "code" to code,
        "title" to title,
        "description" to description,
        "coinReward" to coinReward,
        "totalStock" to totalStock,
        "claimedCount" to claimedCount,
        "isEnabled" to isEnabled,
        "expiresAtMs" to (expiresAtMs ?: 0L),
        "targetGame" to targetGame,
        "createdAt" to (createdAt ?: FieldValue.serverTimestamp())
    )
}

@IgnoreExtraProperties
data class PromoCodeRedemption(
    @DocumentId val id: String = "",
    val code: String = "",
    val userId: String = "",
    val coinsAwarded: Long = 0L,
    val transactionId: String = "",
    val redeemedAtMs: Long = System.currentTimeMillis()
)

/**
 * Dual Platform Mode:
 * - TASK_AND_EARN: Missions, tournaments, panda block puzzle, mini-games, daily bonus.
 * - CREATE_AND_EARN: Creator studio, video feed, watch player, community uploads, channel monetization.
 * Both modes share the exact same user account and single authoritative coin ledger.
 */
enum class AppMode(val title: String, val subtitle: String) {
    TASK_AND_EARN("Task & Earn", "Play, solve puzzles & complete verified missions"),
    CREATE_AND_EARN("Create & Earn", "Watch clips, publish videos & build your creator channel")
}

@IgnoreExtraProperties
data class VideoPost(
    @DocumentId val id: String = "",
    val title: String = "",
    val description: String = "",
    val channelId: String = "",
    val channelName: String = "",
    val authorUid: String = "",
    val videoCategory: String = "Gaming",
    val durationSeconds: Long = 60L,
    val viewsCount: Long = 0L,
    val likesCount: Long = 0L,
    val commentsCount: Long = 0L,
    val videoUrl: String = "",
    val localVideoUri: String = "",
    val fileSizeBytes: Long = 0L,
    val status: String = "published", // preparing, uploading, processing, published, failed, deleted
    val visibility: String = "public", // public, private
    val isVerifiedCreator: Boolean = false,
    val watchRewardCoins: Long = 2L,
    val uploadedAtMs: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "title" to title,
        "description" to description,
        "channelId" to channelId,
        "channelName" to channelName,
        "authorUid" to authorUid,
        "videoCategory" to videoCategory,
        "durationSeconds" to durationSeconds,
        "viewsCount" to viewsCount,
        "likesCount" to likesCount,
        "commentsCount" to commentsCount,
        "videoUrl" to videoUrl,
        "localVideoUri" to localVideoUri,
        "fileSizeBytes" to fileSizeBytes,
        "status" to status,
        "visibility" to visibility,
        "isVerifiedCreator" to isVerifiedCreator,
        "watchRewardCoins" to watchRewardCoins,
        "uploadedAtMs" to uploadedAtMs
    )
}

@IgnoreExtraProperties
data class VideoComment(
    @DocumentId val id: String = "",
    val videoId: String = "",
    val authorUid: String = "",
    val authorName: String = "",
    val commentText: String = "",
    val likesCount: Long = 0L,
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "videoId" to videoId,
        "authorUid" to authorUid,
        "authorName" to authorName,
        "commentText" to commentText,
        "likesCount" to likesCount,
        "createdAtMs" to createdAtMs
    )
}

@IgnoreExtraProperties
data class CreatorChannel(
    @DocumentId val channelId: String = "",
    val ownerUid: String = "",
    val channelName: String = "Athxo Booyah Creator",
    val handle: String = "@athxo_creator",
    val bio: String = "Athxo Booyah gaming & puzzle content creator",
    val subscribersCount: Long = 0L,
    val totalViews: Long = 0L,
    val totalVideos: Long = 0L,
    val totalCoinsEarned: Long = 0L,
    val monetizationStatus: String = "NOT_ELIGIBLE", // NOT_ELIGIBLE, ELIGIBLE, SUBMITTED, APPROVED, REJECTED
    val createdAtMs: Long = System.currentTimeMillis()
) {
    fun toMap(): Map<String, Any> = mapOf(
        "ownerUid" to ownerUid,
        "channelName" to channelName,
        "handle" to handle,
        "bio" to bio,
        "subscribersCount" to subscribersCount,
        "totalViews" to totalViews,
        "totalVideos" to totalVideos,
        "totalCoinsEarned" to totalCoinsEarned,
        "monetizationStatus" to monetizationStatus,
        "createdAtMs" to createdAtMs
    )
}

@IgnoreExtraProperties
data class CreatorMonetizationApplication(
    @DocumentId val id: String = "",
    val channelId: String = "",
    val ownerUid: String = "",
    val channelName: String = "",
    val subscriberCountAtApplication: Long = 0L,
    val totalViewsAtApplication: Long = 0L,
    val status: String = "SUBMITTED", // SUBMITTED, UNDER_REVIEW, APPROVED, REJECTED
    val appliedAtMs: Long = System.currentTimeMillis(),
    val reviewNotes: String? = null
) {
    val creatorId: String
        get() = ownerUid
}

data class MemoryCard(
    val id: Int,
    val pairId: Int,
    val emoji: String,
    val label: String,
    val isFaceUp: Boolean = false,
    val isMatched: Boolean = false
)

enum class MemoryDifficulty(
    val id: String,
    val title: String,
    val rows: Int,
    val cols: Int,
    val pairsCount: Int,
    val baseRewardCoins: Long
) {
    EASY("EASY", "Easy (3×4)", 4, 3, 6, 4L),
    MEDIUM("MEDIUM", "Medium (4×4)", 4, 4, 8, 6L),
    HARD("HARD", "Hard (4×5)", 5, 4, 10, 8L)
}

data class BubblePopCell(
    val row: Int,
    val col: Int,
    val colorIndex: Int, // 0=Green, 1=Purple, 2=Gold, 3=Blue, 4=Coral
    val colorName: String
)

data class MiniGamePersonalBest(
    val userId: String,
    val gameType: String, // MEMORY_MATCH, BUBBLE_POP, PANDA_PUZZLE
    val difficulty: String,
    val bestScore: Int = 0,
    val bestMoves: Int = 0,
    val bestDurationSeconds: Int = 0,
    val gamesPlayed: Int = 0,
    val updatedAtMs: Long = System.currentTimeMillis()
)

data class MiniGameSessionResult(
    val sessionId: String,
    val userId: String,
    val gameType: String,
    val difficulty: String,
    val score: Int,
    val movesCount: Int,
    val durationSeconds: Int,
    val coinsAwarded: Long,
    val newWalletBalance: Long,
    val isNewPersonalBest: Boolean,
    val personalBest: MiniGamePersonalBest
)

data class AuthoritativeGameMatch(
    val matchId: String,
    val userId: String,
    val gameType: String, // LUDO, CARROM
    val matchTitle: String,
    val entryFeeCoins: Long,
    val prizeCoins: Long,
    val status: String, // IN_PROGRESS, COMPLETED_WON, COMPLETED_LOST, SURRENDERED, CANCELLED
    val currentTurn: String, // PLAYER, OPPONENT
    val turnNumber: Int,
    val playerScore: Int,
    val opponentScore: Int,
    val boardStateJson: String,
    val entryIdempotencyKey: String,
    val settlementIdempotencyKey: String? = null,
    val winnerUserId: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val updatedAtMs: Long = System.currentTimeMillis(),
    val completedAtMs: Long? = null
)

data class LudoBoardState(
    val playerTokens: List<Int> = listOf(-1, -1, -1, -1),   // -1 = in base, 0..55 = track/home path, 56 = home triangle
    val opponentTokens: List<Int> = listOf(-1, -1, -1, -1), // -1 = in base, 0..55 = track/home path, 56 = home triangle
    val currentTurn: String = "PLAYER",                     // PLAYER or OPPONENT
    val lastDiceRoll: Int? = null,
    val awaitingTokenMove: Boolean = false,
    val consecutiveSixes: Int = 0,
    val capturesByPlayer: Int = 0,
    val capturesByOpponent: Int = 0,
    val statusMessage: String = "Your turn! Tap Roll Dice (6 leaves base).",
    val winner: String? = null                              // PLAYER or OPPONENT
)

data class CarromDiscState(
    val id: Int,
    val type: String, // WHITE, BLACK, QUEEN
    val x: Float,     // Normalized -0.82f .. 0.82f from board center
    val y: Float,
    val vx: Float = 0f,
    val vy: Float = 0f,
    val isPocketed: Boolean = false,
    val pocketedBy: String? = null
)

data class CarromBoardState(
    val discs: List<CarromDiscState>,
    val strikerX: Float = 0f, // Normalized -0.55f .. 0.55f along baseline
    val currentTurn: String = "PLAYER",
    val playerScore: Int = 0,
    val opponentScore: Int = 0,
    val queenPendingCoverBy: String? = null,
    val queenCoveredBy: String? = null,
    val turnsRemaining: Int = 16,
    val foulCommittedLastTurn: Boolean = false,
    val statusMessage: String = "Place striker on baseline, aim & shoot!",
    val winner: String? = null
)

data class VideoProofSubmission(
    val submissionId: String,
    val userId: String,
    val userDisplayName: String,
    val taskId: String,
    val taskTitle: String,
    val rewardCoins: Long,
    val videoUri: String,
    val mimeType: String,
    val fileSizeBytes: Long,
    val durationSeconds: Int,
    val metadataHash: String,
    val status: String, // pending, approved, rejected
    val rejectionReason: String = "",
    val reviewedByStaffUid: String? = null,
    val settlementIdempotencyKey: String? = null,
    val createdAtMs: Long = System.currentTimeMillis(),
    val reviewedAtMs: Long? = null
)

class InsufficientMatchCoinsException(
    override val message: String,
    val isPlayAgain: Boolean = false
) : IllegalStateException(message)

data class NumberSequenceQuestion(
    val id: String,
    val prompt: String,
    val sequenceDisplay: String,
    val options: List<String>,
    val correctIndex: Int,
    val ruleExplanation: String,
    val rewardCoins: Long = 4L
)
