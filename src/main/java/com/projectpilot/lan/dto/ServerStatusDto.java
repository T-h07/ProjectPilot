package com.projectpilot.lan.dto;

public record ServerStatusDto(
        String status,
        long startedAt,
        long uptimeMs,
        String publicUrl,
        int httpPort,
        int wsPort,
        int connections,
        String mode
) {
    public static ServerStatusDto ok(long startedAt, long uptimeMs, String publicUrl,
                                     int httpPort, int wsPort, int connections, String mode) {
        return new ServerStatusDto("ok", startedAt, uptimeMs, publicUrl, httpPort, wsPort, connections, mode);
    }
}
