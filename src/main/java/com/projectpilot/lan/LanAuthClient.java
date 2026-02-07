package com.projectpilot.lan;

import com.projectpilot.data.db.auth.AuthException;
import com.projectpilot.data.db.auth.AuthProvider;
import com.projectpilot.data.db.auth.UserSession;

public final class LanAuthClient implements AuthProvider {

    private final LanClient client;

    public LanAuthClient(LanClient client) {
        this.client = client;
    }

    @Override
    public boolean needsInitialAdmin() {
        return false;
    }

    @Override
    public UserSession login(String username, String password) {
        return client.login(username, password);
    }

    @Override
    public UserSession createInitialAdmin(String displayName, String username, String email, String password) {
        throw new AuthException("LAN clients cannot create admins.");
    }
}
