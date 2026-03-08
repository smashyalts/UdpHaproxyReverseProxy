package com.github.smashyalts.udpproxy;

import com.github.smashyalts.udpproxy.config.ProxyConfig;
import com.github.smashyalts.udpproxy.network.ProxyServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Main entry point for the UDP HAProxy Reverse Proxy.
 * <p>
 * This proxy forwards UDP packets between clients and a backend server,
 * with optional HAProxy PROXY protocol v2 support for both inbound and outbound connections.
 * The design follows Geyser's (Minecraft Bedrock to Java) architecture patterns.
 * <p>
 * Features:
 * <ul>
 *   <li>Configurable bind and remote addresses via YAML config</li>
 *   <li>HAProxy PROXY protocol v2 encoding (outbound to backend)</li>
 *   <li>HAProxy PROXY protocol v2 decoding (inbound from frontend proxy)</li>
 *   <li>Session management with automatic timeout cleanup</li>
 *   <li>Configurable max sessions limit</li>
 *   <li>Debug logging mode</li>
 * </ul>
 */
public class UdpHaproxyProxy {

    private static final Logger logger = LoggerFactory.getLogger(UdpHaproxyProxy.class);

    private ProxyServer proxyServer;

    public static void main(String[] args) {
        UdpHaproxyProxy proxy = new UdpHaproxyProxy();
        proxy.run(args);
    }

    public void run(String[] args) {
        logger.info("Starting UDP HAProxy Reverse Proxy...");

        try {
            // Determine config file path
            String configFile = args.length > 0 ? args[0] : "config.yml";
            Path configPath = Paths.get(configFile);

            // Load configuration
            ProxyConfig config = ProxyConfig.load(configPath);
            logger.info("Configuration loaded: {}", config);

            // Create and start the proxy server
            proxyServer = new ProxyServer(config);
            proxyServer.start();

            // Register shutdown hook for graceful shutdown
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                logger.info("Shutdown signal received");
                if (proxyServer != null) {
                    proxyServer.shutdown();
                }
            }, "shutdown-hook"));

            // Keep the main thread alive
            proxyServer.getServerChannel().closeFuture().sync();

        } catch (Exception e) {
            logger.error("Failed to start proxy server", e);
            System.exit(1);
        }
    }
}
