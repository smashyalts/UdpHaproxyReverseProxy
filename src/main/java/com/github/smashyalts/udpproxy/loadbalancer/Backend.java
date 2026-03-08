package com.github.smashyalts.udpproxy.loadbalancer;

import java.net.InetSocketAddress;
import java.util.Objects;

/**
 * Represents a backend server that can receive proxied UDP traffic.
 * Each backend has an address, port, and optional weight for weighted load balancing.
 */
public class Backend {

    private final String address;
    private final int port;
    private final int weight;
    private final InetSocketAddress socketAddress;

    public Backend(String address, int port) {
        this(address, port, 1);
    }

    public Backend(String address, int port, int weight) {
        this.address = address;
        this.port = port;
        this.weight = Math.max(1, weight);
        this.socketAddress = new InetSocketAddress(address, port);
    }

    public String getAddress() {
        return address;
    }

    public int getPort() {
        return port;
    }

    public int getWeight() {
        return weight;
    }

    public InetSocketAddress getSocketAddress() {
        return socketAddress;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Backend backend = (Backend) o;
        return port == backend.port && Objects.equals(address, backend.address);
    }

    @Override
    public int hashCode() {
        return Objects.hash(address, port);
    }

    @Override
    public String toString() {
        return address + ":" + port + (weight != 1 ? " (weight=" + weight + ")" : "");
    }
}
