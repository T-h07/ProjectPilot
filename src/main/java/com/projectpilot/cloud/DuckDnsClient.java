package com.projectpilot.cloud;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public final class DuckDnsClient {

    private DuckDnsClient() {}

    public static String update(String domain, String token) {
        String d = normalizeDomain(domain);
        String t = token == null ? "" : token.trim();
        if (d.isBlank()) return "Missing domain";
        if (t.isBlank()) return "Missing token";

        try {
            String url = "https://www.duckdns.org/update?domains=" +
                    URLEncoder.encode(d, StandardCharsets.UTF_8) +
                    "&token=" + URLEncoder.encode(t, StandardCharsets.UTF_8) +
                    "&ip=";

            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(4))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(6))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return "HTTP " + resp.statusCode();
            String body = resp.body() == null ? "" : resp.body().trim();
            return body.isBlank() ? "No response" : body;
        } catch (Exception e) {
            return "Update failed";
        }
    }

    public static String buildUrl(String domain, int port) {
        String d = normalizeDomain(domain);
        if (d.isBlank()) return "";
        String host = d + ".duckdns.org";
        if (port == 80 || port <= 0) return "http://" + host;
        return "http://" + host + ":" + port;
    }

    public static String normalizeDomain(String raw) {
        if (raw == null) return "";
        String v = raw.trim().toLowerCase();
        if (v.isBlank()) return "";
        if (v.endsWith(".duckdns.org")) {
            v = v.substring(0, v.indexOf(".duckdns.org"));
        }
        return v.replaceAll("[^a-z0-9-]", "");
    }
}
