package com.projectpilot.lan;

public record LanConfig(Mode mode, String host, int port, int wsPort, int pollMs) {

    public enum Mode { OFF, HOST, CLIENT }

    public static LanConfig fromSystem() {
        String modeRaw = sys("pp.lan.mode");
        String hostRaw = sys("pp.lan.host");
        String portRaw = sys("pp.lan.port");
        String pollRaw = sys("pp.lan.pollMs");
        String wsPortRaw = sys("pp.lan.wsPort");

        Mode mode = Mode.OFF;
        if (modeRaw != null && !modeRaw.isBlank()) {
            mode = parseMode(modeRaw);
        } else if (hostRaw != null && !hostRaw.isBlank()) {
            mode = Mode.CLIENT;
        }

        int port = parseInt(portRaw, 8090);
        int poll = parseInt(pollRaw, 1000);
        int wsPort = parseInt(wsPortRaw, port + 1);
        String host = normalizeHost(hostRaw, port);

        return new LanConfig(mode, host, port, wsPort, poll);
    }

    public boolean isHost() { return mode == Mode.HOST; }
    public boolean isClient() { return mode == Mode.CLIENT; }

    public String baseUrl() {
        if (host == null || host.isBlank()) return null;
        return host;
    }

    public String wsUrl() {
        if (host == null || host.isBlank()) return null;
        try {
            java.net.URI uri = java.net.URI.create(host);
            String scheme = uri.getScheme();
            String wsScheme = "https".equalsIgnoreCase(scheme) ? "wss" : "ws";
            String h = uri.getHost();
            if (h == null || h.isBlank()) return null;
            return wsScheme + "://" + h + ":" + wsPort + "/ws";
        } catch (Exception e) {
            return null;
        }
    }

    public static LanConfig forLocal(int port, int wsPort, int pollMs) {
        return new LanConfig(Mode.OFF, null, port, wsPort, pollMs);
    }

    public static LanConfig forHost(int port, int wsPort, int pollMs) {
        return new LanConfig(Mode.HOST, null, port, wsPort, pollMs);
    }

    public static LanConfig forClient(String host, int port, int wsPort, int pollMs) {
        return new LanConfig(Mode.CLIENT, normalizeHost(host, port), port, wsPort, pollMs);
    }

    private static String sys(String key) {
        try {
            String v = System.getProperty(key);
            if (v == null || v.isBlank()) {
                v = System.getenv(toEnvKey(key));
            }
            return v;
        } catch (Exception e) {
            return null;
        }
    }

    private static String toEnvKey(String key) {
        if (key == null) return null;
        return key.toUpperCase().replace('.', '_');
    }

    private static Mode parseMode(String raw) {
        if (raw == null) return Mode.OFF;
        String v = raw.trim().toLowerCase();
        return switch (v) {
            case "host" -> Mode.HOST;
            case "client" -> Mode.CLIENT;
            default -> Mode.OFF;
        };
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    public static String normalizeHost(String raw, int port) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            v = "http://" + v;
        }
        try {
            java.net.URI uri = java.net.URI.create(v);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int p = uri.getPort();
            if (host == null || host.isBlank()) return v;
            if (p == -1 && port > 0) {
                if ("https".equalsIgnoreCase(scheme)) {
                    return scheme + "://" + host;
                }
                return scheme + "://" + host + ":" + port;
            }
            return v;
        } catch (Exception e) {
            return v;
        }
    }

}
