package com.github.smashyalts.udpproxy.loadbalancer;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class LoadBalancerTest {

    private final Backend backend1 = new Backend("10.0.0.1", 25565);
    private final Backend backend2 = new Backend("10.0.0.2", 25565);
    private final Backend backend3 = new Backend("10.0.0.3", 25565);

    // --- Backend Tests ---

    @Test
    void testBackendEquality() {
        Backend a = new Backend("10.0.0.1", 25565);
        Backend b = new Backend("10.0.0.1", 25565);
        Backend c = new Backend("10.0.0.1", 25566);

        assertEquals(a, b);
        assertNotEquals(a, c);
        assertEquals(a.hashCode(), b.hashCode());
    }

    @Test
    void testBackendDefaultWeight() {
        Backend b = new Backend("10.0.0.1", 25565);
        assertEquals(1, b.getWeight());
    }

    @Test
    void testBackendMinWeight() {
        Backend b = new Backend("10.0.0.1", 25565, 0);
        assertEquals(1, b.getWeight()); // Minimum weight is 1
    }

    @Test
    void testBackendSocketAddress() {
        Backend b = new Backend("10.0.0.1", 25565);
        assertEquals("10.0.0.1", b.getSocketAddress().getHostString());
        assertEquals(25565, b.getSocketAddress().getPort());
    }

    @Test
    void testBackendToString() {
        Backend b1 = new Backend("10.0.0.1", 25565);
        assertEquals("10.0.0.1:25565", b1.toString());

        Backend b2 = new Backend("10.0.0.1", 25565, 3);
        assertTrue(b2.toString().contains("weight=3"));
    }

    // --- RoundRobin Tests ---

    @Test
    void testRoundRobinDistribution() {
        List<Backend> backends = Arrays.asList(backend1, backend2, backend3);
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(backends);

        // Should cycle through backends in order
        assertEquals(backend1, lb.selectBackend());
        assertEquals(backend2, lb.selectBackend());
        assertEquals(backend3, lb.selectBackend());
        assertEquals(backend1, lb.selectBackend()); // wraps around
    }

    @Test
    void testRoundRobinSingleBackend() {
        List<Backend> backends = List.of(backend1);
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(backends);

        for (int i = 0; i < 5; i++) {
            assertEquals(backend1, lb.selectBackend());
        }
    }

    @Test
    void testRoundRobinWeighted() {
        Backend heavy = new Backend("10.0.0.1", 25565, 2);
        Backend light = new Backend("10.0.0.2", 25565, 1);
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(Arrays.asList(heavy, light));

        // Weighted list: [heavy, heavy, light] → cycle through 3 slots
        int heavyCount = 0;
        int lightCount = 0;
        for (int i = 0; i < 6; i++) { // 2 full cycles
            Backend selected = lb.selectBackend();
            if (selected.equals(heavy)) heavyCount++;
            else if (selected.equals(light)) lightCount++;
        }
        assertEquals(4, heavyCount); // 2x per cycle × 2 cycles
        assertEquals(2, lightCount); // 1x per cycle × 2 cycles
    }

    @Test
    void testRoundRobinEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new RoundRobinLoadBalancer(List.of()));
    }

    @Test
    void testRoundRobinNullThrows() {
        assertThrows(IllegalArgumentException.class, () -> new RoundRobinLoadBalancer(null));
    }

    // --- LeastConnections Tests ---

    @Test
    void testLeastConnectionsInitiallyEven() {
        List<Backend> backends = Arrays.asList(backend1, backend2, backend3);
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer(backends);

        // First selection should pick backend1 (first with 0 connections)
        Backend first = lb.selectBackend();
        assertNotNull(first);
    }

    @Test
    void testLeastConnectionsTracking() {
        List<Backend> backends = Arrays.asList(backend1, backend2);
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer(backends);

        // backend1 has 0 connections, backend2 has 0 → picks first
        Backend selected = lb.selectBackend();
        lb.onSessionCreated(selected);

        // Now one backend has 1 connection, other has 0 → should pick the other
        Backend next = lb.selectBackend();
        assertNotEquals(selected, next);
    }

    @Test
    void testLeastConnectionsRemoval() {
        List<Backend> backends = Arrays.asList(backend1, backend2);
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer(backends);

        // Add 3 connections to backend1
        lb.onSessionCreated(backend1);
        lb.onSessionCreated(backend1);
        lb.onSessionCreated(backend1);
        assertEquals(3, lb.getConnectionCount(backend1));

        // Remove 2
        lb.onSessionRemoved(backend1);
        lb.onSessionRemoved(backend1);
        assertEquals(1, lb.getConnectionCount(backend1));

        // Remove more than exists should bottom at 0
        lb.onSessionRemoved(backend1);
        lb.onSessionRemoved(backend1);
        assertEquals(0, lb.getConnectionCount(backend1));
    }

    @Test
    void testLeastConnectionsWeighted() {
        Backend heavy = new Backend("10.0.0.1", 25565, 2);
        Backend light = new Backend("10.0.0.2", 25565, 1);
        LeastConnectionsLoadBalancer lb = new LeastConnectionsLoadBalancer(Arrays.asList(heavy, light));

        // With weight 2, heavy can handle double the connections before being "equal"
        // Score = connections / weight
        // heavy: 0/2 = 0, light: 0/1 = 0 → picks heavy (first)
        assertEquals(heavy, lb.selectBackend());
        lb.onSessionCreated(heavy);

        // heavy: 1/2 = 0.5, light: 0/1 = 0 → picks light
        assertEquals(light, lb.selectBackend());
        lb.onSessionCreated(light);

        // heavy: 1/2 = 0.5, light: 1/1 = 1.0 → picks heavy
        assertEquals(heavy, lb.selectBackend());
    }

    @Test
    void testLeastConnectionsEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new LeastConnectionsLoadBalancer(List.of()));
    }

    // --- Random Tests ---

    @Test
    void testRandomSelectsFromPool() {
        List<Backend> backends = Arrays.asList(backend1, backend2, backend3);
        RandomLoadBalancer lb = new RandomLoadBalancer(backends);

        Set<Backend> seen = new HashSet<>();
        for (int i = 0; i < 100; i++) {
            seen.add(lb.selectBackend());
        }

        // With 100 tries and 3 backends, extremely unlikely to miss one
        assertEquals(3, seen.size());
    }

    @Test
    void testRandomSingleBackend() {
        List<Backend> backends = List.of(backend1);
        RandomLoadBalancer lb = new RandomLoadBalancer(backends);

        for (int i = 0; i < 10; i++) {
            assertEquals(backend1, lb.selectBackend());
        }
    }

    @Test
    void testRandomEmptyThrows() {
        assertThrows(IllegalArgumentException.class, () -> new RandomLoadBalancer(List.of()));
    }

    // --- Factory Tests ---

    @Test
    void testFactoryRoundRobin() {
        List<Backend> backends = List.of(backend1);
        LoadBalancer lb = LoadBalancerFactory.create("roundrobin", backends);
        assertInstanceOf(RoundRobinLoadBalancer.class, lb);
    }

    @Test
    void testFactoryLeastConn() {
        List<Backend> backends = List.of(backend1);
        LoadBalancer lb = LoadBalancerFactory.create("leastconn", backends);
        assertInstanceOf(LeastConnectionsLoadBalancer.class, lb);
    }

    @Test
    void testFactoryRandom() {
        List<Backend> backends = List.of(backend1);
        LoadBalancer lb = LoadBalancerFactory.create("random", backends);
        assertInstanceOf(RandomLoadBalancer.class, lb);
    }

    @Test
    void testFactoryUnknownFallsBackToRoundRobin() {
        List<Backend> backends = List.of(backend1);
        LoadBalancer lb = LoadBalancerFactory.create("unknown_strategy", backends);
        assertInstanceOf(RoundRobinLoadBalancer.class, lb);
    }

    @Test
    void testFactoryCaseInsensitive() {
        List<Backend> backends = List.of(backend1);
        LoadBalancer lb = LoadBalancerFactory.create("ROUNDROBIN", backends);
        assertInstanceOf(RoundRobinLoadBalancer.class, lb);
    }

    @Test
    void testFactoryEmptyBackendsThrows() {
        assertThrows(IllegalArgumentException.class, () -> LoadBalancerFactory.create("roundrobin", List.of()));
    }

    @Test
    void testGetBackendsReturnsImmutableList() {
        List<Backend> backends = Arrays.asList(backend1, backend2);
        RoundRobinLoadBalancer lb = new RoundRobinLoadBalancer(backends);
        List<Backend> result = lb.getBackends();
        assertThrows(UnsupportedOperationException.class, () -> result.add(backend3));
    }
}
