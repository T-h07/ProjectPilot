package com.projectpilot.server;

import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManagerFactory;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.SecureRandom;

public record ServerConfig(
        int httpPort,
        int wsPort,
        int rateLimitRpm,
        int httpsPort,
        String keyStorePath,
        String keyStorePassword,
        String keyStoreType,
        String publicUrl
) {

    public static ServerConfig fromEnv() {
        int http = readInt("projectpilot.http.port", "PROJECTPILOT_HTTP_PORT", 8090);
        int ws = readInt("projectpilot.ws.port", "PROJECTPILOT_WS_PORT", http + 1);
        int rpm = readInt("projectpilot.rateLimit.rpm", "PROJECTPILOT_RATE_LIMIT_RPM", 0);
        int https = readInt("projectpilot.https.port", "PROJECTPILOT_HTTPS_PORT", 0);
        String ksPath = readString("projectpilot.keystore.path", "PROJECTPILOT_KEYSTORE_PATH", "");
        String ksPass = readString("projectpilot.keystore.password", "PROJECTPILOT_KEYSTORE_PASSWORD", "");
        String ksType = readString("projectpilot.keystore.type", "PROJECTPILOT_KEYSTORE_TYPE", "PKCS12");
        String publicUrl = readString("projectpilot.public.url", "PROJECTPILOT_PUBLIC_URL", "");
        return new ServerConfig(http, ws, rpm, https, ksPath, ksPass, ksType, publicUrl);
    }

    public boolean httpsEnabled() {
        return httpsPort > 0 && keyStorePath != null && !keyStorePath.isBlank();
    }

    public SSLContext buildSslContext() {
        if (!httpsEnabled()) return null;
        try {
            String type = keyStoreType == null || keyStoreType.isBlank() ? "PKCS12" : keyStoreType.trim();
            KeyStore ks = KeyStore.getInstance(type);
            char[] pass = keyStorePassword == null ? new char[0] : keyStorePassword.toCharArray();
            try (InputStream in = Files.newInputStream(Path.of(keyStorePath))) {
                ks.load(in, pass);
            }

            KeyManagerFactory kmf = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
            kmf.init(ks, pass);
            TrustManagerFactory tmf = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            tmf.init(ks);

            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(kmf.getKeyManagers(), tmf.getTrustManagers(), new SecureRandom());
            return ctx;
        } catch (Exception e) {
            throw new IllegalStateException("Failed to load TLS keystore: " + e.getMessage(), e);
        }
    }

    private static int readInt(String propKey, String envKey, int fallback) {
        String v = System.getProperty(propKey);
        if (v == null || v.isBlank()) v = System.getenv(envKey);
        if (v == null || v.isBlank()) return fallback;
        try {
            return Integer.parseInt(v.trim());
        } catch (Exception e) {
            return fallback;
        }
    }

    private static String readString(String propKey, String envKey, String fallback) {
        String v = System.getProperty(propKey);
        if (v == null || v.isBlank()) v = System.getenv(envKey);
        if (v == null || v.isBlank()) return fallback;
        return v.trim();
    }
}
