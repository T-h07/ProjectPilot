package com.projectpilot.lan.dto;

public record WsMessage(String type) {
    public static WsMessage refresh() { return new WsMessage("refresh"); }
}
