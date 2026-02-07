package com.projectpilot.lan;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public final class RateLimiter {

    private final int maxRequests;
    private final long windowMillis;
    private final Map<String, Counter> counters = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequests, long windowMillis) {
        if (maxRequests <= 0) throw new IllegalArgumentException("maxRequests must be > 0");
        if (windowMillis <= 0) throw new IllegalArgumentException("windowMillis must be > 0");
        this.maxRequests = maxRequests;
        this.windowMillis = windowMillis;
    }

    public boolean allow(String key) {
        if (key == null || key.isBlank()) return true;
        long now = System.currentTimeMillis();
        Counter c = counters.computeIfAbsent(key, k -> new Counter(now, 0));
        synchronized (c) {
            if (now - c.windowStart >= windowMillis) {
                c.windowStart = now;
                c.count = 0;
            }
            c.count++;
            return c.count <= maxRequests;
        }
    }

    private static final class Counter {
        private long windowStart;
        private int count;

        private Counter(long windowStart, int count) {
            this.windowStart = windowStart;
            this.count = count;
        }
    }
}
