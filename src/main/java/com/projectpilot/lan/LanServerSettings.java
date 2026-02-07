package com.projectpilot.lan;

import javax.net.ssl.SSLContext;

public record LanServerSettings(
        int httpPort,
        int wsPort,
        int httpsPort,
        String mode,
        String publicUrl,
        SSLContext sslContext
) {
    public static LanServerSettings forLan(int httpPort, int wsPort) {
        return new LanServerSettings(httpPort, wsPort, 0, "lan", "", null);
    }
}
