package com.github.smashyalts.udpproxy.session;

import com.github.smashyalts.udpproxy.loadbalancer.LoadBalancer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Manages proxy sessions, mapping client addresses to their backend connections.
 * Sessions are automatically cleaned up after a configurable timeout period.
 * Notifies the load balancer when sessions are created or removed so that
 * strategies like least-connections can track active connection counts.
 */
public class SessionManager {

    private static final Logger logger = LoggerFactory.getLogger(SessionManager.class);

    private final Map<InetSocketAddress, ProxySession> sessions = new ConcurrentHashMap<>();
    private final int sessionTimeoutSeconds;
    private final int maxSessions;
    private final ScheduledExecutorService cleanupExecutor;
    private volatile LoadBalancer loadBalancer;

    public SessionManager(int sessionTimeoutSeconds, int maxSessions) {
        this.sessionTimeoutSeconds = sessionTimeoutSeconds;
        this.maxSessions = maxSessions;
        this.cleanupExecutor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "session-cleanup");
            t.setDaemon(true);
            return t;
        });
        this.cleanupExecutor.scheduleAtFixedRate(this::cleanupExpiredSessions,
                sessionTimeoutSeconds, sessionTimeoutSeconds, TimeUnit.SECONDS);
    }

    /**
     * Set the load balancer to notify on session lifecycle events.
     */
    public void setLoadBalancer(LoadBalancer loadBalancer) {
        this.loadBalancer = loadBalancer;
    }

    /**
     * Get an existing session for a client address, or null if none exists.
     */
    public ProxySession getSession(InetSocketAddress clientAddress) {
        ProxySession session = sessions.get(clientAddress);
        if (session != null) {
            if (session.isExpired(sessionTimeoutSeconds) || !session.getDownstreamChannel().isActive()) {
                removeSession(clientAddress);
                return null;
            }
            session.touch();
        }
        return session;
    }

    /**
     * Register a new session for a client address.
     *
     * @return the session, or null if max sessions has been reached
     */
    public ProxySession addSession(InetSocketAddress clientAddress, ProxySession session) {
        if (maxSessions > 0 && sessions.size() >= maxSessions) {
            logger.warn("Maximum sessions ({}) reached, rejecting connection from {}", maxSessions, clientAddress);
            return null;
        }
        sessions.put(clientAddress, session);
        if (loadBalancer != null && session.getBackend() != null) {
            loadBalancer.onSessionCreated(session.getBackend());
        }
        logger.info("New session created for {} -> {} (total: {})",
                clientAddress, session.getBackend(), sessions.size());
        return session;
    }

    /**
     * Remove and close a session.
     */
    public void removeSession(InetSocketAddress clientAddress) {
        ProxySession session = sessions.remove(clientAddress);
        if (session != null) {
            if (loadBalancer != null && session.getBackend() != null) {
                loadBalancer.onSessionRemoved(session.getBackend());
            }
            session.close();
            logger.info("Session removed for {} (total: {})", clientAddress, sessions.size());
        }
    }

    /**
     * Check if a session exists for the given client address.
     */
    public boolean hasSession(InetSocketAddress clientAddress) {
        return sessions.containsKey(clientAddress);
    }

    /**
     * Get the number of active sessions.
     */
    public int getSessionCount() {
        return sessions.size();
    }

    /**
     * Clean up expired sessions.
     */
    void cleanupExpiredSessions() {
        int removed = 0;
        for (Map.Entry<InetSocketAddress, ProxySession> entry : sessions.entrySet()) {
            ProxySession session = entry.getValue();
            if (session.isExpired(sessionTimeoutSeconds) || !session.getDownstreamChannel().isActive()) {
                sessions.remove(entry.getKey());
                if (loadBalancer != null && session.getBackend() != null) {
                    loadBalancer.onSessionRemoved(session.getBackend());
                }
                session.close();
                removed++;
            }
        }
        if (removed > 0) {
            logger.info("Cleaned up {} expired sessions (remaining: {})", removed, sessions.size());
        }
    }

    /**
     * Shutdown the session manager and close all sessions.
     */
    public void shutdown() {
        cleanupExecutor.shutdown();
        for (Map.Entry<InetSocketAddress, ProxySession> entry : sessions.entrySet()) {
            ProxySession session = entry.getValue();
            if (loadBalancer != null && session.getBackend() != null) {
                loadBalancer.onSessionRemoved(session.getBackend());
            }
            session.close();
        }
        sessions.clear();
        logger.info("Session manager shut down");
    }
}
