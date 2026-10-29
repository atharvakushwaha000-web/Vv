-- ============================================================================
-- Athxo Booyah — V2 Redeem Workflow, Rate Limiting & RBAC Security Hardening
-- ============================================================================
-- Adds rate-limiting / suspicious-activity audit table and ensures strict
-- separation between pending requests (assigned_code IS NULL) and approved
-- requests (status = 'successful' AND assigned_code IS NOT NULL).

CREATE TABLE IF NOT EXISTS security_rate_limits (
    rate_key TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    endpoint TEXT NOT NULL,
    window_start_ms BIGINT NOT NULL,
    request_count INTEGER NOT NULL DEFAULT 1,
    flagged_suspicious INTEGER NOT NULL DEFAULT 0
);

CREATE INDEX IF NOT EXISTS idx_security_rate_user ON security_rate_limits(user_id, endpoint);

CREATE TABLE IF NOT EXISTS suspicious_activity_logs (
    id TEXT PRIMARY KEY,
    user_id TEXT NOT NULL,
    event_type TEXT NOT NULL,
    details TEXT NOT NULL,
    created_at_ms BIGINT NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_suspicious_user ON suspicious_activity_logs(user_id, created_at_ms DESC);
