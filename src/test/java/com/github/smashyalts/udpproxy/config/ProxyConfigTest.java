package com.github.smashyalts.udpproxy.config;

import com.github.smashyalts.udpproxy.loadbalancer.Backend;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

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
        assertEquals("roundrobin", config.getLoadBalancingStrategy());
        assertTrue(config.getBackends().isEmpty());
    }

    @Test
    void testDefaultResolvedBackendsFallback() {
        ProxyConfig config = new ProxyConfig();
        List<Backend> backends = config.getResolvedBackends();
        assertEquals(1, backends.size());
        assertEquals("127.0.0.1", backends.get(0).getAddress());
        assertEquals(25565, backends.get(0).getPort());
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
    void testLoadBalancingConfig() {
        String yaml = """
                load-balancing-strategy: "leastconn"
                backends:
                  - address: "10.0.0.1"
                    port: 25565
                    weight: 2
                  - address: "10.0.0.2"
                    port: 25566
                  - address: "10.0.0.3"
                    port: 25567
                    weight: 3
                """;

        ProxyConfig config = new ProxyConfig();
        config.loadFromStream(new ByteArrayInputStream(yaml.getBytes(StandardCharsets.UTF_8)));

        assertEquals("leastconn", config.getLoadBalancingStrategy());
        List<Backend> backends = config.getResolvedBackends();
        assertEquals(3, backends.size());

        assertEquals("10.0.0.1", backends.get(0).getAddress());
        assertEquals(25565, backends.get(0).getPort());
        assertEquals(2, backends.get(0).getWeight());

        assertEquals("10.0.0.2", backends.get(1).getAddress());
        assertEquals(25566, backends.get(1).getPort());
        assertEquals(1, backends.get(1).getWeight()); // default weight

        assertEquals("10.0.0.3", backends.get(2).getAddress());
        assertEquals(25567, backends.get(2).getPort());
        assertEquals(3, backends.get(2).getWeight());
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
        assertEquals("roundrobin", config.getLoadBalancingStrategy());
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
        assertTrue(str.contains("loadBalancingStrategy"));
    }
}
