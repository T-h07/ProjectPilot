package com.projectpilot.lan;

public record LanConfig(Mode mode, String host, int port, int pollMs) {

    public enum Mode { OFF, HOST, CLIENT }

    public static LanConfig fromSystem() {
        String modeRaw = sys("pp.lan.mode");
        String hostRaw = sys("pp.lan.host");
        String portRaw = sys("pp.lan.port");
        String pollRaw = sys("pp.lan.pollMs");

        Mode mode = Mode.OFF;
        if (modeRaw != null && !modeRaw.isBlank()) {
            mode = parseMode(modeRaw);
        } else if (hostRaw != null && !hostRaw.isBlank()) {
            mode = Mode.CLIENT;
        }

        int port = parseInt(portRaw, 8090);
        int poll = parseInt(pollRaw, 2000);
        String host = normalizeHost(hostRaw);

        return new LanConfig(mode, host, port, poll);
    }

    public boolean isHost() { return mode == Mode.HOST; }
    public boolean isClient() { return mode == Mode.CLIENT; }

    public String baseUrl() {
        if (host == null || host.isBlank()) return null;
        return host;
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

    private static String normalizeHost(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            v = "http://" + v;
        }
        return v;
    }
}
