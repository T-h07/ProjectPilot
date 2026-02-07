package com.projectpilot.server;

import com.projectpilot.chat.ChatService;
import com.projectpilot.chat.DbChatService;
import com.projectpilot.data.InMemoryStore;
import com.projectpilot.data.db.DbManager;
import com.projectpilot.data.db.DbStore;
import com.projectpilot.data.db.auth.AuthService;
import com.projectpilot.lan.LanServer;
import com.projectpilot.lan.LanServerSettings;
import com.projectpilot.lan.LanSessionRegistry;
import com.projectpilot.lan.LanStoreBroadcaster;
import com.projectpilot.lan.LanWsServer;
import com.projectpilot.lan.RateLimiter;

import javax.net.ssl.SSLContext;
import java.util.concurrent.CountDownLatch;

public final class CloudServerMain {

    public static void main(String[] args) throws Exception {
        ServerConfig config = ServerConfig.fromEnv();
        SSLContext sslContext = config.buildSslContext();

        DbManager db = DbManager.defaultManager();
        db.init();

        InMemoryStore store = new DbStore(db);
        AuthService auth = new AuthService(db);
        ChatService chatService = new DbChatService(db);

        LanSessionRegistry sessions = new LanSessionRegistry();
        LanWsServer wsServer = new LanWsServer(config.wsPort(), sessions, sslContext);
        RateLimiter limiter = config.rateLimitRpm() > 0
                ? new RateLimiter(config.rateLimitRpm(), 60_000L)
                : null;
        LanServerSettings settings = new LanServerSettings(
                config.httpPort(),
                config.wsPort(),
                config.httpsPort(),
                "cloud",
                config.publicUrl(),
                sslContext
        );
        LanServer httpServer = new LanServer(store, auth, chatService, sessions, wsServer, limiter, settings);
        LanStoreBroadcaster broadcaster = new LanStoreBroadcaster(store, wsServer);

        wsServer.start();
        httpServer.start();
        broadcaster.start();

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            try { broadcaster.stop(); } catch (Exception ignored) {}
            try { wsServer.stop(); } catch (Exception ignored) {}
            try { httpServer.stop(); } catch (Exception ignored) {}
            if (store instanceof DbStore ds) ds.shutdown();
        }, "pp-cloud-shutdown"));

        System.out.println("ProjectPilot Cloud Server running");
        System.out.println("HTTP  : " + config.httpPort());
        if (config.httpsEnabled()) {
            System.out.println("HTTPS : " + config.httpsPort());
        }
        System.out.println("WS    : " + config.wsPort());
        System.out.println("DB    : " + db.describe());

        new CountDownLatch(1).await();
    }
}
