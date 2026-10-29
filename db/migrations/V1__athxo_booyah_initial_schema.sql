-- ============================================================================
-- Athxo Booyah — V1 Initial Relational SQL Schema (PostgreSQL & SQLite Compatible)
-- ============================================================================
-- Migrates core authoritative entities from Firebase to Relational SQL:
-- 1. users (accounts, staff/admin roles, ownership email)
-- 2. wallets (server-authoritative coin balances with non-negative constraints)
-- 3. ledger_entries (immutable transaction ledger with unique idempotency keys)
-- 4. task_claims (verified task/mission completions with unique evidence hashes)
-- 5. reward_catalog (redeemable gift card denominations and stock counts)
-- 6. redemption_requests (pending -> successful / rejected redeem workflow)
-- 7. redeem_codes_inventory (assigned gift codes with strict user ownership)
-- 8. admin_audit_logs (append-only privileged staff/admin audit trail)

CREATE TABLE IF NOT EXISTS schema_migrations (
    version INTEGER PRIMARY KEY,
    description TEXT NOT NULL,
    applied_at_ms BIGINT NOT NULL,
    backup_snapshot_json TEXT
);

CREATE TABLE IF NOT EXISTS users (
    user_id TEXT PRIMARY KEY,
    display_name TEXT NOT NULL,
    email TEXT,
    photo_url TEXT,
    referral_code TEXT NOT NULL,
    referred_by TEXT,
    account_status TEXT NOT NULL DEFAULT 'active' CHECK (account_status IN ('active', 'guest', 'suspended')),
    role TEXT NOT NULL DEFAULT 'user' CHECK (role IN ('user', 'staff', 'support', 'contentModerator', 'campaignManager', 'financeAdmin', 'superAdmin', 'admin', 'owner')),
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_users_email ON users(email);
CREATE INDEX IF NOT EXISTS idx_users_role ON users(role);

CREATE TABLE IF NOT EXISTS wallets (
    user_id TEXT PRIMARY KEY,
    available_coins BIGINT NOT NULL DEFAULT 0 CHECK (available_coins >= 0),
    pending_coins BIGINT NOT NULL DEFAULT 0 CHECK (pending_coins >= 0),
    lifetime_earned BIGINT NOT NULL DEFAULT 0 CHECK (lifetime_earned >= 0),
    redeemed_coins BIGINT NOT NULL DEFAULT 0 CHECK (redeemed_coins >= 0),
    sandbox_coins BIGINT NOT NULL DEFAULT 0 CHECK (sandbox_coins >= 0),
    updated_at_ms BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE TABLE IF NOT EXISTS ledger_entries (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    event_id TEXT NOT NULL,
    source_type TEXT NOT NULL,
    source_id TEXT NOT NULL,
    title TEXT NOT NULL,
    amount BIGINT NOT NULL,
    transaction_type TEXT NOT NULL CHECK (transaction_type IN ('earn', 'entry_fee', 'reserve', 'redeem_complete', 'refund', 'admin_adjustment')),
    verification_status TEXT NOT NULL DEFAULT 'approved',
    verification_level TEXT NOT NULL DEFAULT 'server_verified',
    idempotency_key TEXT NOT NULL UNIQUE,
    balance_after BIGINT NOT NULL CHECK (balance_after >= 0),
    actor TEXT NOT NULL DEFAULT 'system',
    reason TEXT,
    created_at_ms BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    UNIQUE (user_id, source_type, event_id)
);

CREATE INDEX IF NOT EXISTS idx_ledger_user_created ON ledger_entries(user_id, created_at_ms DESC);
CREATE INDEX IF NOT EXISTS idx_ledger_idempotency ON ledger_entries(idempotency_key);

CREATE TABLE IF NOT EXISTS task_claims (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    task_type TEXT NOT NULL,
    task_id TEXT NOT NULL,
    campaign_id TEXT NOT NULL,
    title TEXT NOT NULL,
    reward_coins BIGINT NOT NULL CHECK (reward_coins >= 0),
    progress BIGINT NOT NULL DEFAULT 100,
    verification_status TEXT NOT NULL CHECK (verification_status IN ('submitted', 'in_review', 'approved', 'rejected')),
    evidence_hash TEXT UNIQUE,
    rejection_reason TEXT,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE,
    UNIQUE (user_id, campaign_id, task_id)
);

CREATE INDEX IF NOT EXISTS idx_task_claims_user ON task_claims(user_id, created_at_ms DESC);

CREATE TABLE IF NOT EXISTS reward_catalog (
    id TEXT PRIMARY KEY,
    visibility TEXT NOT NULL DEFAULT 'public',
    reward_name TEXT NOT NULL,
    denomination_inr BIGINT NOT NULL CHECK (denomination_inr > 0),
    points_cost BIGINT NOT NULL CHECK (points_cost > 0),
    availability TEXT NOT NULL CHECK (availability IN ('available', 'out_of_stock')),
    stock_count BIGINT NOT NULL DEFAULT 0 CHECK (stock_count >= 0),
    min_account_age_days BIGINT NOT NULL DEFAULT 1,
    region TEXT NOT NULL DEFAULT 'IN',
    status TEXT NOT NULL DEFAULT 'active',
    updated_at_ms BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS redemption_requests (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    user_email TEXT NOT NULL,
    reward_id TEXT NOT NULL,
    reward_name TEXT NOT NULL,
    denomination_inr BIGINT NOT NULL CHECK (denomination_inr > 0),
    points_reserved BIGINT NOT NULL CHECK (points_reserved > 0),
    status TEXT NOT NULL DEFAULT 'pending' CHECK (status IN ('pending', 'successful', 'rejected')),
    assigned_code TEXT,
    delivered_code_masked TEXT,
    rejection_reason TEXT,
    reviewed_by_uid TEXT,
    idempotency_key TEXT NOT NULL UNIQUE,
    created_at_ms BIGINT NOT NULL,
    updated_at_ms BIGINT NOT NULL,
    processed_at_ms BIGINT,
    FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE ON UPDATE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_redemptions_user ON redemption_requests(user_id, created_at_ms DESC);
CREATE INDEX IF NOT EXISTS idx_redemptions_status ON redemption_requests(status, created_at_ms ASC);

CREATE TABLE IF NOT EXISTS redeem_codes_inventory (
    code_id TEXT PRIMARY KEY,
    code TEXT NOT NULL UNIQUE,
    reward_id TEXT NOT NULL,
    assigned_request_id TEXT UNIQUE,
    assigned_user_id TEXT,
    is_assigned INTEGER NOT NULL DEFAULT 0 CHECK (is_assigned IN (0, 1)),
    created_at_ms BIGINT NOT NULL,
    assigned_at_ms BIGINT,
    FOREIGN KEY (assigned_request_id) REFERENCES redemption_requests(id) ON DELETE SET NULL
);

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
    created_at_ms BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_audit_logs_created ON admin_audit_logs(created_at_ms DESC);
