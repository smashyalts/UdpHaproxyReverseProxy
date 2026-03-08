package com.github.smashyalts.udpproxy.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Round-robin load balancing strategy, equivalent to HAProxy's "roundrobin" algorithm.
 * Distributes connections evenly across backends, respecting weights.
 * Weighted backends appear multiple times in the rotation cycle.
 */
public class RoundRobinLoadBalancer implements LoadBalancer {

    private final List<Backend> backends;
    private final List<Backend> weightedList;
    private final AtomicInteger counter = new AtomicInteger(0);

    public RoundRobinLoadBalancer(List<Backend> backends) {
        if (backends == null || backends.isEmpty()) {
            throw new IllegalArgumentException("At least one backend is required");
        }
        this.backends = Collections.unmodifiableList(new ArrayList<>(backends));
        this.weightedList = buildWeightedList(this.backends);
    }

    private static List<Backend> buildWeightedList(List<Backend> backends) {
        List<Backend> list = new ArrayList<>();
        for (Backend backend : backends) {
            for (int i = 0; i < backend.getWeight(); i++) {
                list.add(backend);
            }
        }
        return Collections.unmodifiableList(list);
    }

    @Override
    public Backend selectBackend() {
        int index = Math.floorMod(counter.getAndIncrement(), weightedList.size());
        return weightedList.get(index);
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
