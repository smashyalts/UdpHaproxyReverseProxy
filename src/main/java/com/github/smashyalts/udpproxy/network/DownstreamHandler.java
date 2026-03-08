package com.github.smashyalts.udpproxy.network;

import com.github.smashyalts.udpproxy.config.ProxyConfig;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.socket.DatagramPacket;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;

/**
 * Handles incoming UDP packets from the backend server on a downstream (per-session) channel.
 * Forwards response packets back to the original client.
 * Follows the Geyser pattern for downstream connection handling.
 */
public class DownstreamHandler extends SimpleChannelInboundHandler<DatagramPacket> {

    private static final Logger logger = LoggerFactory.getLogger(DownstreamHandler.class);

    private final InetSocketAddress clientAddress;
    private final Channel upstreamChannel;
    private final ProxyConfig config;

    /**
     * Create a downstream handler that forwards backend responses to the client.
     *
     * @param clientAddress   the original client address to send responses back to
     * @param upstreamChannel the upstream listening channel to send through
     * @param config          proxy configuration
     */
    public DownstreamHandler(InetSocketAddress clientAddress, Channel upstreamChannel, ProxyConfig config) {
        this.clientAddress = clientAddress;
        this.upstreamChannel = upstreamChannel;
        this.config = config;
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket packet) {
        if (config.isDebugMode()) {
            logger.debug("Received {} bytes from backend, forwarding to client {}",
                    packet.content().readableBytes(), clientAddress);
        }

        // Forward the backend response back to the client through the upstream channel
        upstreamChannel.writeAndFlush(new DatagramPacket(
                packet.content().retain(),
                clientAddress
        ));
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
        logger.error("Error in downstream handler for client {}", clientAddress, cause);
    }
}
