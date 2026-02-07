-- Chat/messaging feature (v8) - Postgres

CREATE TABLE IF NOT EXISTS chat_threads (
    id         TEXT PRIMARY KEY,
    type       TEXT NOT NULL, -- DIRECT | TEAM | GROUP
    title      TEXT NOT NULL DEFAULT '',
    team_id    TEXT,
    direct_key TEXT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_direct_key
    ON chat_threads(direct_key);

CREATE UNIQUE INDEX IF NOT EXISTS idx_chat_team_id
    ON chat_threads(team_id)
    WHERE team_id IS NOT NULL;

CREATE TABLE IF NOT EXISTS chat_members (
    thread_id TEXT NOT NULL,
    member_id TEXT NOT NULL,
    joined_at BIGINT NOT NULL,
    PRIMARY KEY (thread_id, member_id),
    FOREIGN KEY (thread_id) REFERENCES chat_threads(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_chat_members_member
    ON chat_members(member_id);

CREATE INDEX IF NOT EXISTS idx_chat_members_thread
    ON chat_members(thread_id);

CREATE TABLE IF NOT EXISTS chat_messages (
    id         TEXT PRIMARY KEY,
    thread_id  TEXT NOT NULL,
    sender_id  TEXT,
    body       TEXT NOT NULL,
    created_at BIGINT NOT NULL,
    FOREIGN KEY (thread_id) REFERENCES chat_threads(id) ON DELETE CASCADE,
    FOREIGN KEY (sender_id) REFERENCES members(id) ON DELETE SET NULL
);

CREATE INDEX IF NOT EXISTS idx_chat_messages_thread_time
    ON chat_messages(thread_id, created_at);
