package com.github.smashyalts.udpproxy.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random load balancing strategy, equivalent to HAProxy's "random" algorithm.
 * Selects a random backend for each new connection, respecting weights
 * via cumulative weight ranges to avoid duplicating backend references.
 */
public class RandomLoadBalancer implements LoadBalancer {

    private final List<Backend> backends;
    private final int totalWeight;

    public RandomLoadBalancer(List<Backend> backends) {
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
        int value = ThreadLocalRandom.current().nextInt(totalWeight);
        int cumulative = 0;
        for (Backend backend : backends) {
            cumulative += backend.getWeight();
            if (value < cumulative) {
                return backend;
            }
        }
        // Should never reach here, but return last as fallback
        return backends.get(backends.size() - 1);
    }

    @Override
    public void onSessionCreated(Backend backend) {
        // No tracking needed for random
    }

    @Override
    public void onSessionRemoved(Backend backend) {
        // No tracking needed for random
    }

    @Override
    public List<Backend> getBackends() {
        return backends;
    }
}
