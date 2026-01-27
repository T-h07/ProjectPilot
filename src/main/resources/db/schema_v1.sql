-- ProjectPilot schema v1
-- IDs: TEXT (UUID)
-- Times: INTEGER epoch millis

CREATE TABLE IF NOT EXISTS projects (
                                        id           TEXT PRIMARY KEY,
                                        name         TEXT NOT NULL,
                                        description  TEXT NOT NULL DEFAULT '',
                                        stakeholders TEXT NOT NULL DEFAULT '',
                                        status       TEXT NOT NULL DEFAULT 'ACTIVE',
                                        created_at   INTEGER NOT NULL,
                                        updated_at   INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS phases (
                                      id         TEXT PRIMARY KEY,
                                      project_id TEXT NOT NULL,
                                      name       TEXT NOT NULL,
                                      sort_index INTEGER NOT NULL DEFAULT 0,
                                      start_at   INTEGER,
                                      end_at     INTEGER,
                                      is_done    INTEGER NOT NULL DEFAULT 0,
                                      created_at INTEGER NOT NULL,
                                      updated_at INTEGER NOT NULL,
                                      FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS members (
                                       id         TEXT PRIMARY KEY,
                                       name       TEXT NOT NULL,
                                       role       TEXT NOT NULL DEFAULT '',
                                       email      TEXT NOT NULL DEFAULT '',
                                       created_at INTEGER NOT NULL,
                                       updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS project_members (
                                               project_id   TEXT NOT NULL,
                                               member_id    TEXT NOT NULL,
                                               project_role TEXT NOT NULL DEFAULT '',
                                               added_at     INTEGER NOT NULL,
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
                                     assignee_member_id  TEXT,
                                     start_at            INTEGER,
                                     due_at              INTEGER,
                                     completed_at        INTEGER,
                                     sort_index          INTEGER NOT NULL DEFAULT 0,
                                     created_at          INTEGER NOT NULL,
                                     updated_at          INTEGER NOT NULL,
                                     FOREIGN KEY (project_id)         REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (phase_id)           REFERENCES phases(id)   ON DELETE SET NULL,
    FOREIGN KEY (assignee_member_id) REFERENCES members(id)  ON DELETE SET NULL
    );

CREATE TABLE IF NOT EXISTS milestones (
                                          id         TEXT PRIMARY KEY,
                                          project_id TEXT NOT NULL,
                                          title      TEXT NOT NULL,
                                          target_at  INTEGER,
                                          is_done    INTEGER NOT NULL DEFAULT 0,
                                          done_at    INTEGER,
                                          created_at INTEGER NOT NULL,
                                          updated_at INTEGER NOT NULL,
                                          FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS activity_log (
                                            id          TEXT PRIMARY KEY,
                                            at          INTEGER NOT NULL,
                                            actor       TEXT NOT NULL DEFAULT '',
                                            project_id  TEXT,
                                            entity_type TEXT NOT NULL,
                                            entity_id   TEXT NOT NULL,
                                            action      TEXT NOT NULL,
                                            details     TEXT NOT NULL DEFAULT '',
                                            FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE SET NULL
    );

CREATE INDEX IF NOT EXISTS idx_phases_project_sort
    ON phases(project_id, sort_index);

CREATE INDEX IF NOT EXISTS idx_tasks_project_status
    ON tasks(project_id, status);

CREATE INDEX IF NOT EXISTS idx_tasks_phase_sort
    ON tasks(phase_id, sort_index);

CREATE INDEX IF NOT EXISTS idx_project_members_member
    ON project_members(member_id);

CREATE INDEX IF NOT EXISTS idx_activity_project_at
    ON activity_log(project_id, at DESC);
