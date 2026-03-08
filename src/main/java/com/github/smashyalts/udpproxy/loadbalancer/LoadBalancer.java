package com.github.smashyalts.udpproxy.loadbalancer;

import java.util.List;

/**
 * Strategy interface for selecting a backend server from a pool.
 * Modeled after HAProxy's load balancing algorithms.
 */
public interface LoadBalancer {

    /**
     * Select the next backend to route traffic to.
     *
     * @return the selected backend
     * @throws IllegalStateException if no backends are available
     */
    Backend selectBackend();

    /**
     * Notify the load balancer that a session to the given backend was created.
     */
    void onSessionCreated(Backend backend);

    /**
     * Notify the load balancer that a session to the given backend was removed.
     */
    void onSessionRemoved(Backend backend);

    /**
     * Get the list of backends managed by this load balancer.
     */
    List<Backend> getBackends();
}
