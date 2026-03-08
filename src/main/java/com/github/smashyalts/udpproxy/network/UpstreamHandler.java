package com.github.smashyalts.udpproxy.network;

import com.github.smashyalts.udpproxy.config.ProxyConfig;
import com.github.smashyalts.udpproxy.loadbalancer.Backend;
import com.github.smashyalts.udpproxy.loadbalancer.LoadBalancer;
import com.github.smashyalts.udpproxy.protocol.HAProxyUtil;
import com.github.smashyalts.udpproxy.session.ProxySession;
import com.github.smashyalts.udpproxy.session.SessionManager;
import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Handles incoming UDP packets from clients on the upstream (listening) channel.
 * Creates sessions and forwards packets to a backend server selected by the load balancer.
 * Follows the Geyser pattern for upstream connection handling.
 */
public class UpstreamHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(UpstreamHandler.class);

    private final ProxyConfig config;
    private final SessionManager sessionManager;
    private final LoadBalancer loadBalancer;
    private final EventLoopGroup workerGroup;
    private Channel upstreamChannel;

    public UpstreamHandler(ProxyConfig config, SessionManager sessionManager,
                           LoadBalancer loadBalancer, EventLoopGroup workerGroup) {
        this.config = config;
        this.sessionManager = sessionManager;
        this.loadBalancer = loadBalancer;
        this.workerGroup = workerGroup;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) {
        this.upstreamChannel = ctx.channel();
        logger.info("Upstream channel active on {}", upstreamChannel.localAddress());
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) throws Exception {
        InetSocketAddress senderAddress = packet.sender();
        ByteBuf content = packet.content();

        InetSocketAddress realClientAddress = senderAddress;
        ByteBuf payload = content;

        // If receiving proxy protocol, decode the header to get the real client address
        if (config.isReceiveProxyProtocol() && HAProxyUtil.isProxyProtocolV2(content)) {
            content.retain(); // Retain since we'll be reading from it
            HAProxyUtil.ProxyProtocolResult result = HAProxyUtil.decodeV2Header(content);
            if (result != null && result.getSourceAddress() != null) {
                realClientAddress = result.getSourceAddress();
                payload = result.getPayload();
                if (config.isDebugMode()) {
                    logger.debug("Decoded PROXY protocol: real client = {}", realClientAddress);
                }
            } else {
                // Not a valid PROXY protocol header, use original content
                content.readerIndex(0);
                payload = content;
            }
        }

        if (config.isDebugMode()) {
            logger.debug("Received {} bytes from {} (real: {})", payload.readableBytes(),
                    senderAddress, realClientAddress);
        }

        // Get or create a session for this client
        ProxySession session = sessionManager.getSession(senderAddress);
        if (session == null) {
            session = createSession(senderAddress, realClientAddress);
            if (session == null) {
                return; // Max sessions reached
            }
        }

        // Forward the packet to the backend
        forwardToBackend(session, payload.retain(), realClientAddress);
    }

    private ProxySession createSession(InetSocketAddress clientAddress, InetSocketAddress realClientAddress) {
        // Select a backend using the load balancer
        Backend backend = loadBalancer.selectBackend();
        InetSocketAddress remoteAddress = backend.getSocketAddress();

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioDatagramChannel.class)
                    .option(ChannelOption.AUTO_READ, true)
                    .handler(new DownstreamHandler(clientAddress, upstreamChannel, config));

            // Bind to a random local port (let OS choose)
            Channel downstreamChannel = bootstrap.bind(0).sync().channel();

            // DO NOT connect() the downstream channel!
            // UDP is connectionless - we need to receive responses from the backend
            // which may come from any source address/port
            // The DatagramPacket destination in forwardToBackend handles addressing

            ProxySession session = new ProxySession(clientAddress, realClientAddress, downstreamChannel, backend);

            if (config.isDebugMode()) {
                logger.debug("Selected backend {} for client {}", backend, clientAddress);
            }

            return sessionManager.addSession(clientAddress, session);
        } catch (Exception e) {
            logger.error("Failed to create session for {} -> {}", clientAddress, backend, e);
            return null;
        }
    }

    private void forwardToBackend(ProxySession session, ByteBuf payload, InetSocketAddress realClientAddress) {
        Channel downstream = session.getDownstreamChannel();
        if (!downstream.isActive()) {
            payload.release();
            sessionManager.removeSession(session.getClientAddress());
            return;
        }

        Backend backend = session.getBackend();
        ByteBuf dataToSend;

        // If proxy protocol outbound is enabled and we haven't sent the header yet,
        // prepend the PROXY protocol v2 header to the first packet
        if (config.isUseProxyProtocol() && !session.isProxyProtocolSent()) {
            InetSocketAddress listenAddress = new InetSocketAddress(config.getBindAddress(), config.getBindPort());
            ByteBuf proxyHeader = HAProxyUtil.encodeV2Header(realClientAddress, listenAddress);

            dataToSend = Unpooled.wrappedBuffer(proxyHeader, payload);
            session.setProxyProtocolSent(true);

            if (config.isDebugMode()) {
                logger.debug("Prepended PROXY protocol v2 header for {} -> {}", realClientAddress, backend);
            }
        } else {
            dataToSend = payload;
        }

        InetSocketAddress backendAddr = backend.getSocketAddress();
        if (config.isDebugMode()) {
            logger.debug("Sending {} bytes to backend {} from local port {}", 
                dataToSend.readableBytes(), backendAddr, downstream.localAddress());
        }
        
        downstream.writeAndFlush(new DatagramPacket(dataToSend, backendAddr)).addListener(future -> {
            if (config.isDebugMode()) {
                if (future.isSuccess()) {
                    logger.debug("Successfully sent packet to backend {}", backendAddr);
                } else {
                    logger.error("Failed to send packet to backend {}: {}", backendAddr, future.cause().getMessage());
                }
            }
        });

        if (config.isDebugMode()) {
            logger.debug("Forwarded {} bytes to backend {}", dataToSend.readableBytes(), backend);
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Error in upstream handler", cause);
    }
}
