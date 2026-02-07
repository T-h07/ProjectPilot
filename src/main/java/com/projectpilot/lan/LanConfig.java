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

    public static LanConfig forCloud(String host, int pollMs) {
        HostInfo info = parseCloudHost(host);
        if (info == null) return new LanConfig(Mode.CLIENT, null, 0, 0, pollMs);
        return new LanConfig(Mode.CLIENT, info.url(), info.port(), info.wsPort(), pollMs);
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

    private static HostInfo parseCloudHost(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String v = raw.trim();
        if (!v.startsWith("http://") && !v.startsWith("https://")) {
            boolean looksLikeIp = v.matches("^\\d{1,3}(?:\\.\\d{1,3}){3}(:\\d+)?(?:/.*)?$");
            boolean hasPort = v.matches(".*:\\d+(/.*)?$");
            boolean localhost = v.startsWith("localhost");
            String scheme = (looksLikeIp || hasPort || localhost) ? "http://" : "https://";
            v = scheme + v;
        }
        try {
            java.net.URI uri = java.net.URI.create(v);
            String scheme = uri.getScheme();
            String host = uri.getHost();
            int port = uri.getPort();
            if (scheme == null || scheme.isBlank()) scheme = "https";
            if (host == null || host.isBlank()) return null;
            if (port == -1) {
                port = "https".equalsIgnoreCase(scheme) ? 443 : 80;
            }
            Integer wsPort = parseWsPort(uri.getQuery());
            if (wsPort == null || wsPort <= 0) {
                if ("https".equalsIgnoreCase(scheme)) {
                    wsPort = port + 1;
                } else {
                    wsPort = port == 80 ? 80 : port + 1;
                }
            }
            String normalized = ("https".equalsIgnoreCase(scheme) && port == 443)
                    || ("http".equalsIgnoreCase(scheme) && port == 80)
                    ? scheme + "://" + host
                    : scheme + "://" + host + ":" + port;
            return new HostInfo(normalized, port, wsPort);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer parseWsPort(String query) {
        if (query == null || query.isBlank()) return null;
        String[] parts = query.split("&");
        for (String part : parts) {
            String[] kv = part.split("=", 2);
            if (kv.length != 2) continue;
            String key = kv[0].trim().toLowerCase();
            if (!key.equals("ws") && !key.equals("wsport")) continue;
            try {
                return Integer.parseInt(kv[1].trim());
            } catch (Exception e) {
                return null;
            }
        }
        return null;
    }

    private record HostInfo(String url, int port, int wsPort) {}
}
