package com.github.smashyalts.udpproxy.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ProxyConfigTest {

    @Test
    void testDefaultValues() {
        ProxyConfig config = new ProxyConfig();
        assertEquals("0.0.0.0", config.getBindAddress());
        assertEquals(19132, config.getBindPort());
        assertEquals("127.0.0.1", config.getRemoteAddress());
        assertEquals(25565, config.getRemotePort());
        assertFalse(config.isUseProxyProtocol());
        assertFalse(config.isReceiveProxyProtocol());
        assertEquals(60, config.getSessionTimeoutSeconds());
        assertEquals(0, config.getMaxSessions());
        assertFalse(config.isDebugMode());
    }

    @Test
    void testLoadFromStream() {
        String yaml = """
                bind-address: "192.168.1.10"
                bind-port: 25565
                remote-address: "10.0.0.1"
                remote-port: 19133
                use-proxy-protocol: true
                receive-proxy-protocol: true
                session-timeout-seconds: 120
                max-sessions: 500
                debug-mode: true
                """;

        ProxyConfig config = new ProxyConfig();
        config.loadFromStream(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals("192.168.1.10", config.getBindAddress());
        assertEquals(25565, config.getBindPort());
        assertEquals("10.0.0.1", config.getRemoteAddress());
        assertEquals(19133, config.getRemotePort());
        assertTrue(config.isUseProxyProtocol());
        assertTrue(config.isReceiveProxyProtocol());
        assertEquals(120, config.getSessionTimeoutSeconds());
        assertEquals(500, config.getMaxSessions());
        assertTrue(config.isDebugMode());
    }

    @Test
    void testPartialConfig() {
        String yaml = """
                bind-port: 8080
                use-proxy-protocol: true
                """;

        ProxyConfig config = new ProxyConfig();
        config.loadFromStream(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        // Changed values
        assertEquals(8080, config.getBindPort());
        assertTrue(config.isUseProxyProtocol());

        // Unchanged defaults
        assertEquals("0.0.0.0", config.getBindAddress());
        assertEquals("127.0.0.1", config.getRemoteAddress());
        assertEquals(25565, config.getRemotePort());
        assertFalse(config.isReceiveProxyProtocol());
        assertEquals(60, config.getSessionTimeoutSeconds());
    }

    @Test
    void testEmptyConfig() {
        ProxyConfig config = new ProxyConfig();
        config.loadFromStream(new ByteArrayInputStream("".getBytes(StandardCharsets.UTF_8)));

        // All defaults should remain
        assertEquals("0.0.0.0", config.getBindAddress());
        assertEquals(19132, config.getBindPort());
    }

    @Test
    void testLoadFromNonexistentFile(@TempDir Path tempDir) throws IOException {
        Path configPath = tempDir.resolve("config.yml");
        ProxyConfig config = ProxyConfig.load(configPath);

        // Should get default values
        assertNotNull(config);
        assertEquals("0.0.0.0", config.getBindAddress());
    }

    @Test
    void testToString() {
        ProxyConfig config = new ProxyConfig();
        String str = config.toString();
        assertTrue(str.contains("bindAddress"));
        assertTrue(str.contains("bindPort"));
        assertTrue(str.contains("remoteAddress"));
        assertTrue(str.contains("useProxyProtocol"));
    }
}
