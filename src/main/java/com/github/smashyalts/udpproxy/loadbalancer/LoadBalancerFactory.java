package com.github.smashyalts.udpproxy.loadbalancer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Factory for creating load balancer instances from configuration.
 */
public final class LoadBalancerFactory {

    private static final Logger logger = LoggerFactory.getLogger(LoadBalancerFactory.class);

    private LoadBalancerFactory() {
    }

    /**
     * Create a load balancer from the given strategy name and backend list.
     *
     * @param strategy the strategy name: "roundrobin", "leastconn", or "random"
     * @param backends the list of backends
     * @return the configured load balancer
     */
    public static LoadBalancer create(String strategy, List<Backend> backends) {
        if (backends == null || backends.isEmpty()) {
            throw new IllegalArgumentException("At least one backend is required");
        }

        String normalized = strategy.toLowerCase().trim();
        LoadBalancer lb;
        switch (normalized) {
            case "roundrobin":
                lb = new RoundRobinLoadBalancer(backends);
                break;
            case "leastconn":
                lb = new LeastConnectionsLoadBalancer(backends);
                break;
            case "random":
                lb = new RandomLoadBalancer(backends);
                break;
            default:
                logger.warn("Unknown load balancing strategy '{}', falling back to roundrobin", strategy);
                lb = new RoundRobinLoadBalancer(backends);
                break;
        }

        logger.info("Load balancer initialized: strategy={}, backends={}", normalized, backends.size());
        for (Backend backend : backends) {
            logger.info("  Backend: {}", backend);
        }
        return lb;
    }
}
