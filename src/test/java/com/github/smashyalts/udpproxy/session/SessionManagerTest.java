package com.github.smashyalts.udpproxy.session;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.DatagramPacket;
import io.netty.channel.socket.nio.NioDatagramChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

class SessionManagerTest {

    private SessionManager sessionManager;
    private NioEventLoopGroup group;

    @BeforeEach
    void setUp() {
        sessionManager = new SessionManager(60, 0);
        group = new NioEventLoopGroup(1);
    }

    @AfterEach
    void tearDown() {
        sessionManager.shutdown();
        group.shutdownGracefully().syncUninterruptibly();
    }

    private Channel createDummyChannel() throws Exception {
        Bootstrap bootstrap = new Bootstrap();
        bootstrap.group(group)
                .channel(NioDatagramChannel.class)
                .handler(new ChannelInitializer<DatagramChannel>() {
                    @Override
                    protected void initChannel(DatagramChannel ch) {
                        ch.pipeline().addLast(new SimpleChannelInboundHandler<DatagramPacket>() {
                            @Override
                            protected void channelRead0(ChannelHandlerContext ctx, DatagramPacket msg) {
                            }
                        });
                    }
                });
        return bootstrap.bind(0).sync().channel();
    }

    @Test
    void testAddAndGetSession() throws Exception {
        InetSocketAddress clientAddr = new InetSocketAddress("192.168.1.1", 12345);
        Channel channel = createDummyChannel();
        ProxySession session = new ProxySession(clientAddr, clientAddr, channel);

        ProxySession added = sessionManager.addSession(clientAddr, session);
        assertNotNull(added);
        assertEquals(1, sessionManager.getSessionCount());

        ProxySession retrieved = sessionManager.getSession(clientAddr);
        assertNotNull(retrieved);
        assertEquals(clientAddr, retrieved.getClientAddress());
    }

    @Test
    void testRemoveSession() throws Exception {
        InetSocketAddress clientAddr = new InetSocketAddress("192.168.1.1", 12345);
        Channel channel = createDummyChannel();
        ProxySession session = new ProxySession(clientAddr, clientAddr, channel);

        sessionManager.addSession(clientAddr, session);
        assertEquals(1, sessionManager.getSessionCount());

        sessionManager.removeSession(clientAddr);
        assertEquals(0, sessionManager.getSessionCount());
        assertNull(sessionManager.getSession(clientAddr));
    }

    @Test
    void testMaxSessions() throws Exception {
        SessionManager limitedManager = new SessionManager(60, 2);
        try {
            InetSocketAddress addr1 = new InetSocketAddress("192.168.1.1", 1001);
            InetSocketAddress addr2 = new InetSocketAddress("192.168.1.2", 1002);
            InetSocketAddress addr3 = new InetSocketAddress("192.168.1.3", 1003);

            Channel ch1 = createDummyChannel();
            Channel ch2 = createDummyChannel();
            Channel ch3 = createDummyChannel();

            assertNotNull(limitedManager.addSession(addr1, new ProxySession(addr1, addr1, ch1)));
            assertNotNull(limitedManager.addSession(addr2, new ProxySession(addr2, addr2, ch2)));
            assertNull(limitedManager.addSession(addr3, new ProxySession(addr3, addr3, ch3)));

            assertEquals(2, limitedManager.getSessionCount());

            ch3.close().syncUninterruptibly();
        } finally {
            limitedManager.shutdown();
        }
    }

    @Test
    void testSessionExpiry() throws Exception {
        SessionManager shortTimeoutManager = new SessionManager(1, 0);
        try {
            InetSocketAddress clientAddr = new InetSocketAddress("192.168.1.1", 12345);
            Channel channel = createDummyChannel();
            ProxySession session = new ProxySession(clientAddr, clientAddr, channel);

            shortTimeoutManager.addSession(clientAddr, session);
            assertNotNull(shortTimeoutManager.getSession(clientAddr));

            // Wait for session to expire
            Thread.sleep(1500);

            assertNull(shortTimeoutManager.getSession(clientAddr));
        } finally {
            shortTimeoutManager.shutdown();
        }
    }

    @Test
    void testHasSession() throws Exception {
        InetSocketAddress clientAddr = new InetSocketAddress("192.168.1.1", 12345);
        Channel channel = createDummyChannel();
        ProxySession session = new ProxySession(clientAddr, clientAddr, channel);

        assertFalse(sessionManager.hasSession(clientAddr));
        sessionManager.addSession(clientAddr, session);
        assertTrue(sessionManager.hasSession(clientAddr));
    }

    @Test
    void testMultipleSessions() throws Exception {
        for (int i = 0; i < 5; i++) {
            InetSocketAddress addr = new InetSocketAddress("192.168.1." + (i + 1), 10000 + i);
            Channel channel = createDummyChannel();
            sessionManager.addSession(addr, new ProxySession(addr, addr, channel));
        }

        assertEquals(5, sessionManager.getSessionCount());

        InetSocketAddress addr3 = new InetSocketAddress("192.168.1.3", 10002);
        sessionManager.removeSession(addr3);
        assertEquals(4, sessionManager.getSessionCount());
    }
}
