# UDP HAProxy Reverse Proxy

A configurable UDP reverse proxy with HAProxy PROXY protocol v2 support and built-in load balancing, following [Geyser](https://github.com/GeyserMC/Geyser)'s (Minecraft Bedrock to Java) architecture patterns.

## Features

- **UDP Reverse Proxy** — Forwards UDP packets between clients and backend servers with per-client session management
- **Load Balancing** — HAProxy-style load balancing across multiple backends with three strategies:
  - `roundrobin` — Distribute connections evenly across backends (HAProxy default)
  - `leastconn` — Route to the backend with the fewest active sessions
  - `random` — Select a random backend for each new session
- **Weighted Backends** — Assign weights to backends for proportional traffic distribution
- **HAProxy PROXY Protocol v2 (Outbound)** — Sends PROXY protocol v2 headers to the backend so it knows the real client IP/port
- **HAProxy PROXY Protocol v2 (Inbound)** — Accepts PROXY protocol v2 headers from a frontend load balancer (e.g. HAProxy) to extract the real client address
- **Sticky Sessions** — Once a client session is established to a backend, all subsequent packets go to the same backend until the session expires
- **YAML Configuration** — Geyser-style `config.yml` for all settings
- **Session Management** — Automatic session creation, timeout, and cleanup
- **Max Sessions Limit** — Configurable cap on concurrent sessions
- **Debug Mode** — Optional verbose logging of all proxied packets
- **IPv4 and IPv6** — Full support for both address families

## Quick Start

### Prerequisites

- Java 17 or higher

### Build

```bash
./gradlew shadowJar
```

This creates a fat JAR (uber JAR) at `build/libs/UdpHaproxyReverseProxy-1.0.0-SNAPSHOT.jar` with all dependencies included.

### Run

```bash
./gradlew run
# or
java -jar build/libs/UdpHaproxyReverseProxy-1.0.0-SNAPSHOT.jar
```

On first run, a default `config.yml` is created in the working directory.

### Configuration

Edit `config.yml` to configure the proxy:

```yaml
# The address the proxy will listen on
bind-address: "0.0.0.0"

# The port the proxy will listen on (default: Bedrock port)
bind-port: 19132

# The address of the remote/backend server (used when no backends list is configured)
remote-address: "127.0.0.1"

# The port of the remote/backend server (used when no backends list is configured)
remote-port: 25565

# Load balancing strategy: "roundrobin", "leastconn", or "random"
load-balancing-strategy: "roundrobin"

# List of backend servers for load balancing.
# If not specified, falls back to remote-address/remote-port as a single backend.
# Each backend has: address, port, and optional weight (default: 1).
backends:
  - address: "10.0.0.1"
    port: 25565
    weight: 1
  - address: "10.0.0.2"
    port: 25565
    weight: 2
  - address: "10.0.0.3"
    port: 25565
    weight: 1

# Send HAProxy PROXY protocol v2 headers to the backend
use-proxy-protocol: false

# Expect HAProxy PROXY protocol v2 headers on incoming packets
receive-proxy-protocol: false

# Session timeout in seconds
session-timeout-seconds: 60

# Maximum concurrent sessions (0 = unlimited)
max-sessions: 0

# Enable debug logging
debug-mode: false
```

You can also specify a custom config path:

```bash
java -jar UdpHaproxyReverseProxy.jar /path/to/config.yml
```

## Architecture

The proxy follows Geyser's architecture with added load balancing:

```
                                         ┌──▶ Backend 1
Clients ──UDP──▶ [Upstream Handler] ─────┼──▶ Backend 2
                 (PROXY proto in)        └──▶ Backend 3
                       │
                 [Load Balancer]
                 (roundrobin/leastconn/random)
                       │
                 [Session Manager]
                 (per-client sticky sessions)
```

- **UpstreamHandler** — Receives client packets, decodes inbound PROXY protocol headers, creates sessions with a load-balanced backend, and forwards to the backend
- **DownstreamHandler** — Receives backend responses and forwards them back to the client
- **LoadBalancer** — Selects a backend for new sessions using the configured strategy (roundrobin, leastconn, random)
- **SessionManager** — Tracks client-to-backend mappings with automatic expiry and notifies the load balancer for connection counting
- **HAProxyUtil** — Encodes/decodes PROXY protocol v2 binary headers for UDP (DGRAM)
- **ProxyConfig** — YAML-based configuration loaded at startup

## Load Balancing

### Strategies

| Strategy | Description | Best For |
|----------|-------------|----------|
| `roundrobin` | Distributes connections evenly, respecting weights | General use, even load distribution |
| `leastconn` | Routes to backend with fewest active sessions, normalized by weight | Backends with varying capacity |
| `random` | Randomly selects a backend, respecting weights | Simple distribution, no shared state |

### Weighted Backends

Backends with higher `weight` values receive proportionally more connections:

```yaml
backends:
  - address: "powerful-server"
    port: 25565
    weight: 3    # Receives 3x more connections
  - address: "standard-server"
    port: 25565
    weight: 1    # Baseline
```

### Sticky Sessions

Once a client is assigned to a backend, all packets from that client are forwarded to the same backend for the duration of the session. This is essential for stateful protocols like Minecraft where the backend maintains per-client state.

### Backward Compatibility

If no `backends` list is specified, the proxy falls back to using `remote-address` and `remote-port` as a single backend — the same behavior as before load balancing was added.

## HAProxy Integration

### Proxy sitting behind HAProxy

If this proxy sits behind an HAProxy instance that adds PROXY protocol headers:

1. Set `receive-proxy-protocol: true` in `config.yml`
2. Configure HAProxy to send PROXY protocol v2 with your UDP frontend

### Forwarding real client IP to backend

To inform the backend server of the real client IP:

1. Set `use-proxy-protocol: true` in `config.yml`
2. The backend must support receiving PROXY protocol v2 headers prepended to the first UDP packet

## Testing

```bash
./gradlew test
```

## License

MIT
