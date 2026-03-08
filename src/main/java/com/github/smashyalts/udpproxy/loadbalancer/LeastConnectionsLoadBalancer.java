package com.github.smashyalts.udpproxy.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Least-connections load balancing strategy, equivalent to HAProxy's "leastconn" algorithm.
 * Routes new connections to the backend with the fewest active sessions.
 * When there is a tie, backends with higher weight are preferred.
 */
public class LeastConnectionsLoadBalancer implements LoadBalancer {

    private final List<Backend> backends;
    private final Map<Backend, Integer> connectionCounts = new ConcurrentHashMap<>();

    public LeastConnectionsLoadBalancer(List<Backend> backends) {
        if (backends == null || backends.isEmpty()) {
            throw new IllegalArgumentException("At least one backend is required");
        }
        this.backends = Collections.unmodifiableList(new ArrayList<>(backends));
        for (Backend backend : this.backends) {
            connectionCounts.put(backend, 0);
        }
    }

    @Override
    public Backend selectBackend() {
        Backend best = null;
        double bestScore = Double.MAX_VALUE;

        for (Backend backend : backends) {
            int count = connectionCounts.getOrDefault(backend, 0);
            // Normalize by weight: lower score = better candidate
            double score = (double) count / backend.getWeight();
            if (score < bestScore) {
                bestScore = score;
                best = backend;
            }
        }

        if (best == null) {
            throw new IllegalStateException("No backends available");
        }
        return best;
    }

    @Override
    public void onSessionCreated(Backend backend) {
        connectionCounts.merge(backend, 1, Integer::sum);
    }

    @Override
    public void onSessionRemoved(Backend backend) {
        connectionCounts.merge(backend, -1, (a, b) -> Math.max(0, a + b));
    }

    @Override
    public List<Backend> getBackends() {
        return backends;
    }

    /**
     * Get the current connection count for a backend (for testing/monitoring).
     */
    public int getConnectionCount(Backend backend) {
        return connectionCounts.getOrDefault(backend, 0);
    }
}
