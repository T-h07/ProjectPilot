-- ProjectPilot schema v3 (v2 + auth_users + activity_log.actor_member_id)

-- (keep your existing v2 CREATE TABLE statements here)
-- projects, phases, members, project_members, tasks, milestones...

CREATE TABLE IF NOT EXISTS activity_log (
                                            id              TEXT PRIMARY KEY,
                                            at              INTEGER NOT NULL,
                                            actor           TEXT NOT NULL DEFAULT '',
                                            actor_member_id TEXT,
                                            project_id      TEXT,
                                            entity_type     TEXT NOT NULL,
                                            entity_id       TEXT NOT NULL,
                                            action          TEXT NOT NULL,
                                            details         TEXT NOT NULL DEFAULT '',
                                            FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL
    );

CREATE INDEX IF NOT EXISTS idx_activity_at ON activity_log(at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_actor_member ON activity_log(actor_member_id);

CREATE TABLE IF NOT EXISTS auth_users (
                                          member_id     TEXT PRIMARY KEY,
                                          username      TEXT NOT NULL UNIQUE,
                                          password_hash TEXT NOT NULL,
                                          salt          TEXT NOT NULL,
                                          global_role   TEXT NOT NULL DEFAULT 'USER' CHECK (global_role IN ('ADMIN','USER')),
    is_active     INTEGER NOT NULL DEFAULT 1,
    created_at    INTEGER NOT NULL,
    updated_at    INTEGER NOT NULL,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
    );

CREATE INDEX IF NOT EXISTS idx_auth_users_username ON auth_users(username);

PRAGMA user_version = 3;
