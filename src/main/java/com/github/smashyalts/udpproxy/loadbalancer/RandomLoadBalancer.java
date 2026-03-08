package com.github.smashyalts.udpproxy.loadbalancer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Random load balancing strategy, equivalent to HAProxy's "random" algorithm.
 * Selects a random backend for each new connection, respecting weights.
 */
public class RandomLoadBalancer implements LoadBalancer {

    private final List<Backend> backends;
    private final List<Backend> weightedList;

    public RandomLoadBalancer(List<Backend> backends) {
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
        int index = ThreadLocalRandom.current().nextInt(weightedList.size());
        return weightedList.get(index);
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
