package com.projectpilot.data.db;

public final class DbException extends RuntimeException {
    public DbException(String message) {
        super(message);
    }

    public DbException(String message, Throwable cause) {
        super(message, cause);
    }
}
