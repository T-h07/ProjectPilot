package com.projectpilot.lan;

public record LanHost(String name, String address, int port, int wsPort) {
    public String baseUrl() {
        return "http://" + address + ":" + port;
    }

    @Override
    public String toString() {
        String label = name == null || name.isBlank() ? "Host" : name;
        return label + " (" + address + ":" + port + ")";
    }
}
