package com.github.smashyalts.udpproxy.session;

import com.github.smashyalts.udpproxy.loadbalancer.Backend;
import io.netty.channel.Channel;

import java.net.InetSocketAddress;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Represents an active proxy session mapping a client to a backend connection.
 * Each session has its own downstream channel for communicating with the backend.
 */
public class ProxySession {

    private final InetSocketAddress clientAddress;
    private final InetSocketAddress realClientAddress;
    private final Channel downstreamChannel;
    private final Backend backend;
    private final AtomicLong lastActivity;
    private volatile boolean proxyProtocolSent;

    /**
     * Create a new proxy session.
     *
     * @param clientAddress     the address the proxy received the packet from
     * @param realClientAddress the real client address (may differ if PROXY protocol was received)
     * @param downstreamChannel the channel used to communicate with the backend
     * @param backend           the backend server this session is connected to
     */
    public ProxySession(InetSocketAddress clientAddress, InetSocketAddress realClientAddress,
                        Channel downstreamChannel, Backend backend) {
        this.clientAddress = clientAddress;
        this.realClientAddress = realClientAddress;
        this.downstreamChannel = downstreamChannel;
        this.backend = backend;
        this.lastActivity = new AtomicLong(System.currentTimeMillis());
        this.proxyProtocolSent = false;
    }

    public InetSocketAddress getClientAddress() {
        return clientAddress;
    }

    public InetSocketAddress getRealClientAddress() {
        return realClientAddress;
    }

    public Channel getDownstreamChannel() {
        return downstreamChannel;
    }

    public Backend getBackend() {
        return backend;
    }

    public long getLastActivity() {
        return lastActivity.get();
    }

    public void touch() {
        lastActivity.set(System.currentTimeMillis());
    }

    public boolean isProxyProtocolSent() {
        return proxyProtocolSent;
    }

    public void setProxyProtocolSent(boolean sent) {
        this.proxyProtocolSent = sent;
    }

    public boolean isExpired(int timeoutSeconds) {
        return (System.currentTimeMillis() - lastActivity.get()) > (timeoutSeconds * 1000L);
    }

    public void close() {
        if (downstreamChannel.isOpen()) {
            downstreamChannel.close();
        }
    }

    @Override
    public String toString() {
        return "ProxySession{" +
                "client=" + clientAddress +
                ", realClient=" + realClientAddress +
                ", backend=" + backend +
                ", active=" + downstreamChannel.isActive() +
                '}';
    }
}
