package com.github.smashyalts.udpproxy.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancing strategy, equivalent to HAProxy's "roundrobin" algorithm.
 * Distributes connections across backends proportionally to their weights
 * using a smooth weighted round-robin algorithm that avoids duplicating
 * backend references in memory.
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final List<Backend> backends;
    private final int totalWeight;
    private final AtomicInteger counter = new AtomicInteger(0);

    public RoundRobinLoadBalancer(List<Backend> backends) {
        if (backends == null || backends.isEmpty()) {
            throw new IllegalArgumentException("At least one backend is required");
        }
        this.backends = Collections.unmodifiableList(new ArrayList<>(backends));
        int total = 0;
        for (Backend backend : this.backends) {
            total += backend.getWeight();
        }
        this.totalWeight = total;
    }

    @Override
    public Backend selectBackend() {
        int index = Math.floorMod(counter.getAndIncrement(), totalWeight);
        int cumulative = 0;
        for (Backend backend : backends) {
            cumulative += backend.getWeight();
            if (index < cumulative) {
                return backend;
            }
        }
        // Should never reach here, but return last as fallback
        return backends.get(backends.size() - 1);
    }

    @Override
    public void onSessionCreated(Backend backend) {
        // No tracking needed for round-robin
    }

    @Override
    public void onSessionRemoved(Backend backend) {
        // No tracking needed for round-robin
    }

    @Override
    public List<Backend> getBackends() {
        return backends;
    }
}
