package com.github.smashyalts.udpproxy.config;

import com.github.smashyalts.udpproxy.loadbalancer.Backend;
import org.yaml.snakeyaml.Yaml;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Configuration for the UDP HAProxy reverse proxy.
 * Loaded from a YAML file following the Geyser configuration pattern.
 * <p>
 * Supports both single-backend (remote-address/remote-port) and
 * multi-backend (backends list) configuration for load balancing.
 */
public class ProxyConfig {

    private String bindAddress = "0.0.0.0";
    private int bindPort = 19132;
    private String remoteAddress = "127.0.0.1";
    private int remotePort = 25565;
    private boolean useProxyProtocol = false;
    private boolean receiveProxyProtocol = false;
    private int sessionTimeoutSeconds = 60;
    private int maxSessions = 0;
    private boolean debugMode = false;
    private String loadBalancingStrategy = "roundrobin";
    private List<Backend> backends = new ArrayList<>();

    public ProxyConfig() {
    }

    /**
     * Load configuration from a YAML file path.
     * Falls back to defaults for any missing values.
     */
    public static ProxyConfig load(Path configPath) throws IOException {
        ProxyConfig config = new ProxyConfig();
        if (Files.exists(configPath)) {
            try (InputStream is = Files.newInputStream(configPath)) {
                config.loadFromStream(is);
            }
        } else {
            // Copy default config from resources
            try (InputStream defaultConfig = ProxyConfig.class.getClassLoader()
                    .getResourceAsStream("config.yml")) {
                if (defaultConfig != null) {
                    Files.copy(defaultConfig, configPath);
                }
            }
        }
        return config;
    }

    /**
     * Load configuration from an input stream (for testing and embedded configs).
     */
    @SuppressWarnings("unchecked")
    public void loadFromStream(InputStream is) {
        Yaml yaml = new Yaml();
        Map<String, Object> data = yaml.load(is);
        if (data == null) {
            return;
        }
        if (data.containsKey("bind-address")) {
            this.bindAddress = (String) data.get("bind-address");
        }
        if (data.containsKey("bind-port")) {
            this.bindPort = ((Number) data.get("bind-port")).intValue();
        }
        if (data.containsKey("remote-address")) {
            this.remoteAddress = (String) data.get("remote-address");
        }
        if (data.containsKey("remote-port")) {
            this.remotePort = ((Number) data.get("remote-port")).intValue();
        }
        if (data.containsKey("use-proxy-protocol")) {
            this.useProxyProtocol = (Boolean) data.get("use-proxy-protocol");
        }
        if (data.containsKey("receive-proxy-protocol")) {
            this.receiveProxyProtocol = (Boolean) data.get("receive-proxy-protocol");
        }
        if (data.containsKey("session-timeout-seconds")) {
            this.sessionTimeoutSeconds = ((Number) data.get("session-timeout-seconds")).intValue();
        }
        if (data.containsKey("max-sessions")) {
            this.maxSessions = ((Number) data.get("max-sessions")).intValue();
        }
        if (data.containsKey("debug-mode")) {
            this.debugMode = (Boolean) data.get("debug-mode");
        }
        if (data.containsKey("load-balancing-strategy")) {
            this.loadBalancingStrategy = (String) data.get("load-balancing-strategy");
        }
        if (data.containsKey("backends")) {
            this.backends = new ArrayList<>();
            List<Map<String, Object>> backendList = (List<Map<String, Object>>) data.get("backends");
            if (backendList != null) {
                for (Map<String, Object> entry : backendList) {
                    String address = (String) entry.get("address");
                    int port = ((Number) entry.get("port")).intValue();
                    int weight = entry.containsKey("weight") ? ((Number) entry.get("weight")).intValue() : 1;
                    this.backends.add(new Backend(address, port, weight));
                }
            }
        }
    }

    /**
     * Get the resolved list of backends for load balancing.
     * If no backends are explicitly configured, falls back to the single
     * remote-address/remote-port as a single backend.
     */
    public List<Backend> getResolvedBackends() {
        if (backends != null && !backends.isEmpty()) {
            return Collections.unmodifiableList(backends);
        }
        return Collections.singletonList(new Backend(remoteAddress, remotePort));
    }

    public String getBindAddress() {
        return bindAddress;
    }

    public int getBindPort() {
        return bindPort;
    }

    public String getRemoteAddress() {
        return remoteAddress;
    }

    public int getRemotePort() {
        return remotePort;
    }

    public boolean isUseProxyProtocol() {
        return useProxyProtocol;
    }

    public boolean isReceiveProxyProtocol() {
        return receiveProxyProtocol;
    }

    public int getSessionTimeoutSeconds() {
        return sessionTimeoutSeconds;
    }

    public int getMaxSessions() {
        return maxSessions;
    }

    public boolean isDebugMode() {
        return debugMode;
    }

    public String getLoadBalancingStrategy() {
        return loadBalancingStrategy;
    }

    public List<Backend> getBackends() {
        return backends;
    }

    @Override
    public String toString() {
        return "ProxyConfig{" +
                "bindAddress='" + bindAddress + '\'' +
                ", bindPort=" + bindPort +
                ", remoteAddress='" + remoteAddress + '\'' +
                ", remotePort=" + remotePort +
                ", useProxyProtocol=" + useProxyProtocol +
                ", receiveProxyProtocol=" + receiveProxyProtocol +
                ", sessionTimeoutSeconds=" + sessionTimeoutSeconds +
                ", maxSessions=" + maxSessions +
                ", debugMode=" + debugMode +
                ", loadBalancingStrategy='" + loadBalancingStrategy + '\'' +
                ", backends=" + getResolvedBackends() +
                '}';
    }
}
