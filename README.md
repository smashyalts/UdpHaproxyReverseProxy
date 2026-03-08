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
./gradlew build
```

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

### Debug Mode

When `debug-mode: true`, the proxy logs detailed information about every packet:

- **Incoming packets:** "Received X bytes from [client-ip] (real: [real-client-ip])"
- **Backend selection:** "Selected backend [backend] for client [client-ip]"
- **PROXY protocol headers:** "Decoded PROXY protocol: real client = [real-ip]" and "Prepended PROXY protocol v2 header for [client] -> [backend]"
- **Forwarded packets:** "Forwarded X bytes to backend [backend]"
- **Backend responses:** "Received X bytes from backend, forwarding to client [client-ip]"

Debug mode is useful for troubleshooting connectivity issues and verifying that packets are being forwarded correctly.

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

### PROXY Protocol Compatibility

HAProxy PROXY protocol v2 is supported for backends that can parse these headers.

**✓ Supported Backends:**
- **Geyser Standalone** — Minecraft Bedrock to Java proxy (supports PROXY protocol v2)
- Custom applications with PROXY protocol support
- Other proxies/load balancers (HAProxy, Nginx, etc.)

**✗ NOT Supported:**
- Vanilla Minecraft Bedrock Edition servers
- Most standard game servers without PROXY protocol support

### Load Balancing Geyser Instances

This proxy is ideal for load balancing multiple Geyser standalone instances while preserving real client IPs:

**Example Configuration:**
```yaml
bind-address: "0.0.0.0"
bind-port: 19132

# Enable PROXY protocol to send real client IPs to Geyser
use-proxy-protocol: true

# Load balance across multiple Geyser instances
load-balancing-strategy: "leastconn"
backends:
  - address: "10.0.0.1"
    port: 19133      # Geyser instance 1
    weight: 1
  - address: "10.0.0.2"
    port: 19133      # Geyser instance 2
    weight: 1

session-timeout-seconds: 300
debug-mode: false
```

**Geyser Configuration:**
In each Geyser instance's `config.yml`, ensure it listens on the correct port and has PROXY protocol enabled:
```yaml
bedrock:
  port: 19133
  enable-proxy-protocol: true
```

### Proxy sitting behind HAProxy

If this proxy sits behind an HAProxy instance that adds PROXY protocol headers:

1. Set `receive-proxy-protocol: true` in `config.yml`
2. Configure HAProxy to send PROXY protocol v2 with your UDP frontend

Example HAProxy configuration:
```
frontend udp_frontend
    bind :19132
    mode udp
    default_backend udp_backend

backend udp_backend
    mode udp
    server proxy1 127.0.0.1:19133 send-proxy-v2
```

### Forwarding real client IP to backend

To inform the backend server of the real client IP (only if backend supports PROXY protocol v2):

1. Set `use-proxy-protocol: true` in `config.yml`
2. The backend must support receiving PROXY protocol v2 headers prepended to the first UDP packet
3. **If unsure, keep this DISABLED** — enabling it without backend support will break connectivity

## Testing

```bash
./gradlew test
```

## Troubleshooting

### Clients cannot see the server in their server list

**Symptom:** You see packets being forwarded in debug mode, but clients don't see the server or cannot connect.

**Common Causes:**

1. **PROXY protocol mismatch:**
   - If using vanilla Minecraft Bedrock servers: Set `use-proxy-protocol: false`
   - If using Geyser: Set `use-proxy-protocol: true` AND ensure Geyser has `enable-proxy-protocol: true`
   
2. **Geyser not configured for PROXY protocol:**
   - If you enabled `use-proxy-protocol: true` but Geyser doesn't have `enable-proxy-protocol: true` in its config, it will ignore packets
   - Check each Geyser instance's `config.yml`

**How to verify:** Enable `debug-mode: true` and check the logs:
- ✅ You should see: `Received X bytes from backend, forwarding to client`
- ❌ If you only see: `Forwarded X bytes to backend` but never responses, your backend isn't responding

**Why this happens:** When PROXY protocol is enabled, the proxy prepends a 28-byte (IPv4) or 52-byte (IPv6) header to the first packet. If the backend doesn't understand this header, it treats the packet as invalid and ignores it, never sending a response.

### Backend is not receiving packets

**Symptom:** Debug logs show `Forwarded X bytes to backend` but backend logs show no received packets.

**Possible causes:**
1. Firewall blocking UDP traffic between proxy and backend
2. Backend not listening on the configured address/port
3. Network routing issues

**Solution:**
1. Verify backend is listening: `netstat -ulnp | grep <backend-port>`
2. Check firewall rules allow UDP between proxy and backend
3. Test direct connectivity: `nc -u <backend-ip> <backend-port>`

### Session timeout too short

**Symptom:** Connections work initially but drop unexpectedly during gameplay.

**Solution:**
- Increase `session-timeout-seconds` in config.yml
- Minecraft typically needs 60-300 seconds depending on your use case
- Sessions are cleaned up after the configured timeout of inactivity

### High latency or packet loss

**Symptom:** Gameplay is laggy even though network is fine.

**Possible causes:**
1. Load balancer selecting a poor backend
2. Too many concurrent sessions
3. Network congestion between proxy and backends

**Solution:**
1. Try different load balancing strategies: `leastconn` or `random`
2. Set `max-sessions` to limit concurrent connections
3. Monitor network latency to backends
4. Place proxy geographically close to backends

### "Max sessions reached" errors

**Symptom:** New clients cannot connect, logs show "Max sessions reached".

**Solution:**
- Increase `max-sessions` in config.yml, or set to `0` for unlimited
- Check for session leak (sessions not timing out properly)
- Monitor session count over time to understand normal usage

## License

MIT
