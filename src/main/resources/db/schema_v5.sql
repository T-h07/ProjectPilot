-- Notifications feature (v5)

CREATE TABLE IF NOT EXISTS notifications (
                                             id TEXT PRIMARY KEY,
                                             target_user_id TEXT NOT NULL,
                                             created_at TEXT NOT NULL,          -- ISO_LOCAL_DATE_TIME
                                             type TEXT NOT NULL,                -- enum name
                                             title TEXT NOT NULL,
                                             body TEXT,
                                             entity_kind TEXT,                  -- "TASK" | "PROJECT" | null
                                             entity_id TEXT,                    -- taskId/projectId
                                             actor_user_id TEXT,                -- optional
                                             read_at TEXT                       -- nullable ISO_LOCAL_DATE_TIME
);

CREATE INDEX IF NOT EXISTS idx_notifications_user_time
    ON notifications(target_user_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notifications_user_read
    ON notifications(target_user_id, read_at);

-- Last-seen tracking per user (so we can show "what’s new since last login")
CREATE TABLE IF NOT EXISTS user_notification_state (
                                                       user_id TEXT PRIMARY KEY,
                                                       last_seen_at TEXT                  -- nullable ISO_LOCAL_DATE_TIME
);
