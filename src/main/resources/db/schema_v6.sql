-- Teams feature (v6)

CREATE TABLE IF NOT EXISTS teams (
                                     id TEXT PRIMARY KEY,
                                     name TEXT NOT NULL,
                                     created_at INTEGER NOT NULL,
                                     updated_at INTEGER NOT NULL
);

CREATE TABLE IF NOT EXISTS team_members (
                                            team_id TEXT NOT NULL,
                                            member_id TEXT NOT NULL,
                                            team_role TEXT NOT NULL DEFAULT 'MEMBER',
                                            added_at INTEGER NOT NULL,
                                            PRIMARY KEY (team_id, member_id),
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE,
    FOREIGN KEY (member_id) REFERENCES members(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_team_members_team
    ON team_members(team_id);

CREATE INDEX IF NOT EXISTS idx_team_members_member
    ON team_members(member_id);

CREATE TABLE IF NOT EXISTS project_teams (
                                             project_id TEXT NOT NULL,
                                             team_id TEXT NOT NULL,
                                             added_at INTEGER NOT NULL,
                                             PRIMARY KEY (project_id, team_id),
    FOREIGN KEY (project_id) REFERENCES projects(id) ON DELETE CASCADE,
    FOREIGN KEY (team_id) REFERENCES teams(id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_project_teams_project
    ON project_teams(project_id);

CREATE INDEX IF NOT EXISTS idx_project_teams_team
    ON project_teams(team_id);
