package com.github.smashyalts.udpproxy.network;

import com.github.smashyalts.udpproxy.config.ProxyConfig;
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
 * Creates sessions and forwards packets to the backend server.
 * Follows the Geyser pattern for upstream connection handling.
 */
public class UpstreamHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(UpstreamHandler.class);

    private final ProxyConfig config;
    private final SessionManager sessionManager;
    private final EventLoopGroup workerGroup;
    private Channel upstreamChannel;

    public UpstreamHandler(ProxyConfig config, SessionManager sessionManager, EventLoopGroup workerGroup) {
        this.config = config;
        this.sessionManager = sessionManager;
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
        InetSocketAddress remoteAddress = new InetSocketAddress(config.getRemoteAddress(), config.getRemotePort());

        try {
            Bootstrap bootstrap = new Bootstrap();
            bootstrap.group(workerGroup)
                    .channel(NioDatagramChannel.class)
                    .handler(new DownstreamHandler(clientAddress, upstreamChannel, config));

            Channel downstreamChannel = bootstrap.bind(0).sync().channel();

            // Connect the downstream channel to the remote address for easy sending
            downstreamChannel.connect(remoteAddress).sync();

            ProxySession session = new ProxySession(clientAddress, realClientAddress, downstreamChannel);
            return sessionManager.addSession(clientAddress, session);
        } catch (Exception e) {
            logger.error("Failed to create session for {}", clientAddress, e);
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

        ByteBuf dataToSend;

        // If proxy protocol outbound is enabled and we haven't sent the header yet,
        // prepend the PROXY protocol v2 header to the first packet
        if (config.isUseProxyProtocol() && !session.isProxyProtocolSent()) {
            InetSocketAddress listenAddress = new InetSocketAddress(config.getBindAddress(), config.getBindPort());
            ByteBuf proxyHeader = HAProxyUtil.encodeV2Header(realClientAddress, listenAddress);

            dataToSend = Unpooled.wrappedBuffer(proxyHeader, payload);
            session.setProxyProtocolSent(true);

            if (config.isDebugMode()) {
                logger.debug("Prepended PROXY protocol v2 header for {} -> backend", realClientAddress);
            }
        } else {
            dataToSend = payload;
        }

        downstream.writeAndFlush(new DatagramPacket(dataToSend,
                new InetSocketAddress(config.getRemoteAddress(), config.getRemotePort())));

        if (config.isDebugMode()) {
            logger.debug("Forwarded {} bytes to backend {}:{}",
                    dataToSend.readableBytes(), config.getRemoteAddress(), config.getRemotePort());
        }
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Error in upstream handler", cause);
    }
}
