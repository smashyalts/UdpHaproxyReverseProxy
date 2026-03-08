# UDP HAProxy Reverse Proxy

A configurable UDP reverse proxy with HAProxy PROXY protocol v2 support, following [Geyser](https://github.com/GeyserMC/Geyser)'s (Minecraft Bedrock to Java) architecture patterns.

## Features

- **UDP Reverse Proxy** — Forwards UDP packets between clients and a backend server with per-client session management
- **HAProxy PROXY Protocol v2 (Outbound)** — Sends PROXY protocol v2 headers to the backend so it knows the real client IP/port
- **HAProxy PROXY Protocol v2 (Inbound)** — Accepts PROXY protocol v2 headers from a frontend load balancer (e.g. HAProxy) to extract the real client address
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

# The address of the remote/backend server
remote-address: "127.0.0.1"

# The port of the remote/backend server
remote-port: 25565

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

The proxy follows Geyser's architecture:

```
Clients ──UDP──▶ [Upstream Handler] ──UDP──▶ Backend Server
                 (PROXY proto in)          (PROXY proto out)
                       │
                 [Session Manager]
                 (per-client state)
```

- **UpstreamHandler** — Receives client packets, decodes inbound PROXY protocol headers, creates sessions, and forwards to the backend
- **DownstreamHandler** — Receives backend responses and forwards them back to the client
- **SessionManager** — Tracks client-to-backend mappings with automatic expiry
- **HAProxyUtil** — Encodes/decodes PROXY protocol v2 binary headers for UDP (DGRAM)
- **ProxyConfig** — YAML-based configuration loaded at startup

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
