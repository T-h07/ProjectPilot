package com.projectpilot.lan.dto;

public record SyncResponse(
        boolean ok,
        String message
) {
    public static SyncResponse ok() { return new SyncResponse(true, ""); }
    public static SyncResponse error(String msg) { return new SyncResponse(false, msg); }
}
