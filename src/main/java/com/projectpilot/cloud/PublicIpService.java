package com.projectpilot.cloud;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

public final class PublicIpService {

    private PublicIpService() {}

    public static String fetch() {
        String ip = fetchFrom("https://api.ipify.org");
        if (!ip.isBlank()) return ip;
        ip = fetchFrom("https://ifconfig.me/ip");
        if (!ip.isBlank()) return ip;
        return "";
    }

    private static String fetchFrom(String url) {
        try {
            HttpClient client = HttpClient.newBuilder()
                    .connectTimeout(Duration.ofSeconds(3))
                    .build();
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .timeout(Duration.ofSeconds(5))
                    .GET()
                    .build();
            HttpResponse<String> resp = client.send(req, HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return "";
            String body = resp.body() == null ? "" : resp.body().trim();
            if (body.isBlank()) return "";
            return body;
        } catch (Exception e) {
            return "";
        }
    }
}
