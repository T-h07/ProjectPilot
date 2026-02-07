-- ProjectPilot schema v3 (Postgres)
-- IDs: TEXT (UUID)
-- Times: BIGINT epoch millis

CREATE TABLE IF NOT EXISTS projects (
    id             TEXT PRIMARY KEY,
    name           TEXT NOT NULL,
    description    TEXT NOT NULL DEFAULT '',
    stakeholders   TEXT NOT NULL DEFAULT '',
    phase_template TEXT NOT NULL DEFAULT 'EMPTY',
    start_date     BIGINT,
    end_date       BIGINT,
    health         TEXT NOT NULL DEFAULT 'ON_TRACK',
    status         TEXT NOT NULL DEFAULT 'ACTIVE',
    completed_date BIGINT,
    created_at     BIGINT NOT NULL,
    updated_at     BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS phases (
    id         TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    name       TEXT NOT NULL,
    sort_index BIGINT NOT NULL DEFAULT 0,
    start_at   BIGINT,
    end_at     BIGINT,
    is_done    BIGINT NOT NULL DEFAULT 0,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS members (
    id         TEXT PRIMARY KEY,
    name       TEXT NOT NULL,
    role       TEXT NOT NULL DEFAULT 'MEMBER',
    email      TEXT NOT NULL DEFAULT '',
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL
);

CREATE TABLE IF NOT EXISTS project_members (
    project_id   TEXT NOT NULL,
    member_id    TEXT NOT NULL,
    project_role TEXT NOT NULL DEFAULT 'MEMBER',
    added_at     BIGINT NOT NULL,
    PRIMARY KEY (project_id, member_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id)  REFERENCES members(id)  ON DELETE CASCADE
);

CREATE TABLE IF NOT EXISTS tasks (
    id                 TEXT PRIMARY KEY,
    project_id          TEXT NOT NULL,
    phase_id            TEXT,
    title               TEXT NOT NULL,
    details             TEXT NOT NULL DEFAULT '',
    status              TEXT NOT NULL DEFAULT 'TODO',
    priority            TEXT NOT NULL DEFAULT 'MEDIUM',
    assignee_member_id  TEXT,
    start_at            BIGINT,
    due_at              BIGINT,
    completed_at        BIGINT,
    sort_index          BIGINT NOT NULL DEFAULT 0,
    created_at          BIGINT NOT NULL,
    updated_at          BIGINT NOT NULL,
    FOREIGN KEY (project_id)         REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (phase_id)           REFERENCES phases(id)   ON DELETE SET NULL,
    FOREIGN KEY (assignee_member_id) REFERENCES members(id)  ON DELETE SET NULL
);

CREATE TABLE IF NOT EXISTS milestones (
    id         TEXT PRIMARY KEY,
    project_id TEXT NOT NULL,
    title      TEXT NOT NULL,
    target_at  BIGINT,
    is_done    BIGINT NOT NULL DEFAULT 0,
    done_at    BIGINT,
    created_at BIGINT NOT NULL,
    updated_at BIGINT NOT NULL,
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
);

-- v3: activity_log now includes actor_member_id
CREATE TABLE IF NOT EXISTS activity_log (
    id              TEXT PRIMARY KEY,
    at              BIGINT NOT NULL,
    actor           TEXT NOT NULL DEFAULT '',
    actor_member_id TEXT,
    project_id      TEXT,
    entity_type     TEXT NOT NULL,
    entity_id       TEXT NOT NULL,
    action          TEXT NOT NULL,
    details         TEXT NOT NULL DEFAULT '',
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL
);

-- v3: auth users
CREATE TABLE IF NOT EXISTS auth_users (
    member_id     TEXT PRIMARY KEY,
    username      TEXT NOT NULL UNIQUE,
    password_hash TEXT NOT NULL,
    salt          TEXT NOT NULL DEFAULT '',
    global_role   TEXT NOT NULL DEFAULT 'USER' CHECK (global_role IN ('ADMIN','USER')),
    is_active     BIGINT NOT NULL DEFAULT 1,
    created_at    BIGINT NOT NULL,
    updated_at    BIGINT NOT NULL,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

-- Indexes
CREATE INDEX IF NOT EXISTS idx_phases_project_sort ON phases(project_id, sort_index);
CREATE INDEX IF NOT EXISTS idx_tasks_project_status ON tasks(project_id, status);
CREATE INDEX IF NOT EXISTS idx_tasks_phase_sort ON tasks(phase_id, sort_index);
CREATE INDEX IF NOT EXISTS idx_project_members_member ON project_members(member_id);
CREATE INDEX IF NOT EXISTS idx_activity_at ON activity_log(at DESC);
CREATE INDEX IF NOT EXISTS idx_activity_actor_member ON activity_log(actor_member_id);
CREATE INDEX IF NOT EXISTS idx_auth_users_username ON auth_users(username);
