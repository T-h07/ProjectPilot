package com.projectpilot.lan;

import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;

public final class LanDiscovery {

    public static final int DEFAULT_PORT = 8092;
    private static final String DISCOVER = "PP_DISCOVER";
    private static final String RESPONSE = "PP_HOST";

    private LanDiscovery() {}

    public interface Responder {
        void stop();
    }

    public static Responder startResponder(int httpPort, int wsPort) {
        return startResponder(DEFAULT_PORT, httpPort, wsPort, defaultName());
    }

    public static Responder startResponder(int discoveryPort, int httpPort, int wsPort, String name) {
        DatagramSocket socket;
        try {
            socket = new DatagramSocket(new InetSocketAddress("0.0.0.0", discoveryPort));
            socket.setBroadcast(true);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to bind discovery port: " + discoveryPort, e);
        }

        AtomicBoolean running = new AtomicBoolean(true);
        Thread t = new Thread(() -> runResponder(socket, running, httpPort, wsPort, name), "pp-lan-discovery");
        t.setDaemon(true);
        t.start();

        return () -> {
            running.set(false);
            socket.close();
        };
    }

    public static List<LanHost> discover(int timeoutMs) {
        return discover(DEFAULT_PORT, timeoutMs);
    }

    public static List<LanHost> discover(int discoveryPort, int timeoutMs) {
        Map<String, LanHost> hosts = new LinkedHashMap<>();
        DatagramSocket socket = null;
        try {
            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setSoTimeout(200);

            byte[] data = DISCOVER.getBytes(StandardCharsets.UTF_8);
            DatagramPacket packet = new DatagramPacket(data, data.length);

            // global broadcast
            packet.setAddress(InetAddress.getByName("255.255.255.255"));
            packet.setPort(discoveryPort);
            socket.send(packet);

            // interface broadcasts
            Enumeration<NetworkInterface> ifaces = NetworkInterface.getNetworkInterfaces();
            while (ifaces.hasMoreElements()) {
                NetworkInterface ni = ifaces.nextElement();
                if (!ni.isUp() || ni.isLoopback()) continue;
                for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                    InetAddress bcast = ia.getBroadcast();
                    if (bcast == null) continue;
                    try {
                        packet.setAddress(bcast);
                        packet.setPort(discoveryPort);
                        socket.send(packet);
                    } catch (Exception e) { com.projectpilot.util.AppLog.warn("lan-discovery", "Failed to send discovery packet to " + bcast + ": " + (e == null ? "" : e.getMessage())); }
                }
            }

            long end = System.currentTimeMillis() + Math.max(300, timeoutMs);
            while (System.currentTimeMillis() < end) {
                try {
                    byte[] buf = new byte[512];
                    DatagramPacket resp = new DatagramPacket(buf, buf.length);
                    socket.receive(resp);
                    LanHost host = parseResponse(resp);
                    if (host != null) {
                        String key = host.address() + ":" + host.port();
                        hosts.putIfAbsent(key, host);
                    }
                } catch (SocketTimeoutException ignored) {
                }
            }
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("lan-discovery", "Discovery failed: " + (e == null ? "" : e.getMessage()));
        } finally {
            if (socket != null) socket.close();
        }
        return new ArrayList<>(hosts.values());
    }

    private static void runResponder(DatagramSocket socket, AtomicBoolean running, int httpPort, int wsPort, String name) {
        byte[] buf = new byte[512];
        while (running.get()) {
            try {
                DatagramPacket packet = new DatagramPacket(buf, buf.length);
                socket.receive(packet);
                String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
                if (!DISCOVER.equalsIgnoreCase(msg)) continue;

                String payload = RESPONSE + "|" + safe(name) + "|" + httpPort + "|" + wsPort;
                byte[] out = payload.getBytes(StandardCharsets.UTF_8);
                DatagramPacket reply = new DatagramPacket(out, out.length, packet.getAddress(), packet.getPort());
                socket.send(reply);
            } catch (SocketException se) {
                break;
            } catch (Exception e) {
                com.projectpilot.util.AppLog.warn("lan-discovery", "Responder loop error: " + (e == null ? "" : e.getMessage()));
            }
        }
    }

    private static LanHost parseResponse(DatagramPacket packet) {
        if (packet == null) return null;
        String msg = new String(packet.getData(), 0, packet.getLength(), StandardCharsets.UTF_8).trim();
        if (!msg.startsWith(RESPONSE)) return null;
        String[] parts = msg.split("\\|");
        if (parts.length < 4) return null;

        String name = parts[1];
        int port = parseInt(parts[2], 8090);
        int wsPort = parseInt(parts[3], port + 1);
        String address = packet.getAddress().getHostAddress();
        return new LanHost(name, address, port, wsPort);
    }

    private static String defaultName() {
        String n = System.getenv("COMPUTERNAME");
        if (n != null && !n.isBlank()) return n.trim();
        try {
            String host = InetAddress.getLocalHost().getHostName();
            if (host != null && !host.isBlank()) return host.trim();
        } catch (Exception e) { com.projectpilot.util.AppLog.warn("lan-discovery", "defaultName lookup failed: " + (e == null ? "" : e.getMessage()));
        }
        return "ProjectPilot Host";
    }

    private static String safe(String v) {
        return v == null ? "" : v.trim();
    }

    private static int parseInt(String raw, int fallback) {
        if (raw == null || raw.isBlank()) return fallback;
        try {
            return Integer.parseInt(raw.trim());
        } catch (Exception e) {
            return fallback;
        }
    }
}
