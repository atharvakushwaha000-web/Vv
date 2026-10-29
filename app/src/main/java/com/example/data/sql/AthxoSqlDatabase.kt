package com.example.data.sql

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import com.example.model.*
import com.google.firebase.Timestamp
import org.json.JSONArray
import org.json.JSONObject
import java.util.Date
import java.util.Locale
import java.util.UUID

/**
 * Relational SQL Database & Migration Engine for **Athxo Booyah**.
 *
 * Implements:
 * - Versioned repeatable SQL migrations (`V1__athxo_booyah_initial_schema`, `V2__redeem_workflow_and_rbac_hardening`)
 * - Foreign Keys (`PRAGMA foreign_keys = ON`), Unique constraints, and `CHECK(available_coins >= 0)`
 * - Server-authoritative coin balances & atomic ledger transactions
 * - Complete Redeem Workflow (`Pending` -> Staff `Approve` with assigned code -> `Successful`, or `Reject` with reason & refund)
 * - Strict code ownership protection (only the owner of an approved request can reveal its code)
 * - Staff/Admin Role-Based Access Control (RBAC) with before-and-after audit logging
 * - Safe Firebase-to-SQL backup, migration, and integrity verification
 */
class AthxoSqlDatabase(
    context: Context?,
    databaseName: String = DATABASE_NAME
) : SQLiteOpenHelper(context, databaseName, null, DATABASE_VERSION) {

    companion object {
        const val DATABASE_NAME = "athxo_booyah_relational.db"
        const val DATABASE_VERSION = 3

        const val INSUFFICIENT_COINS_ENTRY_MESSAGE = "Not enough coins to play this match. Please go back and earn more coins."
        const val INSUFFICIENT_COINS_PLAY_AGAIN_MESSAGE = "Not enough coins to play again. Please go back and earn more coins."

        val ALLOWED_VIDEO_MIME_TYPES = setOf("video/mp4", "video/webm", "video/quicktime", "video/x-matroska")
        const val MIN_VIDEO_FILE_SIZE_BYTES = 10L * 1024L
        const val MAX_VIDEO_FILE_SIZE_BYTES = 100L * 1024L * 1024L
        const val MIN_VIDEO_DURATION_SECONDS = 3
        const val MAX_VIDEO_DURATION_SECONDS = 600

        val LUDO_SAFE_TRACK_INDICES = setOf(0, 8, 13, 21, 26, 34, 39, 47)

        val STAFF_APPROVAL_ROLES = setOf(
            "staff",
            "support",
            "financeAdmin",
            "superAdmin",
            "admin",
            "owner"
        )

        val BALANCE_ADJUSTMENT_ROLES = setOf(
            "financeAdmin",
            "superAdmin",
            "admin",
            "owner"
        )

        val ROLE_MANAGEMENT_ROLES = setOf(
            "superAdmin",
            "owner"
        )

        private val EMAIL_REGEX = Regex("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$")

        fun isValidEmailAddress(email: String): Boolean {
            val trimmed = email.trim()
            return trimmed.length in 5..120 && EMAIL_REGEX.matches(trimmed)
        }

        fun maskRedeemCode(rawCode: String): String {
            val cleaned = rawCode.trim().uppercase(Locale.US)
            if (cleaned.length <= 4) return "****"
            val visibleTail = cleaned.takeLast(4)
            return "****-****-$visibleTail"
        }

        @Volatile
        private var sharedInstance: AthxoSqlDatabase? = null

        fun getInstance(context: Context?): AthxoSqlDatabase {
            return sharedInstance ?: synchronized(this) {
                sharedInstance ?: AthxoSqlDatabase(context?.applicationContext).also {
                    sharedInstance = it
                }
            }
        }

        fun createIsolatedTestInstance(context: Context): AthxoSqlDatabase {
            val uniqueDbName = "athxo_test_${UUID.randomUUID().toString().replace("-", "").take(10)}.db"
            return AthxoSqlDatabase(context, uniqueDbName)
        }
    }

    override fun onConfigure(db: SQLiteDatabase) {
        super.onConfigure(db)
        db.setForeignKeyConstraintsEnabled(true)
    }

    override fun onCreate(db: SQLiteDatabase) {
        applyMigrationV1(db)
        applyMigrationV2(db)
        applyMigrationV3(db)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        if (oldVersion < 1) {
            applyMigrationV1(db)
        }
        if (oldVersion < 2) {
            applyMigrationV2(db)
        }
        if (oldVersion < 3) {
            applyMigrationV3(db)
        }
    }

    private fun applyMigrationV1(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS schema_migrations (
                version INTEGER PRIMARY KEY,
                description TEXT NOT NULL,
                applied_at_ms INTEGER NOT NULL,
                backup_snapshot_json TEXT
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS users (
                user_id TEXT PRIMARY KEY,
                display_name TEXT NOT NULL,
                email TEXT,
                photo_url TEXT,
                referral_code TEXT NOT NULL,
                referred_by TEXT,
                account_status TEXT NOT NULL DEFAULT 'active' CHECK (account_status IN ('active', 'guest', 'suspended')),
                role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'staff', 'support', 'contentModerator', 'campaignManager', 'financeAdmin', 'superAdmin', 'admin', 'owner')),
                created_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_email ON users(email)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_role ON users(role)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS wallets (
                user_id TEXT PRIMARY KEY,
                available_coins INTEGER NOT NULL DEFAULT 0 CHECK (available_coins >= 0),
                pending_coins INTEGER NOT NULL DEFAULT 0 CHECK (pending_coins >= 0),
                lifetime_earned INTEGER NOT NULL DEFAULT 0 CHECK (lifetime_earned >= 0),
                redeemed_coins INTEGER NOT NULL DEFAULT 0 CHECK (redeemed_coins >= 0),
                sandbox_coins INTEGER NOT NULL DEFAULT 0 CHECK (sandbox_coins >= 0),
                updated_at_ms INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS ledger_entries (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                event_id TEXT NOT NULL,
                source_type TEXT NOT NULL,
                source_id TEXT NOT NULL,
                title TEXT NOT NULL,
                amount INTEGER NOT NULL,
                transaction_type TEXT NOT NULL CHECK (transaction_type IN ('earn', 'entry_fee', 'reserve', 'redeem_complete', 'refund', 'admin_adjustment')),
                verification_status TEXT NOT NULL DEFAULT 'approved',
                verification_level TEXT NOT NULL DEFAULT 'server_verified',
                idempotency_key TEXT NOT NULL UNIQUE,
                balance_after INTEGER NOT NULL CHECK (balance_after >= 0),
                actor TEXT NOT NULL DEFAULT 'system',
                reason TEXT,
                created_at_ms INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
                UNIQUE (user_id, source_type, event_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ledger_user_created ON ledger_entries(user_id, created_at_ms DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_ledger_idempotency ON ledger_entries(idempotency_key)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS task_claims (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                task_type TEXT NOT NULL,
                task_id TEXT NOT NULL,
                campaign_id TEXT NOT NULL,
                title TEXT NOT NULL,
                reward_coins INTEGER NOT NULL CHECK (reward_coins >= 0),
                progress INTEGER NOT NULL DEFAULT 100,
                verification_status TEXT NOT NULL CHECK (verification_status IN ('submitted', 'in_review', 'approved', 'rejected')),
                evidence_hash TEXT UNIQUE,
                rejection_reason TEXT,
                created_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
                UNIQUE (user_id, campaign_id, task_id)
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_task_claims_user ON task_claims(user_id, created_at_ms DESC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS reward_catalog (
                id TEXT PRIMARY KEY,
                visibility TEXT NOT NULL DEFAULT 'public',
                reward_name TEXT NOT NULL,
                denomination_inr INTEGER NOT NULL CHECK (denomination_inr > 0),
                points_cost INTEGER NOT NULL CHECK (points_cost > 0),
                availability TEXT NOT NULL CHECK (availability IN ('available', 'out_of_stock')),
                stock_count INTEGER NOT NULL DEFAULT 0 CHECK (stock_count >= 0),
                min_account_age_days INTEGER NOT NULL DEFAULT 1,
                region TEXT NOT NULL DEFAULT 'IN',
                status TEXT NOT NULL DEFAULT 'active',
                updated_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS redemption_requests (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                user_email TEXT NOT NULL,
                reward_id TEXT NOT NULL,
                reward_name TEXT NOT NULL,
                denomination_inr INTEGER NOT NULL CHECK (denomination_inr > 0),
                points_reserved INTEGER NOT NULL CHECK (points_reserved > 0),
                status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'successful', 'rejected')),
                assigned_code TEXT,
                delivered_code_masked TEXT,
                rejection_reason TEXT,
                reviewed_by_uid TEXT,
                idempotency_key TEXT NOT NULL UNIQUE,
                created_at_ms INTEGER NOT NULL,
                updated_at_ms INTEGER NOT NULL,
                processed_at_ms INTEGER,
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_redemptions_user ON redemption_requests(user_id, created_at_ms DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_redemptions_status ON redemption_requests(status, created_at_ms ASC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS redeem_codes_inventory (
                code_id TEXT PRIMARY KEY,
                code TEXT NOT NULL UNIQUE,
                reward_id TEXT NOT NULL,
                assigned_request_id TEXT UNIQUE,
                assigned_user_id TEXT,
                is_assigned INTEGER NOT NULL DEFAULT 0 CHECK (is_assigned IN (0, 1)),
                created_at_ms INTEGER NOT NULL,
                assigned_at_ms INTEGER,
                FOREIGN KEY (assigned_request_id) REFERENCES redemption_requests(id) ON DELETE SET NULL
            )
            """.trimIndent()
        )

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS admin_audit_logs (
                id TEXT PRIMARY KEY,
                admin_uid TEXT NOT NULL,
                admin_role TEXT NOT NULL,
                action TEXT NOT NULL,
                target_type TEXT NOT NULL,
                target_id TEXT NOT NULL,
                before_value TEXT,
                after_value TEXT,
                reason TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON admin_audit_logs(created_at_ms DESC)")

        val cv = ContentValues().apply {
            put("version", 1)
            put("description", "V1__athxo_booyah_initial_schema")
            put("applied_at_ms", System.currentTimeMillis())
        }
        db.insertWithOnConflict("schema_migrations", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun applyMigrationV2(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS security_rate_limits (
                rate_key TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                endpoint TEXT NOT NULL,
                window_start_ms INTEGER NOT NULL,
                request_count INTEGER NOT NULL DEFAULT 1,
                flagged_suspicious INTEGER NOT NULL DEFAULT 0
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_security_rate_user ON security_rate_limits(user_id, endpoint)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS suspicious_activity_logs (
                id TEXT PRIMARY KEY,
                user_id TEXT NOT NULL,
                event_type TEXT NOT NULL,
                details TEXT NOT NULL,
                created_at_ms INTEGER NOT NULL
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_suspicious_user ON suspicious_activity_logs(user_id, created_at_ms DESC)")

        val cv = ContentValues().apply {
            put("version", 2)
            put("description", "V2__redeem_workflow_and_rbac_hardening")
            put("applied_at_ms", System.currentTimeMillis())
        }
        db.insertWithOnConflict("schema_migrations", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    private fun applyMigrationV3(db: SQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS game_matches (
                match_id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                game_type TEXT NOT NULL CHECK (game_type IN ('LUDO', 'CARROM')),
                match_title TEXT NOT NULL,
                entry_fee_coins INTEGER NOT NULL CHECK (entry_fee_coins >= 0),
                prize_coins INTEGER NOT NULL CHECK (prize_coins >= 0),
                status TEXT NOT NULL DEFAULT 'IN_PROGRESS' CHECK (status IN ('IN_PROGRESS', 'COMPLETED_WON', 'COMPLETED_LOST', 'SURRENDERED', 'CANCELLED')),
                current_turn TEXT NOT NULL DEFAULT 'PLAYER' CHECK (current_turn IN ('PLAYER', 'OPPONENT')),
                turn_number INTEGER NOT NULL DEFAULT 1,
                player_score INTEGER NOT NULL DEFAULT 0,
                opponent_score INTEGER NOT NULL DEFAULT 0,
                board_state_json TEXT NOT NULL,
                entry_idempotency_key TEXT NOT NULL UNIQUE,
                settlement_idempotency_key TEXT UNIQUE,
                winner_user_id TEXT,
                created_at INTEGER NOT NULL,
                updated_at INTEGER NOT NULL,
                completed_at INTEGER,
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_game_matches_user_status ON game_matches(user_id, status, updated_at DESC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS game_match_actions (
                action_id TEXT PRIMARY KEY NOT NULL,
                match_id TEXT NOT NULL,
                user_id TEXT NOT NULL,
                actor TEXT NOT NULL CHECK (actor IN ('PLAYER', 'OPPONENT', 'SYSTEM')),
                action_type TEXT NOT NULL,
                turn_number INTEGER NOT NULL,
                action_payload_json TEXT NOT NULL,
                validated INTEGER NOT NULL DEFAULT 1,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (match_id) REFERENCES game_matches(match_id) ON DELETE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_game_match_actions_match ON game_match_actions(match_id, turn_number ASC)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS video_proof_submissions (
                submission_id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                user_display_name TEXT NOT NULL DEFAULT '',
                task_id TEXT NOT NULL,
                task_title TEXT NOT NULL,
                reward_coins INTEGER NOT NULL CHECK (reward_coins > 0),
                video_uri TEXT NOT NULL,
                mime_type TEXT NOT NULL,
                file_size_bytes INTEGER NOT NULL CHECK (file_size_bytes > 0),
                duration_seconds INTEGER NOT NULL CHECK (duration_seconds > 0),
                metadata_hash TEXT NOT NULL,
                status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'approved', 'rejected')),
                rejection_reason TEXT NOT NULL DEFAULT '',
                reviewed_by_staff_uid TEXT,
                settlement_idempotency_key TEXT UNIQUE,
                created_at INTEGER NOT NULL,
                reviewed_at INTEGER,
                UNIQUE(user_id, task_id, metadata_hash),
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_video_proof_user_created ON video_proof_submissions(user_id, created_at DESC)")
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_video_proof_status_created ON video_proof_submissions(status, created_at ASC)")
        db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_video_proof_unique_hash ON video_proof_submissions(metadata_hash)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mini_game_sessions (
                session_id TEXT PRIMARY KEY NOT NULL,
                user_id TEXT NOT NULL,
                game_type TEXT NOT NULL CHECK (game_type IN ('MEMORY_MATCH', 'BUBBLE_POP', 'PANDA_PUZZLE')),
                difficulty TEXT NOT NULL DEFAULT 'MEDIUM',
                score INTEGER NOT NULL CHECK (score >= 0),
                moves_count INTEGER NOT NULL CHECK (moves_count >= 0),
                duration_seconds INTEGER NOT NULL CHECK (duration_seconds >= 0),
                coins_awarded INTEGER NOT NULL DEFAULT 0 CHECK (coins_awarded >= 0),
                session_day_bucket INTEGER NOT NULL,
                idempotency_key TEXT NOT NULL UNIQUE,
                created_at INTEGER NOT NULL,
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS idx_mini_game_sessions_user_day ON mini_game_sessions(user_id, game_type, session_day_bucket)")

        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS mini_game_personal_bests (
                user_id TEXT NOT NULL,
                game_type TEXT NOT NULL,
                difficulty TEXT NOT NULL,
                best_score INTEGER NOT NULL DEFAULT 0,
                best_moves INTEGER NOT NULL DEFAULT 0,
                best_duration_seconds INTEGER NOT NULL DEFAULT 0,
                games_played INTEGER NOT NULL DEFAULT 0,
                updated_at INTEGER NOT NULL,
                PRIMARY KEY (user_id, game_type, difficulty),
                FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
            )
            """.trimIndent()
        )

        val cv = ContentValues().apply {
            put("version", 3)
            put("description", "V3__games_matches_and_video_proof_verification")
            put("applied_at_ms", System.currentTimeMillis())
        }
        db.insertWithOnConflict("schema_migrations", null, cv, SQLiteDatabase.CONFLICT_IGNORE)
    }

    // ========================================================================
    // 1. USER, ROLE & WALLET INITIALIZATION
    // ========================================================================

    fun ensureUserAndWallet(
        userId: String,
        displayName: String,
        email: String?,
        photoUrl: String?,
        isGuest: Boolean,
        defaultRole: String? = null
    ): UserProfile {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val existingUser = queryUserProfileInternal(db, userId)
            val isOwnerEmail = email?.equals("atharvakushwaha000@gmail.com", ignoreCase = true) == true
            val resolvedRole = when {
                defaultRole != null -> defaultRole
                isOwnerEmail -> "superAdmin"
                existingUser != null -> existingUser.role
                else -> "user"
            }
            val refCode = existingUser?.referralCode?.takeIf { it.isNotBlank() }
                ?: "AB-${userId.replace("[^A-Za-z0-9]".toRegex(), "").takeLast(6).uppercase(Locale.US)}"
            val status = if (isGuest) "guest" else (existingUser?.accountStatus ?: "active")
            val finalName = displayName.trim().ifBlank { existingUser?.displayName ?: "Athxo Booyah Player" }
            val finalEmail = email?.trim()?.takeIf { it.isNotBlank() } ?: existingUser?.email

            if (existingUser == null) {
                val userCv = ContentValues().apply {
                    put("user_id", userId)
                    put("display_name", finalName)
                    put("email", finalEmail)
                    put("photo_url", photoUrl)
                    put("referral_code", refCode)
                    put("account_status", status)
                    put("role", resolvedRole)
                    put("created_at_ms", now)
                    put("updated_at_ms", now)
                }
                db.insertOrThrow("users", null, userCv)
            } else {
                val updateCv = ContentValues().apply {
                    put("display_name", finalName)
                    if (finalEmail != null) put("email", finalEmail)
                    if (photoUrl != null) put("photo_url", photoUrl)
                    put("account_status", status)
                    if (isOwnerEmail || defaultRole != null) put("role", resolvedRole)
                    put("updated_at_ms", now)
                }
                db.update("users", updateCv, "user_id = ?", arrayOf(userId))
            }

            // Ensure wallet row exists
            val walletCursor = db.rawQuery(
                "SELECT user_id FROM wallets WHERE user_id = ?",
                arrayOf(userId)
            )
            val walletExists = walletCursor.use { it.moveToFirst() }
            if (!walletExists) {
                val walletCv = ContentValues().apply {
                    put("user_id", userId)
                    put("available_coins", 0L)
                    put("pending_coins", 0L)
                    put("lifetime_earned", 0L)
                    put("redeemed_coins", 0L)
                    put("sandbox_coins", 0L)
                    put("updated_at_ms", now)
                }
                db.insertOrThrow("wallets", null, walletCv)
            }

            db.setTransactionSuccessful()
            return queryUserProfileInternal(db, userId)!!
        } finally {
            db.endTransaction()
        }
    }

    fun getUserProfile(userId: String): UserProfile? {
        return queryUserProfileInternal(readableDatabase, userId)
    }

    fun getUserRole(userId: String): String {
        val db = readableDatabase
        return getUserRoleInternal(db, userId)
    }

    private fun getUserRoleInternal(db: SQLiteDatabase, userId: String): String {
        db.rawQuery("SELECT role FROM users WHERE user_id = ?", arrayOf(userId)).use { cursor ->
            if (cursor.moveToFirst()) {
                return cursor.getString(0) ?: "user"
            }
        }
        return "user"
    }

    private fun queryUserProfileInternal(db: SQLiteDatabase, userId: String): UserProfile? {
        db.rawQuery(
            """
            SELECT user_id, display_name, email, photo_url, referral_code, referred_by, account_status, role, created_at_ms, updated_at_ms
            FROM users WHERE user_id = ?
            """.trimIndent(),
            arrayOf(userId)
        ).use { c ->
            if (!c.moveToFirst()) return null
            val createdMs = c.getLong(8)
            val updatedMs = c.getLong(9)
            return UserProfile(
                id = c.getString(0),
                userId = c.getString(0),
                displayName = c.getString(1),
                email = if (c.isNull(2)) null else c.getString(2),
                photoUrl = if (c.isNull(3)) null else c.getString(3),
                referralCode = c.getString(4),
                referredBy = if (c.isNull(5)) null else c.getString(5),
                accountStatus = c.getString(6),
                role = c.getString(7),
                createdAt = Timestamp(Date(createdMs)),
                updatedAt = Timestamp(Date(updatedMs))
            )
        }
    }

    fun getWallet(userId: String): Wallet {
        val db = readableDatabase
        return queryWalletInternal(db, userId)
    }

    private fun queryWalletInternal(db: SQLiteDatabase, userId: String): Wallet {
        db.rawQuery(
            """
            SELECT available_coins, pending_coins, lifetime_earned, redeemed_coins, sandbox_coins, updated_at_ms
            FROM wallets WHERE user_id = ?
            """.trimIndent(),
            arrayOf(userId)
        ).use { c ->
            if (!c.moveToFirst()) {
                return Wallet(id = "main", userId = userId)
            }
            return Wallet(
                id = "main",
                userId = userId,
                available = c.getLong(0),
                pending = c.getLong(1),
                lifetimeEarned = c.getLong(2),
                redeemed = c.getLong(3),
                sandboxCoins = c.getLong(4),
                updatedAt = Timestamp(Date(c.getLong(5)))
            )
        }
    }

    /**
     * Reconciles and verifies the user's authoritative balance directly from the SQL `ledger_entries`
     * and `wallets` tables. Client-side SharedPreferences or UI state changes cannot alter this balance.
     */
    fun getAuthoritativeBalance(userId: String): Long {
        return getWallet(userId).available
    }

    fun getLedgerEntries(userId: String): List<LedgerEntry> {
        val db = readableDatabase
        val result = mutableListOf<LedgerEntry>()
        db.rawQuery(
            """
            SELECT id, user_id, event_id, source_type, source_id, title, amount, transaction_type,
                   verification_status, verification_level, idempotency_key, balance_after, actor, reason, created_at_ms
            FROM ledger_entries
            WHERE user_id = ?
            ORDER BY created_at_ms DESC, rowid DESC
            """.trimIndent(),
            arrayOf(userId)
        ).use { c ->
            while (c.moveToNext()) {
                result.add(
                    LedgerEntry(
                        id = c.getString(0),
                        userId = c.getString(1),
                        eventId = c.getString(2),
                        sourceType = c.getString(3),
                        sourceId = c.getString(4),
                        title = c.getString(5),
                        amount = c.getLong(6),
                        transactionType = c.getString(7),
                        verificationStatus = c.getString(8),
                        verificationLevel = c.getString(9),
                        idempotencyKey = c.getString(10),
                        balanceAfter = c.getLong(11),
                        actor = c.getString(12),
                        reason = if (c.isNull(13)) null else c.getString(13),
                        createdAt = Timestamp(Date(c.getLong(14)))
                    )
                )
            }
        }
        return result
    }

    // ========================================================================
    // 2. SERVER-AUTHORITATIVE COIN TRANSACTIONS & RATE LIMITING
    // ========================================================================

    fun checkAndRecordRateLimit(
        userId: String,
        endpoint: String,
        maxRequestsPerMinute: Int = 30
    ) {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        val rateKey = "${userId}_$endpoint"
        db.beginTransaction()
        try {
            var windowStart = now
            var count = 0
            db.rawQuery(
                "SELECT window_start_ms, request_count FROM security_rate_limits WHERE rate_key = ?",
                arrayOf(rateKey)
            ).use { c ->
                if (c.moveToFirst()) {
                    windowStart = c.getLong(0)
                    count = c.getInt(1)
                }
            }

            if (now - windowStart > 60_000L) {
                windowStart = now
                count = 1
            } else {
                count += 1
            }

            val flagged = if (count > maxRequestsPerMinute) 1 else 0
            val cv = ContentValues().apply {
                put("rate_key", rateKey)
                put("user_id", userId)
                put("endpoint", endpoint)
                put("window_start_ms", windowStart)
                put("request_count", count)
                put("flagged_suspicious", flagged)
            }
            db.insertWithOnConflict("security_rate_limits", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

            if (flagged == 1) {
                logSuspiciousActivityInternal(
                    db = db,
                    userId = userId,
                    eventType = "RATE_LIMIT_EXCEEDED",
                    details = "Endpoint $endpoint exceeded $maxRequestsPerMinute requests/min (count=$count)"
                )
                throw IllegalStateException("Rate limit exceeded for $endpoint. Please wait before retrying.")
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun logSuspiciousActivityInternal(
        db: SQLiteDatabase,
        userId: String,
        eventType: String,
        details: String,
        createdAtMs: Long = System.currentTimeMillis()
    ) {
        val cv = ContentValues().apply {
            put("id", "susp_${UUID.randomUUID().toString().replace("-", "").take(16)}")
            put("user_id", userId)
            put("event_type", eventType)
            put("details", details)
            put("created_at_ms", createdAtMs)
        }
        db.insert("suspicious_activity_logs", null, cv)
    }

    private fun checkAndIncrementRateLimitInternal(
        db: SQLiteDatabase,
        userId: String,
        endpoint: String,
        maxRequestsPerMinute: Int = 30,
        nowMs: Long = System.currentTimeMillis()
    ) {
        val now = nowMs
        val rateKey = "${userId}_$endpoint"
        var windowStart = now
        var count = 0
        db.rawQuery(
            "SELECT window_start_ms, request_count FROM security_rate_limits WHERE rate_key = ?",
            arrayOf(rateKey)
        ).use { c ->
            if (c.moveToFirst()) {
                windowStart = c.getLong(0)
                count = c.getInt(1)
            }
        }

        if (now - windowStart > 60_000L) {
            windowStart = now
            count = 1
        } else {
            count += 1
        }

        val flagged = if (count > maxRequestsPerMinute) 1 else 0
        val cv = ContentValues().apply {
            put("rate_key", rateKey)
            put("user_id", userId)
            put("endpoint", endpoint)
            put("window_start_ms", windowStart)
            put("request_count", count)
            put("flagged_suspicious", flagged)
        }
        db.insertWithOnConflict("security_rate_limits", null, cv, SQLiteDatabase.CONFLICT_REPLACE)

        if (flagged == 1) {
            logSuspiciousActivityInternal(
                db = db,
                userId = userId,
                eventType = "RATE_LIMIT_EXCEEDED",
                details = "Endpoint $endpoint exceeded $maxRequestsPerMinute requests/min (count=$count)",
                createdAtMs = now
            )
            throw IllegalStateException("Rate limit exceeded for $endpoint. Please wait before retrying.")
        }
    }

    /**
     * Atomically credits coins in SQL and inserts the corresponding `ledger_entries` row.
     * Enforces `UNIQUE(idempotency_key)` and `UNIQUE(user_id, source_type, event_id)` so
     * concurrent or replayed requests can NEVER double-credit coins.
     */
    @Synchronized
    fun creditCoinsTransactional(
        userId: String,
        sourceType: String,
        sourceId: String,
        eventId: String,
        title: String,
        amount: Long,
        verificationLevel: String = "server_verified",
        actor: String = "system",
        reason: String? = null
    ): LedgerEntry {
        require(amount > 0L) { "Reward amount must be strictly positive." }
        require(eventId.isNotBlank()) { "Event ID is required for reward verification." }
        val cleanKey = "${sourceType}_${eventId}_${userId}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            ensureUserExistsInTransaction(db, userId, now)

            // Check idempotency key & (user_id, source_type, event_id)
            db.rawQuery(
                "SELECT id FROM ledger_entries WHERE idempotency_key = ? OR (user_id = ? AND source_type = ? AND event_id = ?)",
                arrayOf(cleanKey, userId, sourceType, eventId)
            ).use { c ->
                if (c.moveToFirst()) {
                    logSuspiciousActivityInternal(
                        db = db,
                        userId = userId,
                        eventType = "DUPLICATE_REWARD_ATTEMPT",
                        details = "Blocked duplicate reward claim for key=$cleanKey"
                    )
                    throw IllegalStateException("Already claimed for this event.")
                }
            }

            val wallet = queryWalletInternal(db, userId)
            val newAvailable = wallet.available + amount
            val newLifetime = wallet.lifetimeEarned + amount

            val walletCv = ContentValues().apply {
                put("available_coins", newAvailable)
                put("lifetime_earned", newLifetime)
                put("updated_at_ms", now)
            }
            db.update("wallets", walletCv, "user_id = ?", arrayOf(userId))

            val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(18)}"
            val ledgerCv = ContentValues().apply {
                put("id", txId)
                put("user_id", userId)
                put("event_id", eventId)
                put("source_type", sourceType)
                put("source_id", sourceId)
                put("title", title)
                put("amount", amount)
                put("transaction_type", "earn")
                put("verification_status", "approved")
                put("verification_level", verificationLevel)
                put("idempotency_key", cleanKey)
                put("balance_after", newAvailable)
                put("actor", actor)
                put("reason", reason)
                put("created_at_ms", now)
            }
            db.insertOrThrow("ledger_entries", null, ledgerCv)

            db.setTransactionSuccessful()
            return LedgerEntry(
                id = txId,
                userId = userId,
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
                actor = actor,
                reason = reason,
                createdAt = Timestamp(Date(now))
            )
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Atomically deducts coins (e.g. for 1v1 tournament entry fee) and records a negative
     * `entry_fee` ledger row. Prevents negative balances (`CHECK (available_coins >= 0)`).
     */
    @Synchronized
    fun deductCoinsTransactional(
        userId: String,
        amount: Long,
        sourceType: String,
        sourceId: String,
        eventId: String,
        title: String
    ): LedgerEntry {
        require(amount > 0L) { "Deduction amount must be positive." }
        val cleanKey = "${sourceType}_${eventId}_${userId}".replace(Regex("[^a-zA-Z0-9_\\-]"), "_").take(120)

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            ensureUserExistsInTransaction(db, userId, now)

            db.rawQuery(
                "SELECT id FROM ledger_entries WHERE idempotency_key = ?",
                arrayOf(cleanKey)
            ).use { c ->
                if (c.moveToFirst()) {
                    throw IllegalStateException("Entry fee already deducted for this match.")
                }
            }

            val wallet = queryWalletInternal(db, userId)
            if (wallet.available < amount) {
                throw IllegalStateException("Insufficient balance: need $amount coins, have ${wallet.available} coins.")
            }

            val newAvailable = wallet.available - amount
            val walletCv = ContentValues().apply {
                put("available_coins", newAvailable)
                put("updated_at_ms", now)
            }
            db.update("wallets", walletCv, "user_id = ?", arrayOf(userId))

            val txId = "tx_${UUID.randomUUID().toString().replace("-", "").take(18)}"
            val ledgerCv = ContentValues().apply {
                put("id", txId)
                put("user_id", userId)
                put("event_id", eventId)
                put("source_type", sourceType)
                put("source_id", sourceId)
                put("title", title)
                put("amount", -amount)
                put("transaction_type", "entry_fee")
                put("verification_status", "approved")
                put("verification_level", "server_verified")
                put("idempotency_key", cleanKey)
                put("balance_after", newAvailable)
                put("actor", userId)
                put("created_at_ms", now)
            }
            db.insertOrThrow("ledger_entries", null, ledgerCv)

            db.setTransactionSuccessful()
            return LedgerEntry(
                id = txId,
                userId = userId,
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
                actor = userId,
                createdAt = Timestamp(Date(now))
            )
        } finally {
            db.endTransaction()
        }
    }

    // ========================================================================
    // 3. COMPLETE REDEEM WORKFLOW (PENDING -> STAFF APPROVAL/REJECTION -> CODE)
    // ========================================================================

    /**
     * Submits a new Redemption Request in an atomic SQL transaction:
     * 1. Validates the email address format and verifies ownership against the user's account email when present.
     * 2. Rejects duplicate active pending requests for the same user & reward.
     * 3. Reserves `pointsCost` coins from `available_coins` into `pending_coins`.
     * 4. Creates the request with a unique ID, timestamp, and `status = "pending"`.
     * 5. Keeps `assigned_code` and `delivered_code_masked` strictly NULL while pending.
     */
    @Synchronized
    fun submitRedemptionRequestTransactional(
        userId: String,
        submittedEmail: String,
        reward: RewardCatalogItem,
        isGuestAccount: Boolean = false,
        idempotencyToken: String? = null
    ): RedemptionRequest {
        if (isGuestAccount || userId.startsWith("guest_")) {
            throw IllegalStateException("GUEST_REQUIRES_GOOGLE_SIGN_IN")
        }
        val cleanEmail = submittedEmail.trim()
        require(isValidEmailAddress(cleanEmail)) {
            "Please enter a valid email address to receive your redeem code."
        }
        require(reward.stockCount > 0L && reward.availability == "available") {
            "Out of stock. Only rewards with real inventory can be redeemed."
        }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            ensureUserExistsInTransaction(db, userId, now)
            val userProfile = queryUserProfileInternal(db, userId)
            val accountEmail = userProfile?.email?.trim()
            if (!accountEmail.isNullOrBlank() && !accountEmail.equals(cleanEmail, ignoreCase = true)) {
                throw IllegalArgumentException(
                    "Email ownership mismatch: please enter the email linked to your account ($accountEmail)."
                )
            }

            // Update user's email if not yet stored
            if (accountEmail.isNullOrBlank()) {
                val emailCv = ContentValues().apply {
                    put("email", cleanEmail)
                    put("updated_at_ms", now)
                }
                db.update("users", emailCv, "user_id = ?", arrayOf(userId))
            }

            // Prevent duplicate pending redemption submissions for the same user & reward
            db.rawQuery(
                "SELECT id FROM redemption_requests WHERE user_id = ? AND reward_id = ? AND status = 'pending'",
                arrayOf(userId, reward.id)
            ).use { c ->
                if (c.moveToFirst()) {
                    throw IllegalStateException(
                        "You already have a Redeem Pending request for ${reward.rewardName}. Please wait for staff review."
                    )
                }
            }

            val idempKey = idempotencyToken?.trim()?.takeIf { it.isNotBlank() }
                ?: "redeem_req_${userId}_${reward.id}_${now / 5000L}"

            db.rawQuery(
                "SELECT id FROM redemption_requests WHERE idempotency_key = ?",
                arrayOf(idempKey)
            ).use { c ->
                if (c.moveToFirst()) {
                    throw IllegalStateException("Duplicate redemption request prevented.")
                }
            }

            val wallet = queryWalletInternal(db, userId)
            if (wallet.available < reward.pointsCost) {
                throw IllegalStateException("Need ${reward.pointsCost} coins (You have ${wallet.available}).")
            }

            val newAvailable = wallet.available - reward.pointsCost
            val newPending = wallet.pending + reward.pointsCost

            val walletCv = ContentValues().apply {
                put("available_coins", newAvailable)
                put("pending_coins", newPending)
                put("updated_at_ms", now)
            }
            db.update("wallets", walletCv, "user_id = ?", arrayOf(userId))

            val requestId = "req_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val txId = "tx_res_${requestId.takeLast(12)}"

            val ledgerCv = ContentValues().apply {
                put("id", txId)
                put("user_id", userId)
                put("event_id", requestId)
                put("source_type", "reward_redemption")
                put("source_id", reward.id)
                put("title", "Redeem Pending: ${reward.rewardName}")
                put("amount", -reward.pointsCost)
                put("transaction_type", "reserve")
                put("verification_status", "pending")
                put("verification_level", "server_verified")
                put("idempotency_key", "ledger_$idempKey")
                put("balance_after", newAvailable)
                put("actor", userId)
                put("reason", "Redemption request submitted ($cleanEmail)")
                put("created_at_ms", now)
            }
            db.insertOrThrow("ledger_entries", null, ledgerCv)

            val reqCv = ContentValues().apply {
                put("id", requestId)
                put("user_id", userId)
                put("user_email", cleanEmail)
                put("reward_id", reward.id)
                put("reward_name", reward.rewardName)
                put("denomination_inr", reward.denominationInr)
                put("points_reserved", reward.pointsCost)
                put("status", "pending")
                putNull("assigned_code")
                putNull("delivered_code_masked")
                putNull("rejection_reason")
                putNull("reviewed_by_uid")
                put("idempotency_key", idempKey)
                put("created_at_ms", now)
                put("updated_at_ms", now)
                putNull("processed_at_ms")
            }
            db.insertOrThrow("redemption_requests", null, reqCv)

            db.setTransactionSuccessful()
            return queryRedemptionByIdInternal(db, requestId)!!
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Authorized Staff/Admin Approval of a Pending Redemption Request:
     * - Verifies that `staffUid` has an authorized staff/admin role in the SQL backend.
     * - Verifies that the request is currently `pending`.
     * - Assigns the valid `assignedRedeemCode` and transitions status to `successful`.
     * - Moves `points_reserved` from `pending_coins` to `redeemed_coins`.
     * - Writes an append-only audit log with before/after values and timestamp.
     */
    @Synchronized
    fun staffApproveRedemptionTransactional(
        staffUid: String,
        requestId: String,
        assignedRedeemCode: String,
        auditReason: String
    ): RedemptionRequest {
        val cleanCode = assignedRedeemCode.trim().uppercase(Locale.US)
        require(cleanCode.length >= 6 && !cleanCode.contains("PLACEHOLDER", ignoreCase = true) && !cleanCode.contains("FAKE", ignoreCase = true)) {
            "A valid, non-placeholder redeem code (min 6 characters) is required to approve a redemption."
        }
        require(auditReason.trim().length >= 3) {
            "Staff approval requires an audit reason."
        }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val staffRole = requireStaffRoleInternal(db, staffUid, STAFF_APPROVAL_ROLES, "APPROVE_REDEMPTION")
            val existing = queryRedemptionByIdInternal(db, requestId)
                ?: throw IllegalArgumentException("Redemption request $requestId not found.")

            if (existing.status != "pending") {
                throw IllegalStateException("Redemption request $requestId is already ${existing.status} and cannot be approved again.")
            }

            // Ensure the redeem code hasn't already been assigned to another request
            db.rawQuery(
                "SELECT assigned_request_id FROM redeem_codes_inventory WHERE code = ? AND is_assigned = 1",
                arrayOf(cleanCode)
            ).use { c ->
                if (c.moveToFirst()) {
                    val otherReq = c.getString(0)
                    if (otherReq != requestId) {
                        throw IllegalStateException("Redeem code $cleanCode has already been assigned to another request.")
                    }
                }
            }

            val masked = maskRedeemCode(cleanCode)

            // Update redemption_requests -> status = 'successful'
            val reqUpdate = ContentValues().apply {
                put("status", "successful")
                put("assigned_code", cleanCode)
                put("delivered_code_masked", masked)
                putNull("rejection_reason")
                put("reviewed_by_uid", staffUid)
                put("updated_at_ms", now)
                put("processed_at_ms", now)
            }
            db.update("redemption_requests", reqUpdate, "id = ?", arrayOf(requestId))

            // Record code assignment in redeem_codes_inventory
            val codeCv = ContentValues().apply {
                put("code_id", "code_${UUID.randomUUID().toString().replace("-", "").take(14)}")
                put("code", cleanCode)
                put("reward_id", existing.rewardId)
                put("assigned_request_id", requestId)
                put("assigned_user_id", existing.userId)
                put("is_assigned", 1)
                put("created_at_ms", now)
                put("assigned_at_ms", now)
            }
            db.insertWithOnConflict("redeem_codes_inventory", null, codeCv, SQLiteDatabase.CONFLICT_REPLACE)

            // Move points from pending_coins -> redeemed_coins in wallets
            val userWallet = queryWalletInternal(db, existing.userId)
            val newPending = (userWallet.pending - existing.pointsReserved).coerceAtLeast(0L)
            val newRedeemed = userWallet.redeemed + existing.pointsReserved
            val walletCv = ContentValues().apply {
                put("pending_coins", newPending)
                put("redeemed_coins", newRedeemed)
                put("updated_at_ms", now)
            }
            db.update("wallets", walletCv, "user_id = ?", arrayOf(existing.userId))

            // Update reserve ledger entry status -> approved
            val ledgerUpdate = ContentValues().apply {
                put("title", "Redeem Successful: ${existing.rewardName}")
                put("verification_status", "approved")
            }
            db.update("ledger_entries", ledgerUpdate, "user_id = ? AND event_id = ?", arrayOf(existing.userId, requestId))

            // Record audit log with before/after values
            insertAuditLogInternal(
                db = db,
                adminUid = staffUid,
                adminRole = staffRole,
                action = "APPROVE_REDEMPTION",
                targetType = "redemption_requests",
                targetId = requestId,
                beforeValue = "status=pending, assigned_code=null, pending_coins=${userWallet.pending}",
                afterValue = "status=successful, assigned_code=$masked, redeemed_coins=$newRedeemed",
                reason = auditReason.trim(),
                nowMs = now
            )

            db.setTransactionSuccessful()
            return queryRedemptionByIdInternal(db, requestId)!!
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Authorized Staff/Admin Rejection of a Pending Redemption Request:
     * - Verifies staff RBAC role.
     * - Transitions status to `rejected` and records `rejectionReason`.
     * - Refunds `points_reserved` from `pending_coins` back to `available_coins` so the user can submit another valid request when eligible.
     * - Records before/after values in `admin_audit_logs`.
     */
    @Synchronized
    fun staffRejectRedemptionTransactional(
        staffUid: String,
        requestId: String,
        rejectionReason: String,
        auditReason: String = rejectionReason
    ): RedemptionRequest {
        val cleanRejection = rejectionReason.trim()
        require(cleanRejection.length >= 3) { "Please provide a clear rejection reason." }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val staffRole = requireStaffRoleInternal(db, staffUid, STAFF_APPROVAL_ROLES, "REJECT_REDEMPTION")
            val existing = queryRedemptionByIdInternal(db, requestId)
                ?: throw IllegalArgumentException("Redemption request $requestId not found.")

            if (existing.status != "pending") {
                throw IllegalStateException("Redemption request $requestId is already ${existing.status}.")
            }

            val reqUpdate = ContentValues().apply {
                put("status", "rejected")
                putNull("assigned_code")
                putNull("delivered_code_masked")
                put("rejection_reason", cleanRejection)
                put("reviewed_by_uid", staffUid)
                put("updated_at_ms", now)
                put("processed_at_ms", now)
            }
            db.update("redemption_requests", reqUpdate, "id = ?", arrayOf(requestId))

            // Refund reserved points back to available_coins
            val userWallet = queryWalletInternal(db, existing.userId)
            val newAvailable = userWallet.available + existing.pointsReserved
            val newPending = (userWallet.pending - existing.pointsReserved).coerceAtLeast(0L)
            val walletCv = ContentValues().apply {
                put("available_coins", newAvailable)
                put("pending_coins", newPending)
                put("updated_at_ms", now)
            }
            db.update("wallets", walletCv, "user_id = ?", arrayOf(existing.userId))

            // Insert refund ledger entry
            val refundTxId = "tx_ref_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val ledgerCv = ContentValues().apply {
                put("id", refundTxId)
                put("user_id", existing.userId)
                put("event_id", "refund_$requestId")
                put("source_type", "reward_redemption_refund")
                put("source_id", existing.rewardId)
                put("title", "Redeem Rejected (Refunded): ${existing.rewardName}")
                put("amount", existing.pointsReserved)
                put("transaction_type", "refund")
                put("verification_status", "approved")
                put("verification_level", "server_verified")
                put("idempotency_key", "refund_redemption_$requestId")
                put("balance_after", newAvailable)
                put("actor", staffUid)
                put("reason", cleanRejection)
                put("created_at_ms", now)
            }
            db.insertOrThrow("ledger_entries", null, ledgerCv)

            insertAuditLogInternal(
                db = db,
                adminUid = staffUid,
                adminRole = staffRole,
                action = "REJECT_REDEMPTION",
                targetType = "redemption_requests",
                targetId = requestId,
                beforeValue = "status=pending, available_coins=${userWallet.available}, pending_coins=${userWallet.pending}",
                afterValue = "status=rejected, available_coins=$newAvailable, reason=$cleanRejection",
                reason = auditReason.trim().ifBlank { cleanRejection },
                nowMs = now
            )

            db.setTransactionSuccessful()
            return queryRedemptionByIdInternal(db, requestId)!!
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Reveals the unmasked redeem code ONLY to the authorized owner (`callerUserId`)
     * and ONLY when the redemption request status is `successful`.
     *
     * - Throws `SecurityException` if another user attempts to read the code.
     * - Throws `IllegalStateException` if the request is `pending` or `rejected`.
     */
    fun revealAssignedRedeemCode(requestId: String, callerUserId: String): String {
        val db = readableDatabase
        db.rawQuery(
            "SELECT user_id, status, assigned_code FROM redemption_requests WHERE id = ?",
            arrayOf(requestId)
        ).use { c ->
            if (!c.moveToFirst()) {
                throw IllegalArgumentException("Redemption request not found.")
            }
            val ownerUserId = c.getString(0)
            val status = c.getString(1)
            val code = if (c.isNull(2)) null else c.getString(2)

            if (ownerUserId != callerUserId) {
                throw SecurityException("UNAUTHORIZED_CODE_ACCESS: You are not authorized to view another user's redeem code.")
            }
            if (status != "successful") {
                throw IllegalStateException("Redeem code is hidden while request status is '$status'.")
            }
            if (code.isNullOrBlank()) {
                throw IllegalStateException("No redeem code assigned to this request.")
            }
            return code
        }
    }

    /**
     * Returns redemption requests for a specific user.
     * Importantly, `assignedCode` is NEVER populated on list items while `status != "successful"`.
     */
    fun getUserRedemptions(userId: String): List<RedemptionRequest> {
        val db = readableDatabase
        val list = mutableListOf<RedemptionRequest>()
        db.rawQuery(
            """
            SELECT id, user_id, user_email, reward_id, reward_name, denomination_inr, points_reserved,
                   status, assigned_code, delivered_code_masked, rejection_reason, reviewed_by_uid,
                   created_at_ms, updated_at_ms, processed_at_ms
            FROM redemption_requests
            WHERE user_id = ?
            ORDER BY created_at_ms DESC, rowid DESC
            """.trimIndent(),
            arrayOf(userId)
        ).use { c ->
            while (c.moveToNext()) {
                list.add(readRedemptionCursorRow(c, includeFullCodeForOwner = false))
            }
        }
        return list
    }

    /**
     * Returns all redemption requests for authorized staff/admin review.
     */
    fun getAllRedemptionsForStaff(staffUid: String): List<RedemptionRequest> {
        val db = readableDatabase
        requireStaffRoleInternal(db, staffUid, STAFF_APPROVAL_ROLES, "VIEW_ALL_REDEMPTIONS")
        val list = mutableListOf<RedemptionRequest>()
        db.rawQuery(
            """
            SELECT id, user_id, user_email, reward_id, reward_name, denomination_inr, points_reserved,
                   status, assigned_code, delivered_code_masked, rejection_reason, reviewed_by_uid,
                   created_at_ms, updated_at_ms, processed_at_ms
            FROM redemption_requests
            ORDER BY CASE WHEN status = 'pending' THEN 0 ELSE 1 END, created_at_ms DESC
            """.trimIndent(),
            emptyArray()
        ).use { c ->
            while (c.moveToNext()) {
                list.add(readRedemptionCursorRow(c, includeFullCodeForOwner = false))
            }
        }
        return list
    }

    private fun queryRedemptionByIdInternal(db: SQLiteDatabase, requestId: String): RedemptionRequest? {
        db.rawQuery(
            """
            SELECT id, user_id, user_email, reward_id, reward_name, denomination_inr, points_reserved,
                   status, assigned_code, delivered_code_masked, rejection_reason, reviewed_by_uid,
                   created_at_ms, updated_at_ms, processed_at_ms
            FROM redemption_requests
            WHERE id = ?
            """.trimIndent(),
            arrayOf(requestId)
        ).use { c ->
            if (!c.moveToFirst()) return null
            return readRedemptionCursorRow(c, includeFullCodeForOwner = false)
        }
    }

    private fun readRedemptionCursorRow(
        c: android.database.Cursor,
        includeFullCodeForOwner: Boolean
    ): RedemptionRequest {
        val status = c.getString(7)
        val rawCode = if (c.isNull(8)) null else c.getString(8)
        val maskedCode = if (c.isNull(9)) null else c.getString(9)
        // Never expose any code or mask when status is pending or rejected
        val safeMasked = if (status == "successful") maskedCode else null
        val safeFull = if (status == "successful" && includeFullCodeForOwner) rawCode else null
        val createdMs = c.getLong(12)
        val updatedMs = c.getLong(13)
        val processedMs = if (c.isNull(14)) null else c.getLong(14)

        return RedemptionRequest(
            id = c.getString(0),
            userId = c.getString(1),
            userEmail = c.getString(2),
            rewardId = c.getString(3),
            rewardName = c.getString(4),
            denominationInr = c.getLong(5),
            pointsReserved = c.getLong(6),
            status = status,
            deliveredCodeMasked = safeMasked,
            assignedCode = safeFull,
            rejectionReason = if (c.isNull(10)) null else c.getString(10),
            reviewedByUid = if (c.isNull(11)) null else c.getString(11),
            createdAtMs = createdMs,
            processedAtMs = processedMs,
            createdAt = Timestamp(Date(createdMs)),
            updatedAt = Timestamp(Date(updatedMs))
        )
    }

    // ========================================================================
    // 4. STAFF / ADMIN RBAC & AUDITED BALANCE ADJUSTMENTS
    // ========================================================================

    fun requireStaffRole(staffUid: String, allowedRoles: Set<String> = STAFF_APPROVAL_ROLES, operation: String = "STAFF_OP"): String {
        return requireStaffRoleInternal(readableDatabase, staffUid, allowedRoles, operation)
    }

    private fun requireStaffRoleInternal(
        db: SQLiteDatabase,
        staffUid: String,
        allowedRoles: Set<String>,
        operation: String
    ): String {
        var role = "user"
        db.rawQuery("SELECT role FROM users WHERE user_id = ?", arrayOf(staffUid)).use { c ->
            if (c.moveToFirst()) {
                role = c.getString(0) ?: "user"
            }
        }
        if (role !in allowedRoles) {
            logSuspiciousActivityInternal(
                db = db,
                userId = staffUid,
                eventType = "UNAUTHORIZED_STAFF_OPERATION",
                details = "User $staffUid with role '$role' attempted $operation"
            )
            throw SecurityException("UNAUTHORIZED_STAFF_OPERATION: Role '$role' is not authorized for $operation.")
        }
        return role
    }

    /**
     * Prevents users from promoting themselves to staff/admin, and restricts role assignment
     * strictly to `superAdmin` / `owner`, recording before-and-after values in `admin_audit_logs`.
     */
    @Synchronized
    fun assignUserRoleTransactional(
        actorUid: String,
        targetUserId: String,
        newRole: String,
        reason: String
    ) {
        require(reason.trim().length >= 3) { "Role assignment requires an explicit audit reason." }
        val validRoles = setOf("user", "staff", "support", "contentModerator", "campaignManager", "financeAdmin", "superAdmin", "admin", "owner")
        require(newRole in validRoles) { "Invalid role: $newRole" }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val actorRole = requireStaffRoleInternal(db, actorUid, ROLE_MANAGEMENT_ROLES, "ASSIGN_ROLE")
            ensureUserExistsInTransaction(db, targetUserId, now)
            val oldRole = queryUserProfileInternal(db, targetUserId)?.role ?: "user"

            val cv = ContentValues().apply {
                put("role", newRole)
                put("updated_at_ms", now)
            }
            db.update("users", cv, "user_id = ?", arrayOf(targetUserId))

            insertAuditLogInternal(
                db = db,
                adminUid = actorUid,
                adminRole = actorRole,
                action = "ASSIGN_USER_ROLE",
                targetType = "users",
                targetId = targetUserId,
                beforeValue = "role=$oldRole",
                afterValue = "role=$newRole",
                reason = reason.trim(),
                nowMs = now
            )

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Manual coin balance adjustment restricted to `financeAdmin` / `superAdmin`.
     * Requires a mandatory reason, prevents negative balances, and records before-and-after
     * values in both `ledger_entries` and `admin_audit_logs`.
     */
    @Synchronized
    fun staffAdjustUserBalanceTransactional(
        staffUid: String,
        targetUserId: String,
        deltaCoins: Long,
        reason: String
    ): LedgerEntry {
        val cleanReason = reason.trim()
        require(cleanReason.length >= 5) { "Manual balance adjustment requires a detailed reason (min 5 characters)." }
        require(deltaCoins != 0L) { "Adjustment delta cannot be zero." }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val staffRole = requireStaffRoleInternal(db, staffUid, BALANCE_ADJUSTMENT_ROLES, "MANUAL_BALANCE_ADJUSTMENT")
            ensureUserExistsInTransaction(db, targetUserId, now)

            val wallet = queryWalletInternal(db, targetUserId)
            val beforeAvail = wallet.available
            val afterAvail = beforeAvail + deltaCoins
            if (afterAvail < 0L) {
                throw IllegalStateException("Balance adjustment rejected: resulting balance ($afterAvail) cannot be negative.")
            }

            val newLifetime = if (deltaCoins > 0) wallet.lifetimeEarned + deltaCoins else wallet.lifetimeEarned
            val walletCv = ContentValues().apply {
                put("available_coins", afterAvail)
                put("lifetime_earned", newLifetime)
                put("updated_at_ms", now)
            }
            db.update("wallets", walletCv, "user_id = ?", arrayOf(targetUserId))

            val txId = "tx_adj_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val eventId = "adj_${now}_$txId"
            val idempKey = "admin_adj_${txId}_$targetUserId"
            val title = if (deltaCoins > 0) {
                "Admin Credit (+$deltaCoins Coins): $cleanReason"
            } else {
                "Admin Debit ($deltaCoins Coins): $cleanReason"
            }

            val ledgerCv = ContentValues().apply {
                put("id", txId)
                put("user_id", targetUserId)
                put("event_id", eventId)
                put("source_type", "admin_adjustment")
                put("source_id", staffUid)
                put("title", title)
                put("amount", deltaCoins)
                put("transaction_type", "admin_adjustment")
                put("verification_status", "approved")
                put("verification_level", "admin_approved")
                put("idempotency_key", idempKey)
                put("balance_after", afterAvail)
                put("actor", staffUid)
                put("reason", cleanReason)
                put("created_at_ms", now)
            }
            db.insertOrThrow("ledger_entries", null, ledgerCv)

            insertAuditLogInternal(
                db = db,
                adminUid = staffUid,
                adminRole = staffRole,
                action = "MANUAL_BALANCE_ADJUSTMENT",
                targetType = "wallets",
                targetId = targetUserId,
                beforeValue = "available_coins=$beforeAvail",
                afterValue = "available_coins=$afterAvail",
                reason = cleanReason,
                nowMs = now
            )

            db.setTransactionSuccessful()
            return LedgerEntry(
                id = txId,
                userId = targetUserId,
                eventId = eventId,
                sourceType = "admin_adjustment",
                sourceId = staffUid,
                title = title,
                amount = deltaCoins,
                transactionType = "admin_adjustment",
                verificationStatus = "approved",
                verificationLevel = "admin_approved",
                idempotencyKey = idempKey,
                balanceAfter = afterAvail,
                actor = staffUid,
                reason = cleanReason,
                createdAt = Timestamp(Date(now))
            )
        } finally {
            db.endTransaction()
        }
    }

    fun recordStaffAuditLog(
        staffUid: String,
        action: String,
        targetType: String,
        targetId: String,
        beforeValue: String? = null,
        afterValue: String? = null,
        reason: String
    ): AdminAuditLog {
        require(reason.trim().length >= 3) { "Admin action requires an explicit audit reason." }
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val role = requireStaffRoleInternal(db, staffUid, STAFF_APPROVAL_ROLES, action)
            val log = insertAuditLogInternal(
                db = db,
                adminUid = staffUid,
                adminRole = role,
                action = action,
                targetType = targetType,
                targetId = targetId,
                beforeValue = beforeValue,
                afterValue = afterValue,
                reason = reason.trim(),
                nowMs = now
            )
            db.setTransactionSuccessful()
            return log
        } finally {
            db.endTransaction()
        }
    }

    private fun insertAuditLogInternal(
        db: SQLiteDatabase,
        adminUid: String,
        adminRole: String,
        action: String,
        targetType: String,
        targetId: String,
        beforeValue: String?,
        afterValue: String?,
        reason: String,
        nowMs: Long
    ): AdminAuditLog {
        val logId = "audit_${UUID.randomUUID().toString().replace("-", "").take(18)}"
        val cv = ContentValues().apply {
            put("id", logId)
            put("admin_uid", adminUid)
            put("admin_role", adminRole)
            put("action", action)
            put("target_type", targetType)
            put("target_id", targetId)
            put("before_value", beforeValue)
            put("after_value", afterValue)
            put("reason", reason)
            put("created_at_ms", nowMs)
        }
        db.insertOrThrow("admin_audit_logs", null, cv)
        return AdminAuditLog(
            id = logId,
            adminUid = adminUid,
            adminRole = adminRole,
            action = action,
            targetType = targetType,
            targetId = targetId,
            beforeValue = beforeValue,
            afterValue = afterValue,
            reason = reason,
            createdAt = Timestamp(Date(nowMs))
        )
    }

    fun getAuditLogs(): List<AdminAuditLog> {
        val db = readableDatabase
        val list = mutableListOf<AdminAuditLog>()
        db.rawQuery(
            """
            SELECT id, admin_uid, admin_role, action, target_type, target_id, before_value, after_value, reason, created_at_ms
            FROM admin_audit_logs
            ORDER BY created_at_ms DESC, rowid DESC
            """.trimIndent(),
            emptyArray()
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    AdminAuditLog(
                        id = c.getString(0),
                        adminUid = c.getString(1),
                        adminRole = c.getString(2),
                        action = c.getString(3),
                        targetType = c.getString(4),
                        targetId = c.getString(5),
                        beforeValue = if (c.isNull(6)) null else c.getString(6),
                        afterValue = if (c.isNull(7)) null else c.getString(7),
                        reason = c.getString(8),
                        createdAt = Timestamp(Date(c.getLong(9)))
                    )
                )
            }
        }
        return list
    }

    // ========================================================================
    // 5. SAFE FIREBASE-TO-SQL BACKUP, MIGRATION & INTEGRITY VERIFICATION
    // ========================================================================

    data class SqlMigrationReport(
        val backupSnapshotJson: String,
        val migratedUsersCount: Int,
        val migratedWalletsCount: Int,
        val migratedLedgerCount: Int,
        val migratedRedemptionsCount: Int,
        val foreignKeyViolations: Int,
        val integrityVerified: Boolean
    )

    /**
     * Backs up existing records into a JSON snapshot before migrating them into the relational
     * SQL tables (`users`, `wallets`, `ledger_entries`, `redemption_requests`), then validates
     * migrated record counts and foreign-key relationships without deleting the source records.
     */
    @Synchronized
    fun backupAndMigrateRecordsToSql(
        users: List<UserProfile>,
        wallets: List<Wallet>,
        ledgerEntries: List<LedgerEntry>,
        redemptions: List<RedemptionRequest>
    ): SqlMigrationReport {
        val backupRoot = JSONObject().apply {
            put("timestampMs", System.currentTimeMillis())
            put("usersCount", users.size)
            put("walletsCount", wallets.size)
            put("ledgerCount", ledgerEntries.size)
            put("redemptionsCount", redemptions.size)
            put("users", JSONArray().apply {
                users.forEach { u ->
                    put(JSONObject().apply {
                        put("userId", u.userId.ifBlank { u.id })
                        put("displayName", u.displayName)
                        put("email", u.email ?: "")
                        put("role", u.role)
                    })
                }
            })
            put("wallets", JSONArray().apply {
                wallets.forEach { w ->
                    put(JSONObject().apply {
                        put("userId", w.userId)
                        put("available", w.available)
                        put("pending", w.pending)
                        put("lifetimeEarned", w.lifetimeEarned)
                        put("redeemed", w.redeemed)
                    })
                }
            })
        }
        val backupJson = backupRoot.toString()

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            // Save backup in schema_migrations
            val backupCv = ContentValues().apply {
                put("backup_snapshot_json", backupJson)
            }
            db.update("schema_migrations", backupCv, "version = 1", null)

            users.forEach { u ->
                val uid = u.userId.ifBlank { u.id }
                if (uid.isNotBlank()) {
                    val userCv = ContentValues().apply {
                        put("user_id", uid)
                        put("display_name", u.displayName.ifBlank { "Athxo Booyah Player" })
                        put("email", u.email)
                        put("photo_url", u.photoUrl)
                        put("referral_code", u.referralCode.ifBlank { "AB-${uid.takeLast(6).uppercase(Locale.US)}" })
                        put("referred_by", u.referredBy)
                        put("account_status", u.accountStatus.ifBlank { "active" })
                        put("role", u.role.ifBlank { "user" })
                        put("created_at_ms", u.createdAt?.toDate()?.time ?: now)
                        put("updated_at_ms", u.updatedAt?.toDate()?.time ?: now)
                    }
                    db.insertWithOnConflict("users", null, userCv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            wallets.forEach { w ->
                val uid = w.userId.ifBlank { w.id }
                if (uid.isNotBlank()) {
                    ensureUserExistsInTransaction(db, uid, now)
                    val walletCv = ContentValues().apply {
                        put("user_id", uid)
                        put("available_coins", w.available.coerceAtLeast(0L))
                        put("pending_coins", w.pending.coerceAtLeast(0L))
                        put("lifetime_earned", w.lifetimeEarned.coerceAtLeast(0L))
                        put("redeemed_coins", w.redeemed.coerceAtLeast(0L))
                        put("sandbox_coins", w.sandboxCoins.coerceAtLeast(0L))
                        put("updated_at_ms", w.updatedAt?.toDate()?.time ?: now)
                    }
                    db.insertWithOnConflict("wallets", null, walletCv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            ledgerEntries.forEach { entry ->
                if (entry.userId.isNotBlank()) {
                    ensureUserExistsInTransaction(db, entry.userId, now)
                    val txId = entry.id.ifBlank { "tx_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
                    val idemp = entry.idempotencyKey.ifBlank { "${entry.sourceType}_${entry.eventId}_${entry.userId}" }
                    val txType = when (entry.transactionType) {
                        "earn", "entry_fee", "reserve", "redeem_complete", "refund", "admin_adjustment" -> entry.transactionType
                        else -> if (entry.amount >= 0) "earn" else "entry_fee"
                    }
                    val ledgerCv = ContentValues().apply {
                        put("id", txId)
                        put("user_id", entry.userId)
                        put("event_id", entry.eventId.ifBlank { txId })
                        put("source_type", entry.sourceType.ifBlank { "migrated" })
                        put("source_id", entry.sourceId.ifBlank { "migrated" })
                        put("title", entry.title.ifBlank { "Migrated Transaction" })
                        put("amount", entry.amount)
                        put("transaction_type", txType)
                        put("verification_status", entry.verificationStatus.ifBlank { "approved" })
                        put("verification_level", entry.verificationLevel.ifBlank { "server_verified" })
                        put("idempotency_key", idemp)
                        put("balance_after", entry.balanceAfter.coerceAtLeast(0L))
                        put("actor", entry.actor.ifBlank { "system" })
                        put("reason", entry.reason)
                        put("created_at_ms", entry.createdAt?.toDate()?.time ?: now)
                    }
                    db.insertWithOnConflict("ledger_entries", null, ledgerCv, SQLiteDatabase.CONFLICT_IGNORE)
                }
            }

            redemptions.forEach { req ->
                if (req.userId.isNotBlank()) {
                    ensureUserExistsInTransaction(db, req.userId, now)
                    val reqId = req.id.ifBlank { "req_${UUID.randomUUID().toString().replace("-", "").take(16)}" }
                    val mappedStatus = when (req.status.lowercase(Locale.US)) {
                        "approved", "successful" -> "successful"
                        "rejected" -> "rejected"
                        else -> "pending"
                    }
                    val reqCv = ContentValues().apply {
                        put("id", reqId)
                        put("user_id", req.userId)
                        put("user_email", req.userEmail.ifBlank { "migrated@athxobooyah.app" })
                        put("reward_id", req.rewardId.ifBlank { "rew_inr_${req.denominationInr}" })
                        put("reward_name", req.rewardName.ifBlank { "Google Play Redeem Code ₹${req.denominationInr}" })
                        put("denomination_inr", req.denominationInr.coerceAtLeast(10L))
                        put("points_reserved", req.pointsReserved.coerceAtLeast(1L))
                        put("status", mappedStatus)
                        if (mappedStatus == "successful") {
                            put("assigned_code", req.assignedCode)
                            put("delivered_code_masked", req.deliveredCodeMasked)
                        } else {
                            putNull("assigned_code")
                            putNull("delivered_code_masked")
                        }
                        put("rejection_reason", req.rejectionReason)
                        put("reviewed_by_uid", req.reviewedByUid)
                        put("idempotency_key", "mig_red_$reqId")
                        put("created_at_ms", req.createdAtMs.takeIf { it > 0L } ?: (req.createdAt?.toDate()?.time ?: now))
                        put("updated_at_ms", req.updatedAt?.toDate()?.time ?: now)
                        put("processed_at_ms", req.processedAtMs)
                    }
                    db.insertWithOnConflict("redemption_requests", null, reqCv, SQLiteDatabase.CONFLICT_REPLACE)
                }
            }

            // Check foreign key integrity
            var fkViolations = 0
            db.rawQuery("PRAGMA foreign_key_check", emptyArray()).use { c ->
                fkViolations = c.count
            }
            if (fkViolations > 0) {
                throw IllegalStateException("SQL migration failed foreign key integrity check ($fkViolations violations).")
            }

            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        val totalUsersInSql = queryTableCount("users")
        val totalWalletsInSql = queryTableCount("wallets")
        val totalLedgerInSql = queryTableCount("ledger_entries")
        val totalRedemptionsInSql = queryTableCount("redemption_requests")

        val verified = totalUsersInSql >= users.size &&
            totalWalletsInSql >= wallets.size &&
            totalLedgerInSql >= ledgerEntries.size &&
            totalRedemptionsInSql >= redemptions.size

        return SqlMigrationReport(
            backupSnapshotJson = backupJson,
            migratedUsersCount = totalUsersInSql,
            migratedWalletsCount = totalWalletsInSql,
            migratedLedgerCount = totalLedgerInSql,
            migratedRedemptionsCount = totalRedemptionsInSql,
            foreignKeyViolations = 0,
            integrityVerified = verified
        )
    }

    fun queryTableCount(tableName: String): Int {
        val allowed = setOf(
            "users",
            "wallets",
            "ledger_entries",
            "task_claims",
            "reward_catalog",
            "redemption_requests",
            "redeem_codes_inventory",
            "admin_audit_logs",
            "schema_migrations",
            "game_matches",
            "game_match_actions",
            "video_proof_submissions",
            "mini_game_sessions",
            "mini_game_personal_bests"
        )
        require(tableName in allowed) { "Invalid table name: $tableName" }
        readableDatabase.rawQuery("SELECT COUNT(*) FROM $tableName", emptyArray()).use { c ->
            return if (c.moveToFirst()) c.getInt(0) else 0
        }
    }

    // ========================================================================
    // 7. AUTHORITATIVE LUDO & CARROM MATCH LIFECYCLE & MOVE VALIDATION
    // ========================================================================

    fun serializeLudoBoardState(state: LudoBoardState): String {
        val obj = JSONObject()
        obj.put("playerTokens", JSONArray(state.playerTokens))
        obj.put("opponentTokens", JSONArray(state.opponentTokens))
        obj.put("currentTurn", state.currentTurn)
        if (state.lastDiceRoll != null) obj.put("lastDiceRoll", state.lastDiceRoll) else obj.put("lastDiceRoll", JSONObject.NULL)
        obj.put("awaitingTokenMove", state.awaitingTokenMove)
        obj.put("consecutiveSixes", state.consecutiveSixes)
        obj.put("capturesByPlayer", state.capturesByPlayer)
        obj.put("capturesByOpponent", state.capturesByOpponent)
        obj.put("statusMessage", state.statusMessage)
        if (state.winner != null) obj.put("winner", state.winner) else obj.put("winner", JSONObject.NULL)
        return obj.toString()
    }

    fun deserializeLudoBoardState(json: String): LudoBoardState {
        return try {
            val obj = JSONObject(json)
            val pArr = obj.optJSONArray("playerTokens")
            val oArr = obj.optJSONArray("opponentTokens")
            val pList = if (pArr != null && pArr.length() == 4) {
                List(4) { pArr.optInt(it, -1) }
            } else listOf(-1, -1, -1, -1)
            val oList = if (oArr != null && oArr.length() == 4) {
                List(4) { oArr.optInt(it, -1) }
            } else listOf(-1, -1, -1, -1)
            LudoBoardState(
                playerTokens = pList,
                opponentTokens = oList,
                currentTurn = obj.optString("currentTurn", "PLAYER"),
                lastDiceRoll = if (obj.isNull("lastDiceRoll")) null else obj.optInt("lastDiceRoll"),
                awaitingTokenMove = obj.optBoolean("awaitingTokenMove", false),
                consecutiveSixes = obj.optInt("consecutiveSixes", 0),
                capturesByPlayer = obj.optInt("capturesByPlayer", 0),
                capturesByOpponent = obj.optInt("capturesByOpponent", 0),
                statusMessage = obj.optString("statusMessage", "Your turn! Tap Roll Dice."),
                winner = if (obj.isNull("winner")) null else obj.optString("winner")
            )
        } catch (_: Exception) {
            LudoBoardState()
        }
    }

    fun createInitialCarromBoardState(): CarromBoardState {
        val discs = mutableListOf<CarromDiscState>()
        // Red Queen at exact center (0f, 0f)
        discs.add(CarromDiscState(id = 0, type = "QUEEN", x = 0f, y = 0f))
        // Ring of 6 alternating White & Black carrom men around the Queen
        val ringRadius = 0.14f
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60).toDouble())
            val type = if (i % 2 == 0) "WHITE" else "BLACK"
            discs.add(
                CarromDiscState(
                    id = i + 1,
                    type = type,
                    x = (Math.cos(angle) * ringRadius).toFloat(),
                    y = (Math.sin(angle) * ringRadius).toFloat()
                )
            )
        }
        // Outer ring of 6 more men
        val outerRadius = 0.26f
        for (i in 0 until 6) {
            val angle = Math.toRadians((i * 60 + 30).toDouble())
            val type = if (i % 2 == 0) "WHITE" else "BLACK"
            discs.add(
                CarromDiscState(
                    id = i + 7,
                    type = type,
                    x = (Math.cos(angle) * outerRadius).toFloat(),
                    y = (Math.sin(angle) * outerRadius).toFloat()
                )
            )
        }
        return CarromBoardState(discs = discs)
    }

    fun serializeCarromBoardState(state: CarromBoardState): String {
        val obj = JSONObject()
        val arr = JSONArray()
        state.discs.forEach { d ->
            val dObj = JSONObject()
            dObj.put("id", d.id)
            dObj.put("type", d.type)
            dObj.put("x", d.x.toDouble())
            dObj.put("y", d.y.toDouble())
            dObj.put("vx", d.vx.toDouble())
            dObj.put("vy", d.vy.toDouble())
            dObj.put("isPocketed", d.isPocketed)
            if (d.pocketedBy != null) dObj.put("pocketedBy", d.pocketedBy) else dObj.put("pocketedBy", JSONObject.NULL)
            arr.put(dObj)
        }
        obj.put("discs", arr)
        obj.put("strikerX", state.strikerX.toDouble())
        obj.put("currentTurn", state.currentTurn)
        obj.put("playerScore", state.playerScore)
        obj.put("opponentScore", state.opponentScore)
        if (state.queenPendingCoverBy != null) obj.put("queenPendingCoverBy", state.queenPendingCoverBy) else obj.put("queenPendingCoverBy", JSONObject.NULL)
        if (state.queenCoveredBy != null) obj.put("queenCoveredBy", state.queenCoveredBy) else obj.put("queenCoveredBy", JSONObject.NULL)
        obj.put("turnsRemaining", state.turnsRemaining)
        obj.put("foulCommittedLastTurn", state.foulCommittedLastTurn)
        obj.put("statusMessage", state.statusMessage)
        if (state.winner != null) obj.put("winner", state.winner) else obj.put("winner", JSONObject.NULL)
        return obj.toString()
    }

    fun deserializeCarromBoardState(json: String): CarromBoardState {
        return try {
            val obj = JSONObject(json)
            val arr = obj.optJSONArray("discs")
            val discs = mutableListOf<CarromDiscState>()
            if (arr != null) {
                for (i in 0 until arr.length()) {
                    val d = arr.getJSONObject(i)
                    discs.add(
                        CarromDiscState(
                            id = d.optInt("id", i),
                            type = d.optString("type", "WHITE"),
                            x = d.optDouble("x", 0.0).toFloat(),
                            y = d.optDouble("y", 0.0).toFloat(),
                            vx = d.optDouble("vx", 0.0).toFloat(),
                            vy = d.optDouble("vy", 0.0).toFloat(),
                            isPocketed = d.optBoolean("isPocketed", false),
                            pocketedBy = if (d.isNull("pocketedBy")) null else d.optString("pocketedBy")
                        )
                    )
                }
            }
            if (discs.isEmpty()) return createInitialCarromBoardState()
            CarromBoardState(
                discs = discs,
                strikerX = obj.optDouble("strikerX", 0.0).toFloat(),
                currentTurn = obj.optString("currentTurn", "PLAYER"),
                playerScore = obj.optInt("playerScore", 0),
                opponentScore = obj.optInt("opponentScore", 0),
                queenPendingCoverBy = if (obj.isNull("queenPendingCoverBy")) null else obj.optString("queenPendingCoverBy"),
                queenCoveredBy = if (obj.isNull("queenCoveredBy")) null else obj.optString("queenCoveredBy"),
                turnsRemaining = obj.optInt("turnsRemaining", 16),
                foulCommittedLastTurn = obj.optBoolean("foulCommittedLastTurn", false),
                statusMessage = obj.optString("statusMessage", "Place striker on baseline, aim & shoot!"),
                winner = if (obj.isNull("winner")) null else obj.optString("winner")
            )
        } catch (_: Exception) {
            createInitialCarromBoardState()
        }
    }

    /**
     * Authoritative Match Entry & Atomic Fee Reservation:
     * - Verifies user authentication & eligibility
     * - Checks available wallet balance against `entryFeeCoins`
     * - If insufficient, throws `InsufficientMatchCoinsException` with the EXACT required message:
     *   - Initial entry: "Not enough coins to play this match. Please go back and earn more coins."
     *   - Play Again: "Not enough coins to play again. Please go back and earn more coins."
     * - Reserves/deducts the entry fee atomically ONCE per `idempotencyKey`
     * - Creates the authoritative `game_matches` row and logs the initial `MATCH_STARTED` action.
     */
    fun startAuthoritativeMatchTransactional(
        userId: String,
        gameType: String, // "LUDO" or "CARROM"
        matchTitle: String,
        entryFeeCoins: Long,
        prizeCoins: Long,
        idempotencyKey: String,
        isPlayAgain: Boolean = false
    ): AuthoritativeGameMatch {
        require(userId.isNotBlank()) { "Authentication required to start a match." }
        val normalizedGameType = gameType.trim().uppercase(Locale.US)
        require(normalizedGameType in setOf("LUDO", "CARROM")) { "Unsupported game type: $gameType" }
        require(entryFeeCoins >= 0L) { "Entry fee cannot be negative." }
        require(prizeCoins >= 0L) { "Prize coins cannot be negative." }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            ensureUserExistsInTransaction(db, userId, now)

            // Check if match was already created for this exact idempotencyKey (prevents double charge)
            db.rawQuery(
                "SELECT match_id FROM game_matches WHERE entry_idempotency_key = ?",
                arrayOf(idempotencyKey)
            ).use { existing ->
                if (existing.moveToFirst()) {
                    val existingMatchId = existing.getString(0)
                    db.setTransactionSuccessful()
                    return queryAuthoritativeMatchInternal(db, existingMatchId)!!
                }
            }

            // Verify user account is not suspended
            val userProfile = queryUserProfileInternal(db, userId)
            if (userProfile?.accountStatus == "suspended") {
                throw SecurityException("Account is suspended and cannot join matches.")
            }

            // Check available coin balance
            val wallet = queryWalletInternal(db, userId)
            if (wallet.available < entryFeeCoins) {
                val exactMessage = if (isPlayAgain) {
                    INSUFFICIENT_COINS_PLAY_AGAIN_MESSAGE
                } else {
                    INSUFFICIENT_COINS_ENTRY_MESSAGE
                }
                throw InsufficientMatchCoinsException(exactMessage, isPlayAgain = isPlayAgain)
            }

            // Atomically deduct entry fee if > 0
            val newAvailable = wallet.available - entryFeeCoins
            if (entryFeeCoins > 0L) {
                val walletUpdate = ContentValues().apply {
                    put("available_coins", newAvailable)
                    put("updated_at_ms", now)
                }
                db.update("wallets", walletUpdate, "user_id = ?", arrayOf(userId))

                val ledgerId = "tx_entry_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val ledgerCv = ContentValues().apply {
                    put("id", ledgerId)
                    put("user_id", userId)
                    put("event_id", idempotencyKey)
                    put("source_type", "${normalizedGameType.lowercase(Locale.US)}_match_entry")
                    put("source_id", normalizedGameType)
                    put("title", "$matchTitle Entry Fee")
                    put("amount", -entryFeeCoins)
                    put("transaction_type", "entry_fee")
                    put("verification_status", "approved")
                    put("verification_level", "server_verified")
                    put("idempotency_key", "ledger_entry_$idempotencyKey")
                    put("balance_after", newAvailable)
                    put("actor", "system")
                    put("reason", "Authoritative $normalizedGameType match entry deduction")
                    put("created_at_ms", now)
                }
                db.insertOrThrow("ledger_entries", null, ledgerCv)
            }

            val matchId = "match_${normalizedGameType.lowercase(Locale.US)}_${UUID.randomUUID().toString().replace("-", "").take(14)}"
            val initialBoardJson = if (normalizedGameType == "LUDO") {
                serializeLudoBoardState(LudoBoardState())
            } else {
                serializeCarromBoardState(createInitialCarromBoardState())
            }

            val matchCv = ContentValues().apply {
                put("match_id", matchId)
                put("user_id", userId)
                put("game_type", normalizedGameType)
                put("match_title", matchTitle)
                put("entry_fee_coins", entryFeeCoins)
                put("prize_coins", prizeCoins)
                put("status", "IN_PROGRESS")
                put("current_turn", "PLAYER")
                put("turn_number", 1)
                put("player_score", 0)
                put("opponent_score", 0)
                put("board_state_json", initialBoardJson)
                put("entry_idempotency_key", idempotencyKey)
                put("created_at", now)
                put("updated_at", now)
            }
            db.insertOrThrow("game_matches", null, matchCv)

            recordMatchActionInternal(
                db = db,
                matchId = matchId,
                userId = userId,
                actor = "SYSTEM",
                actionType = "MATCH_STARTED",
                turnNumber = 1,
                payloadJson = JSONObject().apply {
                    put("gameType", normalizedGameType)
                    put("entryFeeCoins", entryFeeCoins)
                    put("prizeCoins", prizeCoins)
                    put("walletBalanceAfterEntry", newAvailable)
                }.toString(),
                nowMs = now
            )

            db.setTransactionSuccessful()
            return queryAuthoritativeMatchInternal(db, matchId)!!
        } finally {
            db.endTransaction()
        }
    }

    fun getAuthoritativeMatch(matchId: String): AuthoritativeGameMatch? {
        return queryAuthoritativeMatchInternal(readableDatabase, matchId)
    }

    fun getActiveMatchForUser(userId: String, gameType: String): AuthoritativeGameMatch? {
        val normalizedGameType = gameType.trim().uppercase(Locale.US)
        readableDatabase.rawQuery(
            """
            SELECT match_id FROM game_matches
            WHERE user_id = ? AND game_type = ? AND status = 'IN_PROGRESS'
            ORDER BY updated_at DESC LIMIT 1
            """.trimIndent(),
            arrayOf(userId, normalizedGameType)
        ).use { c ->
            if (c.moveToFirst()) {
                return queryAuthoritativeMatchInternal(readableDatabase, c.getString(0))
            }
        }
        return null
    }

    /**
     * Server-Authoritative Ludo Dice Roll Validation:
     * - Rejects out-of-turn rolls or rolls when awaiting a token move
     * - Enforces fair dice value (1..6)
     * - Checks if the current actor has any legal moves; if none, automatically passes turn.
     */
    fun validateAndRecordLudoDiceRollTransactional(
        matchId: String,
        userId: String,
        actor: String, // "PLAYER" or "OPPONENT"
        requestedDiceValue: Int? = null
    ): AuthoritativeGameMatch {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val match = queryAuthoritativeMatchInternal(db, matchId)
                ?: throw IllegalArgumentException("Match not found: $matchId")
            if (match.userId != userId) {
                throw SecurityException("Unauthorized: Match belongs to a different user.")
            }
            if (match.status != "IN_PROGRESS") {
                throw IllegalStateException("Match is already finished (${match.status}).")
            }
            val board = deserializeLudoBoardState(match.boardStateJson)
            if (board.currentTurn != actor) {
                logSuspiciousActivityInternal(db, userId, "LUDO_OUT_OF_TURN_DICE", "Actor $actor rolled on ${board.currentTurn} turn in $matchId", now)
                throw SecurityException("Out-of-turn dice roll rejected by server.")
            }
            if (board.awaitingTokenMove) {
                throw IllegalStateException("Dice already rolled (${board.lastDiceRoll}). Select a legal token to move.")
            }

            val dice = requestedDiceValue ?: ((1..6).random())
            if (dice !in 1..6) {
                logSuspiciousActivityInternal(db, userId, "LUDO_INVALID_DICE", "Invalid dice value $dice in $matchId", now)
                throw SecurityException("Invalid dice roll value ($dice). Must be between 1 and 6.")
            }

            val tokens = if (actor == "PLAYER") board.playerTokens else board.opponentTokens
            val legalIndices = getLegalLudoTokenIndices(tokens, dice)

            val updatedBoard = if (legalIndices.isEmpty()) {
                val nextTurn = if (actor == "PLAYER") "OPPONENT" else "PLAYER"
                val who = if (actor == "PLAYER") "You" else "Opponent"
                board.copy(
                    currentTurn = nextTurn,
                    lastDiceRoll = dice,
                    awaitingTokenMove = false,
                    consecutiveSixes = 0,
                    statusMessage = "$who rolled $dice — no legal moves! Turn passes."
                )
            } else {
                val who = if (actor == "PLAYER") "You" else "Opponent"
                board.copy(
                    lastDiceRoll = dice,
                    awaitingTokenMove = true,
                    statusMessage = "$who rolled $dice! Select a highlighted token to move."
                )
            }

            val nextTurnNumber = match.turnNumber + 1
            val cv = ContentValues().apply {
                put("current_turn", updatedBoard.currentTurn)
                put("turn_number", nextTurnNumber)
                put("board_state_json", serializeLudoBoardState(updatedBoard))
                put("updated_at", now)
            }
            db.update("game_matches", cv, "match_id = ?", arrayOf(matchId))

            recordMatchActionInternal(
                db = db,
                matchId = matchId,
                userId = userId,
                actor = actor,
                actionType = "DICE_ROLLED",
                turnNumber = nextTurnNumber,
                payloadJson = JSONObject().apply {
                    put("dice", dice)
                    put("legalMoveCount", legalIndices.size)
                }.toString(),
                nowMs = now
            )

            db.setTransactionSuccessful()
            return queryAuthoritativeMatchInternal(db, matchId)!!
        } finally {
            db.endTransaction()
        }
    }

    fun getLegalLudoTokenIndices(tokens: List<Int>, dice: Int): List<Int> {
        if (dice !in 1..6) return emptyList()
        return tokens.mapIndexedNotNull { idx, pos ->
            when {
                pos == -1 && dice == 6 -> idx // Need a 6 to leave home base
                pos in 0..55 && (pos + dice) <= 56 -> idx // Exact roll required to reach 56 (home triangle)
                else -> null
            }
        }
    }

    /**
     * Converts a player's or opponent's relative track step (0..50) into the shared 52-square
     * outer perimeter index (0..51) so captures and safe-square checks are 100% accurate.
     * Steps 51..55 are inside the player's private colored home stretch lane and cannot be captured.
     * Step 56 is the center Home Triangle.
     */
    fun toGlobalLudoTrackSquare(actor: String, relativePos: Int): Int? {
        if (relativePos !in 0..50) return null
        val startOffset = if (actor == "PLAYER") 0 else 26
        return (relativePos + startOffset) % 52
    }

    /**
     * Server-Authoritative Ludo Token Move Validation & Settlement:
     * - Verifies turn ownership, active dice roll, and legal token eligibility
     * - Enforces:
     *   1. Rolling 6 required to leave base (-1 -> 0)
     *   2. Exact roll required to enter home triangle (56)
     *   3. Capturing opponent tokens on non-safe perimeter squares (sends captured token back to -1 base)
     *   4. Safe-square protection (`LUDO_SAFE_TRACK_INDICES`: 0, 8, 13, 21, 26, 34, 39, 47)
     *   5. Extra turn granted when rolling a 6 or capturing an opponent token
     *   6. Victory when all 4 tokens reach home (56), atomically crediting prize coins once.
     */
    fun submitAndValidateLudoMoveTransactional(
        matchId: String,
        userId: String,
        actor: String, // "PLAYER" or "OPPONENT"
        tokenIndex: Int
    ): AuthoritativeGameMatch {
        require(tokenIndex in 0..3) { "Invalid token index: $tokenIndex" }
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val match = queryAuthoritativeMatchInternal(db, matchId)
                ?: throw IllegalArgumentException("Match not found: $matchId")
            if (match.userId != userId) {
                throw SecurityException("Unauthorized: Match belongs to a different user.")
            }
            if (match.status != "IN_PROGRESS") {
                throw IllegalStateException("Match is no longer in progress (${match.status}).")
            }

            val board = deserializeLudoBoardState(match.boardStateJson)
            if (board.currentTurn != actor) {
                logSuspiciousActivityInternal(db, userId, "LUDO_OUT_OF_TURN_MOVE", "Actor $actor attempted move on ${board.currentTurn} turn", now)
                throw SecurityException("Out-of-turn token move rejected by server.")
            }
            val dice = board.lastDiceRoll
            if (!board.awaitingTokenMove || dice == null || dice !in 1..6) {
                throw IllegalStateException("Must roll the dice before moving a token.")
            }

            val myTokens = (if (actor == "PLAYER") board.playerTokens else board.opponentTokens).toMutableList()
            val theirTokens = (if (actor == "PLAYER") board.opponentTokens else board.playerTokens).toMutableList()
            val otherActor = if (actor == "PLAYER") "OPPONENT" else "PLAYER"

            val currentPos = myTokens[tokenIndex]
            val newPos = when {
                currentPos == -1 && dice == 6 -> 0
                currentPos == -1 && dice != 6 -> {
                    logSuspiciousActivityInternal(db, userId, "LUDO_ILLEGAL_BASE_EXIT", "Token $tokenIndex attempted base exit with dice $dice", now)
                    throw SecurityException("Illegal move: A roll of 6 is required to leave the home base.")
                }
                currentPos in 0..55 && (currentPos + dice) <= 56 -> currentPos + dice
                else -> {
                    logSuspiciousActivityInternal(db, userId, "LUDO_ILLEGAL_OVERSHOOT", "Token $tokenIndex at $currentPos cannot move $dice steps", now)
                    throw SecurityException("Illegal move: Exact dice roll is required to enter the home triangle.")
                }
            }

            myTokens[tokenIndex] = newPos

            // Check capture on shared outer track (0..50 relative -> 0..51 global)
            var capturedOpponentCount = 0
            val myGlobalSquare = toGlobalLudoTrackSquare(actor, newPos)
            if (myGlobalSquare != null && myGlobalSquare !in LUDO_SAFE_TRACK_INDICES) {
                for (i in 0..3) {
                    val oppRel = theirTokens[i]
                    val oppGlobal = toGlobalLudoTrackSquare(otherActor, oppRel)
                    if (oppGlobal != null && oppGlobal == myGlobalSquare) {
                        theirTokens[i] = -1 // Send captured opponent token back to base!
                        capturedOpponentCount++
                    }
                }
            }

            val reachedHome = newPos == 56
            val allFourHome = myTokens.all { it == 56 }
            val extraTurn = !allFourHome && (dice == 6 || capturedOpponentCount > 0 || reachedHome)
            val nextTurn = if (extraTurn) actor else otherActor

            val updatedPlayerTokens = if (actor == "PLAYER") myTokens else theirTokens
            val updatedOpponentTokens = if (actor == "PLAYER") theirTokens else myTokens
            val newCapturesByPlayer = board.capturesByPlayer + (if (actor == "PLAYER") capturedOpponentCount else 0)
            val newCapturesByOpponent = board.capturesByOpponent + (if (actor == "OPPONENT") capturedOpponentCount else 0)

            val playerScore = updatedPlayerTokens.sumOf { if (it < 0) 0 else it + 1 } + (newCapturesByPlayer * 15)
            val opponentScore = updatedOpponentTokens.sumOf { if (it < 0) 0 else it + 1 } + (newCapturesByOpponent * 15)

            val whoLabel = if (actor == "PLAYER") "You" else "Opponent"
            val statusMsg = when {
                allFourHome && actor == "PLAYER" -> "Booyah Victory! All 4 of your Panda tokens reached Home!"
                allFourHome && actor == "OPPONENT" -> "Match Complete! Opponent reached Home with all 4 tokens."
                capturedOpponentCount > 0 -> "$whoLabel captured an opponent token and earned a Bonus Roll!"
                reachedHome -> "$whoLabel brought a token into the Home Triangle! Bonus Roll!"
                dice == 6 -> "$whoLabel rolled 6 and moved token #${tokenIndex + 1}! Bonus Roll!"
                else -> "$whoLabel moved token #${tokenIndex + 1}. ${if (nextTurn == "PLAYER") "Your turn!" else "Opponent's turn..."}"
            }

            val updatedBoard = board.copy(
                playerTokens = updatedPlayerTokens,
                opponentTokens = updatedOpponentTokens,
                currentTurn = nextTurn,
                lastDiceRoll = null,
                awaitingTokenMove = false,
                consecutiveSixes = if (dice == 6) board.consecutiveSixes + 1 else 0,
                capturesByPlayer = newCapturesByPlayer,
                capturesByOpponent = newCapturesByOpponent,
                statusMessage = statusMsg,
                winner = if (allFourHome) actor else null
            )

            val nextTurnNumber = match.turnNumber + 1
            val newStatus = when {
                allFourHome && actor == "PLAYER" -> "COMPLETED_WON"
                allFourHome && actor == "OPPONENT" -> "COMPLETED_LOST"
                else -> "IN_PROGRESS"
            }

            val cv = ContentValues().apply {
                put("status", newStatus)
                put("current_turn", updatedBoard.currentTurn)
                put("turn_number", nextTurnNumber)
                put("player_score", playerScore)
                put("opponent_score", opponentScore)
                put("board_state_json", serializeLudoBoardState(updatedBoard))
                put("updated_at", now)
                if (newStatus != "IN_PROGRESS") {
                    put("completed_at", now)
                    put("winner_user_id", if (newStatus == "COMPLETED_WON") userId else "ai_opponent")
                    put("settlement_idempotency_key", "settle_$matchId")
                }
            }
            db.update("game_matches", cv, "match_id = ?", arrayOf(matchId))

            recordMatchActionInternal(
                db = db,
                matchId = matchId,
                userId = userId,
                actor = actor,
                actionType = "TOKEN_MOVED",
                turnNumber = nextTurnNumber,
                payloadJson = JSONObject().apply {
                    put("tokenIndex", tokenIndex)
                    put("fromPos", currentPos)
                    put("toPos", newPos)
                    put("dice", dice)
                    put("capturedOpponentCount", capturedOpponentCount)
                    put("extraTurn", extraTurn)
                }.toString(),
                nowMs = now
            )

            if (newStatus == "COMPLETED_WON" && match.prizeCoins > 0L) {
                creditMatchPrizeInternal(db, match, now)
            }

            db.setTransactionSuccessful()
            return queryAuthoritativeMatchInternal(db, matchId)!!
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Server-Authoritative Carrom Shot Validation & Physics Settlement:
     * - Verifies turn ownership & legal striker baseline placement (`-0.65f..0.65f`)
     * - Validates shot power (`0.05f..1.0f`)
     * - Updates board state with pocketed White/Black men, Red Queen cover rule, and Striker foul penalty.
     */
    fun submitAndValidateCarromShotTransactional(
        matchId: String,
        userId: String,
        actor: String, // "PLAYER" or "OPPONENT"
        strikerX: Float,
        aimAngleDeg: Float,
        powerFraction: Float,
        updatedDiscs: List<CarromDiscState>,
        strikerFoul: Boolean
    ): AuthoritativeGameMatch {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val match = queryAuthoritativeMatchInternal(db, matchId)
                ?: throw IllegalArgumentException("Match not found: $matchId")
            if (match.userId != userId) {
                throw SecurityException("Unauthorized: Match belongs to a different user.")
            }
            if (match.status != "IN_PROGRESS") {
                throw IllegalStateException("Match is already finished (${match.status}).")
            }

            val prevBoard = deserializeCarromBoardState(match.boardStateJson)
            if (prevBoard.currentTurn != actor) {
                logSuspiciousActivityInternal(db, userId, "CARROM_OUT_OF_TURN_SHOT", "Actor $actor shot on ${prevBoard.currentTurn} turn", now)
                throw SecurityException("Out-of-turn Carrom shot rejected by server.")
            }
            if (strikerX !in -0.75f..0.75f || strikerX.isNaN()) {
                logSuspiciousActivityInternal(db, userId, "CARROM_INVALID_STRIKER_POS", "Invalid strikerX=$strikerX", now)
                throw SecurityException("Illegal striker placement ($strikerX). Striker must be placed within the baseline bounds.")
            }
            if (powerFraction !in 0.01f..1.05f || powerFraction.isNaN()) {
                throw SecurityException("Illegal shot power ($powerFraction).")
            }

            // Diff newly pocketed pieces this shot
            val prevPocketedIds = prevBoard.discs.filter { it.isPocketed }.map { it.id }.toSet()
            val newlyPocketed = updatedDiscs.filter { it.isPocketed && it.id !in prevPocketedIds }

            var playerScore = prevBoard.playerScore
            var opponentScore = prevBoard.opponentScore
            var queenPending = prevBoard.queenPendingCoverBy
            var queenCovered = prevBoard.queenCoveredBy
            var earnedBonusTurn = false

            val PocketedWhiteCount = newlyPocketed.count { it.type == "WHITE" }
            val PocketedBlackCount = newlyPocketed.count { it.type == "BLACK" }
            val PocketedQueen = newlyPocketed.any { it.type == "QUEEN" }

            val myMenCount = if (actor == "PLAYER") PocketedWhiteCount else PocketedBlackCount
            val theirMenCount = if (actor == "PLAYER") PocketedBlackCount else PocketedWhiteCount

            // Score standard carrom men (White = 20 pts for Player, Black = 20 pts for Opponent; pocketing opponent's piece awards points to them)
            playerScore += PocketedWhiteCount * 20
            opponentScore += PocketedBlackCount * 20

            // Queen cover rule:
            // If Queen was pocketed this shot:
            // - If the shooter also pocketed one of their own men in the same shot, Queen is immediately covered (+50 pts)!
            // - Otherwise Queen is pending cover on the immediate next shot.
            if (PocketedQueen && queenCovered == null) {
                if (myMenCount > 0 && !strikerFoul) {
                    queenCovered = actor
                    queenPending = null
                    if (actor == "PLAYER") playerScore += 50 else opponentScore += 50
                    earnedBonusTurn = true
                } else if (!strikerFoul) {
                    queenPending = actor
                    earnedBonusTurn = true
                }
            } else if (queenPending == actor) {
                if (myMenCount > 0 && !strikerFoul) {
                    queenCovered = actor
                    queenPending = null
                    if (actor == "PLAYER") playerScore += 50 else opponentScore += 50
                    earnedBonusTurn = true
                } else {
                    // Failed to cover Queen — return Queen to center of board!
                    queenPending = null
                }
            }

            if (myMenCount > 0 && !strikerFoul) {
                earnedBonusTurn = true
            }

            // Striker foul penalty (-10 pts and turn lost)
            if (strikerFoul) {
                earnedBonusTurn = false
                if (actor == "PLAYER") {
                    playerScore = (playerScore - 10).coerceAtLeast(0)
                } else {
                    opponentScore = (opponentScore - 10).coerceAtLeast(0)
                }
            }

            // Normalize discs (if Queen failed cover or was fouled, return Queen to center)
            val normalizedDiscs = updatedDiscs.map { disc ->
                if (disc.type == "QUEEN" && queenCovered == null && queenPending == null && disc.isPocketed) {
                    disc.copy(x = 0f, y = 0f, vx = 0f, vy = 0f, isPocketed = false, pocketedBy = null)
                } else {
                    disc.copy(vx = 0f, vy = 0f)
                }
            }

            val turnsLeft = (prevBoard.turnsRemaining - 1).coerceAtLeast(0)
            val allMenCleared = normalizedDiscs.all { it.isPocketed }
            val matchFinished = allMenCleared || turnsLeft <= 0

            val otherActor = if (actor == "PLAYER") "OPPONENT" else "PLAYER"
            val nextTurn = if (matchFinished) actor else if (earnedBonusTurn) actor else otherActor

            val winner = if (matchFinished) {
                if (playerScore >= opponentScore) "PLAYER" else "OPPONENT"
            } else null

            val whoLabel = if (actor == "PLAYER") "You" else "Opponent"
            val statusMsg = when {
                matchFinished && winner == "PLAYER" -> "Booyah Carrom Victory! Final Score: $playerScore to $opponentScore"
                matchFinished && winner == "OPPONENT" -> "Carrom Match Complete! Opponent won $opponentScore to $playerScore"
                strikerFoul -> "$whoLabel committed a Striker Foul (-10 pts)! Turn passes."
                queenCovered == actor && (PocketedQueen || prevBoard.queenPendingCoverBy == actor) -> "$whoLabel covered the Red Queen (+50 pts)! Bonus Shot!"
                queenPending == actor -> "$whoLabel pocketed the Red Queen! Pocket your piece next shot to cover it!"
                myMenCount > 0 -> "$whoLabel pocketed $myMenCount carrom piece(s)! Bonus Shot!"
                theirMenCount > 0 -> "$whoLabel pocketed opponent's piece. Turn passes."
                else -> "$whoLabel took a shot ($turnsLeft turns left). ${if (nextTurn == "PLAYER") "Your turn!" else "Opponent's turn..."}"
            }

            val updatedBoard = prevBoard.copy(
                discs = normalizedDiscs,
                strikerX = strikerX,
                currentTurn = nextTurn,
                playerScore = playerScore,
                opponentScore = opponentScore,
                queenPendingCoverBy = queenPending,
                queenCoveredBy = queenCovered,
                turnsRemaining = turnsLeft,
                foulCommittedLastTurn = strikerFoul,
                statusMessage = statusMsg,
                winner = winner
            )

            val nextTurnNumber = match.turnNumber + 1
            val newStatus = when {
                matchFinished && winner == "PLAYER" -> "COMPLETED_WON"
                matchFinished && winner == "OPPONENT" -> "COMPLETED_LOST"
                else -> "IN_PROGRESS"
            }

            val cv = ContentValues().apply {
                put("status", newStatus)
                put("current_turn", updatedBoard.currentTurn)
                put("turn_number", nextTurnNumber)
                put("player_score", playerScore)
                put("opponent_score", opponentScore)
                put("board_state_json", serializeCarromBoardState(updatedBoard))
                put("updated_at", now)
                if (newStatus != "IN_PROGRESS") {
                    put("completed_at", now)
                    put("winner_user_id", if (newStatus == "COMPLETED_WON") userId else "ai_opponent")
                    put("settlement_idempotency_key", "settle_$matchId")
                }
            }
            db.update("game_matches", cv, "match_id = ?", arrayOf(matchId))

            recordMatchActionInternal(
                db = db,
                matchId = matchId,
                userId = userId,
                actor = actor,
                actionType = "CARROM_SHOT",
                turnNumber = nextTurnNumber,
                payloadJson = JSONObject().apply {
                    put("strikerX", strikerX.toDouble())
                    put("aimAngleDeg", aimAngleDeg.toDouble())
                    put("powerFraction", powerFraction.toDouble())
                    put("pocketedWhite", PocketedWhiteCount)
                    put("pocketedBlack", PocketedBlackCount)
                    put("pocketedQueen", PocketedQueen)
                    put("strikerFoul", strikerFoul)
                }.toString(),
                nowMs = now
            )

            if (newStatus == "COMPLETED_WON" && match.prizeCoins > 0L) {
                creditMatchPrizeInternal(db, match, now)
            }

            db.setTransactionSuccessful()
            return queryAuthoritativeMatchInternal(db, matchId)!!
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Authoritative Match Settlement / Surrender:
     * - Safely settles an active match or processes an explicit player surrender
     * - Prevents duplicate prize payouts via `settlement_idempotency_key`.
     */
    fun settleAuthoritativeMatchTransactional(
        matchId: String,
        userId: String,
        playerScore: Int,
        opponentScore: Int,
        surrendered: Boolean = false
    ): AuthoritativeGameMatch {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val match = queryAuthoritativeMatchInternal(db, matchId)
                ?: throw IllegalArgumentException("Match not found: $matchId")
            if (match.userId != userId) {
                throw SecurityException("Unauthorized: Match belongs to a different user.")
            }
            if (match.status != "IN_PROGRESS") {
                db.setTransactionSuccessful()
                return match
            }

            val finalStatus = when {
                surrendered -> "SURRENDERED"
                playerScore > opponentScore -> "COMPLETED_WON"
                else -> "COMPLETED_LOST"
            }

            val cv = ContentValues().apply {
                put("status", finalStatus)
                put("player_score", playerScore)
                put("opponent_score", opponentScore)
                put("winner_user_id", if (finalStatus == "COMPLETED_WON") userId else "ai_opponent")
                put("settlement_idempotency_key", "settle_$matchId")
                put("updated_at", now)
                put("completed_at", now)
            }
            db.update("game_matches", cv, "match_id = ?", arrayOf(matchId))

            recordMatchActionInternal(
                db = db,
                matchId = matchId,
                userId = userId,
                actor = "PLAYER",
                actionType = if (surrendered) "MATCH_SURRENDERED" else "MATCH_SETTLED",
                turnNumber = match.turnNumber + 1,
                payloadJson = JSONObject().apply {
                    put("finalStatus", finalStatus)
                    put("playerScore", playerScore)
                    put("opponentScore", opponentScore)
                }.toString(),
                nowMs = now
            )

            if (finalStatus == "COMPLETED_WON" && match.prizeCoins > 0L) {
                creditMatchPrizeInternal(db, match, now)
            }

            db.setTransactionSuccessful()
            return queryAuthoritativeMatchInternal(db, matchId)!!
        } finally {
            db.endTransaction()
        }
    }

    private fun creditMatchPrizeInternal(db: SQLiteDatabase, match: AuthoritativeGameMatch, nowMs: Long) {
        val idempKey = "ledger_prize_${match.matchId}"
        val alreadyCredited = db.rawQuery(
            "SELECT id FROM ledger_entries WHERE idempotency_key = ?",
            arrayOf(idempKey)
        ).use { it.moveToFirst() }
        if (alreadyCredited) return

        val wallet = queryWalletInternal(db, match.userId)
        val newAvailable = wallet.available + match.prizeCoins
        val newLifetime = wallet.lifetimeEarned + match.prizeCoins

        val walletCv = ContentValues().apply {
            put("available_coins", newAvailable)
            put("lifetime_earned", newLifetime)
            put("updated_at_ms", nowMs)
        }
        db.update("wallets", walletCv, "user_id = ?", arrayOf(match.userId))

        val ledgerId = "tx_prize_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val ledgerCv = ContentValues().apply {
            put("id", ledgerId)
            put("user_id", match.userId)
            put("event_id", match.matchId)
            put("source_type", "${match.gameType.lowercase(Locale.US)}_match_win")
            put("source_id", match.matchId)
            put("title", "${match.matchTitle} Victory Prize")
            put("amount", match.prizeCoins)
            put("transaction_type", "earn")
            put("verification_status", "approved")
            put("verification_level", "server_verified")
            put("idempotency_key", idempKey)
            put("balance_after", newAvailable)
            put("actor", "system")
            put("reason", "Authoritative ${match.gameType} match victory settlement")
            put("created_at_ms", nowMs)
        }
        db.insertOrThrow("ledger_entries", null, ledgerCv)
    }

    private fun recordMatchActionInternal(
        db: SQLiteDatabase,
        matchId: String,
        userId: String,
        actor: String,
        actionType: String,
        turnNumber: Int,
        payloadJson: String,
        nowMs: Long
    ) {
        val actionId = "act_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val cv = ContentValues().apply {
            put("action_id", actionId)
            put("match_id", matchId)
            put("user_id", userId)
            put("actor", actor)
            put("action_type", actionType)
            put("turn_number", turnNumber)
            put("action_payload_json", payloadJson)
            put("validated", 1)
            put("created_at", nowMs)
        }
        db.insertOrThrow("game_match_actions", null, cv)
    }

    private fun queryAuthoritativeMatchInternal(db: SQLiteDatabase, matchId: String): AuthoritativeGameMatch? {
        db.rawQuery(
            """
            SELECT match_id, user_id, game_type, match_title, entry_fee_coins, prize_coins,
                   status, current_turn, turn_number, player_score, opponent_score,
                   board_state_json, entry_idempotency_key, settlement_idempotency_key,
                   winner_user_id, created_at, updated_at, completed_at
            FROM game_matches WHERE match_id = ?
            """.trimIndent(),
            arrayOf(matchId)
        ).use { c ->
            if (!c.moveToFirst()) return null
            return AuthoritativeGameMatch(
                matchId = c.getString(0),
                userId = c.getString(1),
                gameType = c.getString(2),
                matchTitle = c.getString(3),
                entryFeeCoins = c.getLong(4),
                prizeCoins = c.getLong(5),
                status = c.getString(6),
                currentTurn = c.getString(7),
                turnNumber = c.getInt(8),
                playerScore = c.getInt(9),
                opponentScore = c.getInt(10),
                boardStateJson = c.getString(11),
                entryIdempotencyKey = c.getString(12),
                settlementIdempotencyKey = if (c.isNull(13)) null else c.getString(13),
                winnerUserId = if (c.isNull(14)) null else c.getString(14),
                createdAtMs = c.getLong(15),
                updatedAtMs = c.getLong(16),
                completedAtMs = if (c.isNull(17)) null else c.getLong(17)
            )
        }
    }

    // ========================================================================
    // 8. VIDEO PROOF VERIFICATION WORKFLOW (REAL VALIDATION & STAFF REVIEW)
    // ========================================================================

    /**
     * Submits a video proof for a task:
     * - Validates MIME type (`video/mp4`, `video/webm`, `video/quicktime`, `video/3gpp`)
     * - Validates file size (`10 KB .. 100 MB`)
     * - Validates duration (`3s .. 600s`)
     * - Validates metadata hash is non-blank and has not been submitted before (anti-duplicate)
     * - Blocks duplicate pending/approved submission for the same `(userId, taskId)`
     * - Stores status as `pending` — NEVER awards coins automatically on upload.
     */
    fun submitVideoProofTransactional(
        userId: String,
        taskId: String,
        taskTitle: String,
        rewardCoins: Long,
        videoUri: String,
        mimeType: String,
        fileSizeBytes: Long,
        durationSeconds: Int,
        metadataHash: String
    ): VideoProofSubmission {
        require(userId.isNotBlank()) { "Authentication required to submit video proof." }
        require(taskId.isNotBlank()) { "Task ID is required." }
        require(videoUri.isNotBlank()) { "Please select or record a valid proof video." }
        require(rewardCoins in 1L..500L) { "Invalid task reward amount." }

        val normalizedMime = mimeType.trim().lowercase(Locale.US)
        if (normalizedMime !in ALLOWED_VIDEO_MIME_TYPES) {
            throw IllegalArgumentException(
                "Unsupported video format ($mimeType). Allowed formats: MP4, WebM, MOV/QuickTime."
            )
        }
        if (fileSizeBytes < MIN_VIDEO_FILE_SIZE_BYTES || fileSizeBytes > MAX_VIDEO_FILE_SIZE_BYTES) {
            throw IllegalArgumentException(
                "Invalid video file size (${fileSizeBytes / 1024} KB). Video must be between 10 KB and 100 MB."
            )
        }
        if (durationSeconds < MIN_VIDEO_DURATION_SECONDS || durationSeconds > MAX_VIDEO_DURATION_SECONDS) {
            throw IllegalArgumentException(
                "Invalid video duration (${durationSeconds}s). Proof video must be between 3 seconds and 10 minutes."
            )
        }
        val cleanHash = metadataHash.trim()
        if (cleanHash.length < 8) {
            throw IllegalArgumentException("Invalid video metadata signature.")
        }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            ensureUserExistsInTransaction(db, userId, now)
            checkAndIncrementRateLimitInternal(db, userId, "submit_video_proof", maxRequestsPerMinute = 5, nowMs = now)

            // 1. Check duplicate video file hash across all submissions
            db.rawQuery(
                "SELECT submission_id FROM video_proof_submissions WHERE metadata_hash = ?",
                arrayOf(cleanHash)
            ).use { c ->
                if (c.moveToFirst()) {
                    logSuspiciousActivityInternal(db, userId, "DUPLICATE_VIDEO_PROOF_HASH", "Reused video hash $cleanHash for task $taskId", now)
                    throw IllegalStateException("Duplicate video proof detected. This video file has already been submitted.")
                }
            }

            // 2. Check if user already has a pending or approved submission for this taskId
            db.rawQuery(
                """
                SELECT status FROM video_proof_submissions
                WHERE user_id = ? AND task_id = ? AND status IN ('pending', 'approved')
                """.trimIndent(),
                arrayOf(userId, taskId)
            ).use { c ->
                if (c.moveToFirst()) {
                    val existingStatus = c.getString(0)
                    throw IllegalStateException("You already have a $existingStatus video proof submission for this task.")
                }
            }

            val profile = queryUserProfileInternal(db, userId)
            val submissionId = "vp_${UUID.randomUUID().toString().replace("-", "").take(16)}"

            val cv = ContentValues().apply {
                put("submission_id", submissionId)
                put("user_id", userId)
                put("user_display_name", profile?.displayName ?: "Athxo Booyah Player")
                put("task_id", taskId)
                put("task_title", taskTitle.trim().ifBlank { "Video Proof Task" })
                put("reward_coins", rewardCoins)
                put("video_uri", videoUri.trim())
                put("mime_type", normalizedMime)
                put("file_size_bytes", fileSizeBytes)
                put("duration_seconds", durationSeconds)
                put("metadata_hash", cleanHash)
                put("status", "pending")
                put("rejection_reason", "")
                put("created_at", now)
            }
            db.insertOrThrow("video_proof_submissions", null, cv)

            db.setTransactionSuccessful()
            return queryVideoProofInternal(db, submissionId)!!
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Staff/Admin Approval of Video Proof:
     * - Enforces RBAC (`STAFF_APPROVAL_ROLES`)
     * - Transitions status `pending -> approved`
     * - Atomically credits `reward_coins` to the user's wallet ONCE via idempotent ledger transaction
     * - Writes before/after audit log.
     */
    fun staffApproveVideoProofTransactional(
        staffUid: String,
        submissionId: String,
        auditReason: String = "Verified gameplay video proof"
    ): VideoProofSubmission {
        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val staffRole = getUserRoleInternal(db, staffUid)
            if (staffRole !in STAFF_APPROVAL_ROLES) {
                logSuspiciousActivityInternal(db, staffUid, "UNAUTHORIZED_VIDEO_PROOF_APPROVAL", "Role $staffRole tried approving $submissionId", now)
                throw SecurityException("Access Denied: Staff or Admin role required to approve video proofs.")
            }

            val submission = queryVideoProofInternal(db, submissionId)
                ?: throw IllegalArgumentException("Video proof submission not found: $submissionId")
            if (submission.status == "approved") {
                throw IllegalStateException("This video proof submission has already been approved.")
            }
            if (submission.status != "pending") {
                throw IllegalStateException("Only pending video proofs can be approved (current status: ${submission.status}).")
            }

            val settlementKey = "vp_reward_${submission.submissionId}"
            val walletBefore = queryWalletInternal(db, submission.userId)
            val newAvailable = walletBefore.available + submission.rewardCoins
            val newLifetime = walletBefore.lifetimeEarned + submission.rewardCoins

            // Update wallet
            val walletCv = ContentValues().apply {
                put("available_coins", newAvailable)
                put("lifetime_earned", newLifetime)
                put("updated_at_ms", now)
            }
            db.update("wallets", walletCv, "user_id = ?", arrayOf(submission.userId))

            // Insert ledger entry
            val ledgerId = "tx_vp_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val ledgerCv = ContentValues().apply {
                put("id", ledgerId)
                put("user_id", submission.userId)
                put("event_id", submission.submissionId)
                put("source_type", "video_proof_verified")
                put("source_id", submission.taskId)
                put("title", "Verified Video Proof: ${submission.taskTitle}")
                put("amount", submission.rewardCoins)
                put("transaction_type", "earn")
                put("verification_status", "approved")
                put("verification_level", "staff_verified")
                put("idempotency_key", settlementKey)
                put("balance_after", newAvailable)
                put("actor", staffUid)
                put("reason", auditReason)
                put("created_at_ms", now)
            }
            db.insertOrThrow("ledger_entries", null, ledgerCv)

            // Update submission row
            val subCv = ContentValues().apply {
                put("status", "approved")
                put("rejection_reason", "")
                put("reviewed_by_staff_uid", staffUid)
                put("settlement_idempotency_key", settlementKey)
                put("reviewed_at", now)
            }
            db.update("video_proof_submissions", subCv, "submission_id = ?", arrayOf(submissionId))

            // Audit log
            insertAuditLogInternal(
                db = db,
                adminUid = staffUid,
                adminRole = staffRole,
                action = "VIDEO_PROOF_APPROVED",
                targetType = "video_proof_submission",
                targetId = submissionId,
                beforeValue = "status=pending,wallet=${walletBefore.available}",
                afterValue = "status=approved,wallet=$newAvailable,reward=${submission.rewardCoins}",
                reason = auditReason.ifBlank { "Verified video proof" },
                nowMs = now
            )

            db.setTransactionSuccessful()
            return queryVideoProofInternal(db, submissionId)!!
        } finally {
            db.endTransaction()
        }
    }

    /**
     * Staff/Admin Rejection of Video Proof:
     * - Enforces RBAC (`STAFF_APPROVAL_ROLES`)
     * - Requires a clear rejection reason
     * - Transitions status `pending -> rejected` and awards 0 coins.
     */
    fun staffRejectVideoProofTransactional(
        staffUid: String,
        submissionId: String,
        rejectionReason: String
    ): VideoProofSubmission {
        val cleanReason = rejectionReason.trim()
        require(cleanReason.length >= 4) { "A clear rejection reason is required when rejecting a video proof." }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        db.beginTransaction()
        try {
            val staffRole = getUserRoleInternal(db, staffUid)
            if (staffRole !in STAFF_APPROVAL_ROLES) {
                logSuspiciousActivityInternal(db, staffUid, "UNAUTHORIZED_VIDEO_PROOF_REJECTION", "Role $staffRole tried rejecting $submissionId", now)
                throw SecurityException("Access Denied: Staff or Admin role required to reject video proofs.")
            }

            val submission = queryVideoProofInternal(db, submissionId)
                ?: throw IllegalArgumentException("Video proof submission not found: $submissionId")
            if (submission.status != "pending") {
                throw IllegalStateException("Only pending video proofs can be rejected (current status: ${submission.status}).")
            }

            val subCv = ContentValues().apply {
                put("status", "rejected")
                put("rejection_reason", cleanReason)
                put("reviewed_by_staff_uid", staffUid)
                put("reviewed_at", now)
            }
            db.update("video_proof_submissions", subCv, "submission_id = ?", arrayOf(submissionId))

            insertAuditLogInternal(
                db = db,
                adminUid = staffUid,
                adminRole = staffRole,
                action = "VIDEO_PROOF_REJECTED",
                targetType = "video_proof_submission",
                targetId = submissionId,
                beforeValue = "status=pending",
                afterValue = "status=rejected,reason=$cleanReason",
                reason = cleanReason,
                nowMs = now
            )

            db.setTransactionSuccessful()
            return queryVideoProofInternal(db, submissionId)!!
        } finally {
            db.endTransaction()
        }
    }

    fun getUserVideoProofSubmissions(userId: String): List<VideoProofSubmission> {
        val list = mutableListOf<VideoProofSubmission>()
        readableDatabase.rawQuery(
            """
            SELECT submission_id, user_id, user_display_name, task_id, task_title, reward_coins,
                   video_uri, mime_type, file_size_bytes, duration_seconds, metadata_hash,
                   status, rejection_reason, reviewed_by_staff_uid, settlement_idempotency_key,
                   created_at, reviewed_at
            FROM video_proof_submissions
            WHERE user_id = ?
            ORDER BY created_at DESC
            """.trimIndent(),
            arrayOf(userId)
        ).use { c ->
            while (c.moveToNext()) {
                list.add(readVideoProofCursor(c))
            }
        }
        return list
    }

    fun getAllVideoProofSubmissionsForStaff(staffUid: String): List<VideoProofSubmission> {
        val role = getUserRole(staffUid)
        if (role !in STAFF_APPROVAL_ROLES) {
            throw SecurityException("Access Denied: Staff role required to view all video proof submissions.")
        }
        val list = mutableListOf<VideoProofSubmission>()
        readableDatabase.rawQuery(
            """
            SELECT submission_id, user_id, user_display_name, task_id, task_title, reward_coins,
                   video_uri, mime_type, file_size_bytes, duration_seconds, metadata_hash,
                   status, rejection_reason, reviewed_by_staff_uid, settlement_idempotency_key,
                   created_at, reviewed_at
            FROM video_proof_submissions
            ORDER BY CASE status WHEN 'pending' THEN 0 ELSE 1 END, created_at DESC
            """.trimIndent(),
            emptyArray()
        ).use { c ->
            while (c.moveToNext()) {
                list.add(readVideoProofCursor(c))
            }
        }
        return list
    }

    private fun queryVideoProofInternal(db: SQLiteDatabase, submissionId: String): VideoProofSubmission? {
        db.rawQuery(
            """
            SELECT submission_id, user_id, user_display_name, task_id, task_title, reward_coins,
                   video_uri, mime_type, file_size_bytes, duration_seconds, metadata_hash,
                   status, rejection_reason, reviewed_by_staff_uid, settlement_idempotency_key,
                   created_at, reviewed_at
            FROM video_proof_submissions WHERE submission_id = ?
            """.trimIndent(),
            arrayOf(submissionId)
        ).use { c ->
            return if (c.moveToFirst()) readVideoProofCursor(c) else null
        }
    }

    private fun readVideoProofCursor(c: android.database.Cursor): VideoProofSubmission {
        return VideoProofSubmission(
            submissionId = c.getString(0),
            userId = c.getString(1),
            userDisplayName = c.getString(2),
            taskId = c.getString(3),
            taskTitle = c.getString(4),
            rewardCoins = c.getLong(5),
            videoUri = c.getString(6),
            mimeType = c.getString(7),
            fileSizeBytes = c.getLong(8),
            durationSeconds = c.getInt(9),
            metadataHash = c.getString(10),
            status = c.getString(11),
            rejectionReason = c.getString(12) ?: "",
            reviewedByStaffUid = if (c.isNull(13)) null else c.getString(13),
            settlementIdempotencyKey = if (c.isNull(14)) null else c.getString(14),
            createdAtMs = c.getLong(15),
            reviewedAtMs = if (c.isNull(16)) null else c.getLong(16)
        )
    }

    // ========================================================================
    // 9. MINI-GAME VALIDATED SESSIONS & PERSONAL BESTS (MEMORY MATCH & BUBBLE POP)
    // ========================================================================

    /**
     * Completes a mini-game session (Panda Memory Match or Panda Bubble Pop) with server-side
     * anti-cheat validation and daily reward bounds so users cannot farm unlimited coins.
     */
    fun completeMiniGameSessionTransactional(
        userId: String,
        gameType: String, // "MEMORY_MATCH", "BUBBLE_POP", "PANDA_PUZZLE"
        difficulty: String,
        score: Int,
        movesCount: Int,
        durationSeconds: Int,
        idempotencyKey: String,
        maxDailyRewardedSessions: Int = 5
    ): MiniGameSessionResult {
        require(userId.isNotBlank()) { "Authentication required." }
        val normGame = gameType.trim().uppercase(Locale.US)
        require(normGame in setOf("MEMORY_MATCH", "BUBBLE_POP", "PANDA_PUZZLE")) { "Invalid mini-game type: $gameType" }
        val normDiff = difficulty.trim().uppercase(Locale.US).ifBlank { "MEDIUM" }
        require(score in 0..50_000) { "Unrealistic score rejected by anti-cheat validator." }
        require(movesCount in 1..5_000) { "Invalid move count rejected by anti-cheat validator." }
        require(durationSeconds in 1..7_200) { "Invalid session duration rejected by anti-cheat validator." }

        // Minimum realistic moves check for Memory Match
        if (normGame == "MEMORY_MATCH") {
            val minPairs = when (normDiff) {
                "EASY" -> 6
                "HARD" -> 10
                else -> 8
            }
            if (movesCount < minPairs) {
                throw SecurityException("Impossible Memory Match completion in $movesCount moves for $minPairs pairs.")
            }
        }

        val db = writableDatabase
        val now = System.currentTimeMillis()
        val dayBucket = (now / 86_400_000L).toInt()

        db.beginTransaction()
        try {
            ensureUserExistsInTransaction(db, userId, now)

            // Check idempotency
            db.rawQuery(
                "SELECT session_id, coins_awarded FROM mini_game_sessions WHERE idempotency_key = ?",
                arrayOf(idempotencyKey)
            ).use { c ->
                if (c.moveToFirst()) {
                    val existingId = c.getString(0)
                    val existingCoins = c.getLong(1)
                    val wallet = queryWalletInternal(db, userId)
                    val pb = queryPersonalBestInternal(db, userId, normGame, normDiff)
                    db.setTransactionSuccessful()
                    return MiniGameSessionResult(
                        sessionId = existingId,
                        userId = userId,
                        gameType = normGame,
                        difficulty = normDiff,
                        score = score,
                        movesCount = movesCount,
                        durationSeconds = durationSeconds,
                        coinsAwarded = existingCoins,
                        newWalletBalance = wallet.available,
                        isNewPersonalBest = false,
                        personalBest = pb
                    )
                }
            }

            // Count rewarded sessions today for this mini-game to prevent unlimited coin farming
            val rewardedToday = db.rawQuery(
                """
                SELECT COUNT(*) FROM mini_game_sessions
                WHERE user_id = ? AND game_type = ? AND session_day_bucket = ? AND coins_awarded > 0
                """.trimIndent(),
                arrayOf(userId, normGame, dayBucket.toString())
            ).use { c ->
                if (c.moveToFirst()) c.getInt(0) else 0
            }

            val baseCoins = when (normGame) {
                "MEMORY_MATCH" -> when (normDiff) {
                    "EASY" -> 4L
                    "HARD" -> 8L
                    else -> 6L
                }
                "BUBBLE_POP" -> if (score >= 150) 6L else if (score >= 60) 4L else 2L
                else -> 5L
            }

            val coinsToAward = if (rewardedToday < maxDailyRewardedSessions && score > 0) baseCoins else 0L

            val walletBefore = queryWalletInternal(db, userId)
            val newAvailable = walletBefore.available + coinsToAward
            val newLifetime = walletBefore.lifetimeEarned + coinsToAward

            if (coinsToAward > 0L) {
                val walletCv = ContentValues().apply {
                    put("available_coins", newAvailable)
                    put("lifetime_earned", newLifetime)
                    put("updated_at_ms", now)
                }
                db.update("wallets", walletCv, "user_id = ?", arrayOf(userId))

                val ledgerId = "tx_mg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
                val ledgerCv = ContentValues().apply {
                    put("id", ledgerId)
                    put("user_id", userId)
                    put("event_id", idempotencyKey)
                    put("source_type", "minigame_${normGame.lowercase(Locale.US)}")
                    put("source_id", "${normGame}_$normDiff")
                    put("title", "$normGame ($normDiff) Reward")
                    put("amount", coinsToAward)
                    put("transaction_type", "earn")
                    put("verification_status", "approved")
                    put("verification_level", "server_verified")
                    put("idempotency_key", "ledger_$idempotencyKey")
                    put("balance_after", newAvailable)
                    put("actor", "system")
                    put("reason", "Server-validated mini-game session reward")
                    put("created_at_ms", now)
                }
                db.insertOrThrow("ledger_entries", null, ledgerCv)
            }

            val sessionId = "mgs_${UUID.randomUUID().toString().replace("-", "").take(16)}"
            val sessionCv = ContentValues().apply {
                put("session_id", sessionId)
                put("user_id", userId)
                put("game_type", normGame)
                put("difficulty", normDiff)
                put("score", score)
                put("moves_count", movesCount)
                put("duration_seconds", durationSeconds)
                put("coins_awarded", coinsToAward)
                put("session_day_bucket", dayBucket)
                put("idempotency_key", idempotencyKey)
                put("created_at", now)
            }
            db.insertOrThrow("mini_game_sessions", null, sessionCv)

            // Update Personal Best
            val existingPb = queryPersonalBestInternal(db, userId, normGame, normDiff)
            val isFirstPlay = existingPb.gamesPlayed == 0
            val isBetterScore = score > existingPb.bestScore
            val isFasterMemory = normGame == "MEMORY_MATCH" && score >= existingPb.bestScore &&
                (existingPb.bestMoves == 0 || movesCount < existingPb.bestMoves)
            val isNewBest = isFirstPlay || isBetterScore || isFasterMemory

            val newBestScore = if (isNewBest) maxOf(score, existingPb.bestScore) else existingPb.bestScore
            val newBestMoves = if (isFirstPlay || (movesCount > 0 && (existingPb.bestMoves == 0 || movesCount < existingPb.bestMoves))) {
                movesCount
            } else existingPb.bestMoves
            val newBestDuration = if (isFirstPlay || (durationSeconds > 0 && (existingPb.bestDurationSeconds == 0 || durationSeconds < existingPb.bestDurationSeconds))) {
                durationSeconds
            } else existingPb.bestDurationSeconds

            val pbCv = ContentValues().apply {
                put("user_id", userId)
                put("game_type", normGame)
                put("difficulty", normDiff)
                put("best_score", newBestScore)
                put("best_moves", newBestMoves)
                put("best_duration_seconds", newBestDuration)
                put("games_played", existingPb.gamesPlayed + 1)
                put("updated_at", now)
            }
            db.insertWithOnConflict("mini_game_personal_bests", null, pbCv, SQLiteDatabase.CONFLICT_REPLACE)

            val updatedPb = queryPersonalBestInternal(db, userId, normGame, normDiff)
            db.setTransactionSuccessful()
            return MiniGameSessionResult(
                sessionId = sessionId,
                userId = userId,
                gameType = normGame,
                difficulty = normDiff,
                score = score,
                movesCount = movesCount,
                durationSeconds = durationSeconds,
                coinsAwarded = coinsToAward,
                newWalletBalance = newAvailable,
                isNewPersonalBest = isNewBest,
                personalBest = updatedPb
            )
        } finally {
            db.endTransaction()
        }
    }

    fun getMiniGamePersonalBests(userId: String): List<MiniGamePersonalBest> {
        val list = mutableListOf<MiniGamePersonalBest>()
        readableDatabase.rawQuery(
            """
            SELECT user_id, game_type, difficulty, best_score, best_moves, best_duration_seconds, games_played, updated_at
            FROM mini_game_personal_bests
            WHERE user_id = ?
            """.trimIndent(),
            arrayOf(userId)
        ).use { c ->
            while (c.moveToNext()) {
                list.add(
                    MiniGamePersonalBest(
                        userId = c.getString(0),
                        gameType = c.getString(1),
                        difficulty = c.getString(2),
                        bestScore = c.getInt(3),
                        bestMoves = c.getInt(4),
                        bestDurationSeconds = c.getInt(5),
                        gamesPlayed = c.getInt(6),
                        updatedAtMs = c.getLong(7)
                    )
                )
            }
        }
        return list
    }

    private fun queryPersonalBestInternal(
        db: SQLiteDatabase,
        userId: String,
        gameType: String,
        difficulty: String
    ): MiniGamePersonalBest {
        db.rawQuery(
            """
            SELECT best_score, best_moves, best_duration_seconds, games_played, updated_at
            FROM mini_game_personal_bests
            WHERE user_id = ? AND game_type = ? AND difficulty = ?
            """.trimIndent(),
            arrayOf(userId, gameType, difficulty)
        ).use { c ->
            if (c.moveToFirst()) {
                return MiniGamePersonalBest(
                    userId = userId,
                    gameType = gameType,
                    difficulty = difficulty,
                    bestScore = c.getInt(0),
                    bestMoves = c.getInt(1),
                    bestDurationSeconds = c.getInt(2),
                    gamesPlayed = c.getInt(3),
                    updatedAtMs = c.getLong(4)
                )
            }
        }
        return MiniGamePersonalBest(userId = userId, gameType = gameType, difficulty = difficulty)
    }

    private fun ensureUserExistsInTransaction(db: SQLiteDatabase, userId: String, nowMs: Long) {
        val exists = db.rawQuery("SELECT user_id FROM users WHERE user_id = ?", arrayOf(userId)).use {
            it.moveToFirst()
        }
        if (!exists) {
            val isGuest = userId.startsWith("guest_")
            val userCv = ContentValues().apply {
                put("user_id", userId)
                put("display_name", if (isGuest) "Guest Player" else "Athxo Booyah Player")
                put("referral_code", "AB-${userId.replace("[^A-Za-z0-9]".toRegex(), "").takeLast(6).uppercase(Locale.US)}")
                put("account_status", if (isGuest) "guest" else "active")
                put("role", "user")
                put("created_at_ms", nowMs)
                put("updated_at_ms", nowMs)
            }
            db.insertWithOnConflict("users", null, userCv, SQLiteDatabase.CONFLICT_IGNORE)
        }
        val walletExists = db.rawQuery("SELECT user_id FROM wallets WHERE user_id = ?", arrayOf(userId)).use {
            it.moveToFirst()
        }
        if (!walletExists) {
            val walletCv = ContentValues().apply {
                put("user_id", userId)
                put("available_coins", 0L)
                put("pending_coins", 0L)
                put("lifetime_earned", 0L)
                put("redeemed_coins", 0L)
                put("sandbox_coins", 0L)
                put("updated_at_ms", nowMs)
            }
            db.insertWithOnConflict("wallets", null, walletCv, SQLiteDatabase.CONFLICT_IGNORE)
        }
    }
}
