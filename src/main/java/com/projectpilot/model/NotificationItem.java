package com.projectpilot.model;

import java.time.LocalDateTime;
import java.util.Objects;

public record NotificationItem(
        String key,
        LocalDateTime at,
        String title,
        String detail,
        boolean urgent,
        boolean read
) {
    public NotificationItem {
        Objects.requireNonNull(key);
        Objects.requireNonNull(at);
        Objects.requireNonNull(title);
        Objects.requireNonNull(detail);
    }

    public NotificationItem withRead(boolean r) {
        return new NotificationItem(key, at, title, detail, urgent, r);
    }
}
