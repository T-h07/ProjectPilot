package com.projectpilot.cloud;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public final class DuckDnsAutoUpdater {

    private static final DuckDnsAutoUpdater INSTANCE = new DuckDnsAutoUpdater();

    private ScheduledExecutorService exec;
    private String domain = "";
    private String token = "";

    private DuckDnsAutoUpdater() {}

    public static DuckDnsAutoUpdater instance() {
        return INSTANCE;
    }

    public synchronized void start(String domain, String token) {
        String d = DuckDnsClient.normalizeDomain(domain);
        String t = token == null ? "" : token.trim();
        if (d.isBlank() || t.isBlank()) {
            stop();
            return;
        }
        if (exec != null && d.equals(this.domain) && t.equals(this.token)) {
            return;
        }
        stop();
        this.domain = d;
        this.token = t;
        exec = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread th = new Thread(r, "pp-duckdns-auto");
            th.setDaemon(true);
            return th;
        });
        exec.scheduleAtFixedRate(() -> DuckDnsClient.update(this.domain, this.token), 0, 5, TimeUnit.MINUTES);
    }

    public synchronized void stop() {
        if (exec != null) {
            exec.shutdownNow();
            exec = null;
        }
    }

    public synchronized boolean isRunning() {
        return exec != null;
    }
}
