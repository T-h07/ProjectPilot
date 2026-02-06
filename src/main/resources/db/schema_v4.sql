-- v4: link auth user to a member record
CREATE TABLE IF NOT EXISTS auth_users (
                                          id INTEGER PRIMARY KEY AUTOINCREMENT,
                                          username TEXT NOT NULL UNIQUE,
                                          password_hash TEXT NOT NULL,
                                          global_role TEXT NOT NULL,
                                          is_active INTEGER NOT NULL DEFAULT 1,
                                          member_id INTEGER NULL
);

CREATE INDEX IF NOT EXISTS idx_auth_users_member_id ON auth_users(member_id);
