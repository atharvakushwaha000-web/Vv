-- =============================================================================
-- ATHXO BOOYAH — V3 SQL MIGRATION
-- Complete Game Integration (Authoritative Ludo & Carrom Matches),
-- Mini-Game Anti-Farming Sessions & Personal Bests,
-- and Real Video Proof Verification Workflow
-- =============================================================================

-- 1. Authoritative Multiplayer/Board Game Matches (Ludo & Carrom)
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
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_game_matches_user_status
    ON game_matches(user_id, status, updated_at DESC);

-- 2. Authoritative Match Action Log (Anti-Cheat Move Validation Audit Trail)
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
);

CREATE INDEX IF NOT EXISTS idx_game_match_actions_match
    ON game_match_actions(match_id, turn_number ASC);

-- 3. Video Proof Verification Submissions
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
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_video_proof_user_created
    ON video_proof_submissions(user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_video_proof_status_created
    ON video_proof_submissions(status, created_at ASC);

CREATE UNIQUE INDEX IF NOT EXISTS idx_video_proof_unique_hash
    ON video_proof_submissions(metadata_hash);

-- 4. Mini-Game Validated Sessions & Personal Bests (Panda Memory Match & Panda Bubble Pop)
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
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_mini_game_sessions_user_day
    ON mini_game_sessions(user_id, game_type, session_day_bucket);

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
    FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);
