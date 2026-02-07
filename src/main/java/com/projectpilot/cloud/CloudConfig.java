package com.projectpilot.cloud;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class CloudConfig {

    private static final String KEY_DOMAIN = "duckdns.domain";
    private static final String KEY_TOKEN = "duckdns.token";
    private static final String KEY_AUTO = "duckdns.auto";

    private String duckDomain = "";
    private String duckToken = "";
    private boolean autoUpdate = false;

    public static CloudConfig load() {
        CloudConfig cfg = new CloudConfig();
        Path path = configPath();
        if (!Files.exists(path)) return cfg;

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(path)) {
            props.load(in);
            cfg.duckDomain = props.getProperty(KEY_DOMAIN, "").trim();
            cfg.duckToken = props.getProperty(KEY_TOKEN, "").trim();
            cfg.autoUpdate = Boolean.parseBoolean(props.getProperty(KEY_AUTO, "false").trim());
        } catch (IOException ignored) {
        }
        return cfg;
    }

    public void save() {
        Properties props = new Properties();
        props.setProperty(KEY_DOMAIN, safe(duckDomain));
        props.setProperty(KEY_TOKEN, safe(duckToken));
        props.setProperty(KEY_AUTO, Boolean.toString(autoUpdate));

        Path path = configPath();
        try {
            Files.createDirectories(path.getParent());
            try (OutputStream out = Files.newOutputStream(path)) {
                props.store(out, "ProjectPilot cloud settings");
            }
        } catch (IOException ignored) {
        }
    }

    public String duckDomain() {
        return duckDomain;
    }

    public String duckToken() {
        return duckToken;
    }

    public boolean autoUpdate() {
        return autoUpdate;
    }

    public void setDuckDomain(String duckDomain) {
        this.duckDomain = safe(duckDomain);
    }

    public void setDuckToken(String duckToken) {
        this.duckToken = safe(duckToken);
    }

    public void setAutoUpdate(boolean autoUpdate) {
        this.autoUpdate = autoUpdate;
    }

    public boolean hasDuckCredentials() {
        return !safe(duckDomain).isBlank() && !safe(duckToken).isBlank();
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private static Path configPath() {
        String home = System.getProperty("user.home");
        return Path.of(home, ".projectpilot", "cloud.properties");
    }
}
