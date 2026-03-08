package com.github.smashyalts.udpproxy.network;

import com.github.smashyalts.udpproxy.config.ProxyConfig;
import com.github.smashyalts.udpproxy.loadbalancer.LoadBalancer;
import com.github.smashyalts.udpproxy.loadbalancer.LoadBalancerFactory;
import com.github.smashyalts.udpproxy.session.SessionManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * The main UDP proxy server. Binds to the configured address and port,
 * manages the Netty event loop groups, and coordinates the upstream/downstream
 * packet flow with load balancing across multiple backends.
 * Follows Geyser's server architecture patterns.
 */
public class ProxyServer {

    private static final Logger logger = LoggerFactory.getLogger(ProxyServer.class);

    private final ProxyConfig config;
    private final SessionManager sessionManager;
    private final LoadBalancer loadBalancer;
    private EventLoopGroup bossGroup;
    private EventLoopGroup workerGroup;
    private Channel serverChannel;

    public ProxyServer(ProxyConfig config) {
        this.config = config;
        this.sessionManager = new SessionManager(config.getSessionTimeoutSeconds(), config.getMaxSessions());
        this.loadBalancer = LoadBalancerFactory.create(
                config.getLoadBalancingStrategy(), config.getResolvedBackends());
        this.sessionManager.setLoadBalancer(this.loadBalancer);
    }

    /**
     * Start the proxy server and begin listening for UDP packets.
     */
    public void start() throws Exception {
        bossGroup = new NioEventLoopGroup(1);
        workerGroup = new NioEventLoopGroup();

        InetSocketAddress bindAddress = new InetSocketAddress(config.getBindAddress(), config.getBindPort());

        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(bossGroup)
                .channel(NioDatagramChannel.class)
                .option(ChannelOption.SO_BROADCAST, true)
                .option(ChannelOption.SO_REUSEADDR, true)
                .handler(new UpstreamHandler(config, sessionManager, loadBalancer, workerGroup));

        serverChannel = bootstrap.bind(bindAddress).sync().channel();

        logger.info("UDP Proxy server started on {}:{}", config.getBindAddress(), config.getBindPort());
        logger.info("Load balancing strategy: {}", config.getLoadBalancingStrategy());
        logger.info("Proxy Protocol outbound: {}", config.isUseProxyProtocol() ? "ENABLED" : "DISABLED");
        logger.info("Proxy Protocol inbound: {}", config.isReceiveProxyProtocol() ? "ENABLED" : "DISABLED");
        logger.info("Session timeout: {}s", config.getSessionTimeoutSeconds());
        if (config.getMaxSessions() > 0) {
            logger.info("Max sessions: {}", config.getMaxSessions());
        }
        if (config.isDebugMode()) {
            logger.info("Debug mode: ENABLED");
        }
    }

    /**
     * Gracefully shut down the proxy server.
     */
    public void shutdown() {
        logger.info("Shutting down proxy server...");

        sessionManager.shutdown();

        if (serverChannel != null) {
            serverChannel.close().syncUninterruptibly();
        }
        if (bossGroup != null) {
            bossGroup.shutdownGracefully().syncUninterruptibly();
        }
        if (workerGroup != null) {
            workerGroup.shutdownGracefully().syncUninterruptibly();
        }

        logger.info("Proxy server shut down complete");
    }

    public SessionManager getSessionManager() {
        return sessionManager;
    }

    public LoadBalancer getLoadBalancer() {
        return loadBalancer;
    }

    public Channel getServerChannel() {
        return serverChannel;
    }
}
