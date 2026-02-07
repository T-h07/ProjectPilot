package com.projectpilot.util;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;

public final class SslUtil {

    private SslUtil() {}

    public static SSLContext trustAllContextIfEnabled() {
        if (!trustAllEnabled()) return null;
        try {
            TrustManager[] trustAll = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {}

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[0];
                        }
                    }
            };
            SSLContext ctx = SSLContext.getInstance("TLS");
            ctx.init(null, trustAll, new SecureRandom());
            return ctx;
        } catch (Exception e) {
            return null;
        }
    }

    private static boolean trustAllEnabled() {
        String v = System.getProperty("projectpilot.ssl.trustAll");
        if (v == null || v.isBlank()) v = System.getenv("PROJECTPILOT_TRUST_ALL_SSL");
        if (v == null || v.isBlank()) return false;
        v = v.trim().toLowerCase();
        return v.equals("1") || v.equals("true") || v.equals("yes") || v.equals("on");
    }
}
