# UDP HAProxy Reverse Proxy - Copilot Instructions

## Repository Overview

This is a **UDP reverse proxy with HAProxy PROXY protocol v2 support and load balancing**, designed for stateful UDP protocols like Minecraft Bedrock. The project follows [Geyser](https://github.com/GeyserMC/Geyser)'s architecture patterns.

**Project Type:** Java-based network proxy application  
**Languages/Frameworks:** Java 17, Netty 4.1, SnakeYAML, SLF4J/Logback  
**Build Tool:** Gradle 8.6 with Shadow plugin for fat JAR generation  
**Repository Size:** Small (~20 source files)

## Build, Test, and Run Instructions

### Prerequisites
- **Java 17 or higher** is required
- Gradle wrapper is included (no separate Gradle installation needed)

### Build Process

**Always clean before building to avoid stale artifacts:**
```bash
./gradlew clean build
```

This will:
1. Compile all Java sources
2. Run all tests
3. Generate a fat JAR with all dependencies via the Shadow plugin
4. Create distribution archives (ZIP and TAR)

**Build output location:** `build/libs/UdpHaproxyReverseProxy-1.0.0-SNAPSHOT.jar`

**Build time:** Approximately 30-40 seconds on first run (with Gradle daemon startup), ~5-10 seconds on subsequent builds.

### Running Tests

```bash
./gradlew test
```

Tests use **JUnit 5** and are located in `src/test/java/`. All tests should pass before committing changes.

**Test coverage areas:**
- Load balancer implementations (roundrobin, leastconn, random)
- HAProxy PROXY protocol v2 encoding/decoding
- Configuration loading and validation
- Session management

### Running the Application

**Option 1: Using Gradle**
```bash
./gradlew run
```

**Option 2: Using the fat JAR**
```bash
java -jar build/libs/UdpHaproxyReverseProxy-1.0.0-SNAPSHOT.jar
```

**Option 3: With custom config path**
```bash
java -jar build/libs/UdpHaproxyReverseProxy-1.0.0-SNAPSHOT.jar /path/to/config.yml
```

On first run, if `config.yml` doesn't exist in the working directory, a default configuration file is created automatically at `config.yml`.

### Clean Environment

To completely clean the build environment:
```bash
./gradlew clean
```

## Project Structure and Architecture

### Source Layout

```
src/
├── main/
│   ├── java/com/github/smashyalts/udpproxy/
│   │   ├── UdpHaproxyProxy.java          # Main entry point
│   │   ├── config/
│   │   │   └── ProxyConfig.java          # YAML configuration loader
│   │   ├── loadbalancer/
│   │   │   ├── Backend.java              # Backend server representation
│   │   │   ├── LoadBalancer.java         # Load balancer interface
│   │   │   ├── LoadBalancerFactory.java  # Factory for creating load balancers
│   │   │   ├── RoundRobinLoadBalancer.java
│   │   │   ├── LeastConnectionsLoadBalancer.java
│   │   │   └── RandomLoadBalancer.java
│   │   ├── network/
│   │   │   ├── ProxyServer.java          # Main server setup and lifecycle
│   │   │   ├── UpstreamHandler.java      # Handles client->proxy packets
│   │   │   └── DownstreamHandler.java    # Handles backend->proxy packets
│   │   ├── protocol/
│   │   │   └── HAProxyUtil.java          # PROXY protocol v2 codec
│   │   └── session/
│   │       ├── ProxySession.java         # Per-client session state
│   │       └── SessionManager.java       # Session lifecycle management
│   └── resources/
│       └── config.yml                     # Default configuration template
└── test/
    └── java/com/github/smashyalts/udpproxy/
        ├── config/
        │   └── ProxyConfigTest.java
        ├── loadbalancer/
        │   └── LoadBalancerTest.java
        ├── protocol/
        │   └── HAProxyUtilTest.java
        └── session/
            └── SessionManagerTest.java
```

### Key Configuration Files

- **`build.gradle`** — Gradle build configuration with Shadow plugin for fat JARs
- **`src/main/resources/config.yml`** — Default configuration template bundled in JAR
- **`config.yml`** (working directory) — Runtime configuration file (created on first run if missing)

### Architecture Components

1. **ProxyServer** (`network/ProxyServer.java`) — Netty-based UDP server that binds to the configured address/port
2. **UpstreamHandler** (`network/UpstreamHandler.java`) — Processes packets from clients, extracts PROXY protocol headers if enabled, creates sessions, and forwards to backends
3. **DownstreamHandler** (`network/DownstreamHandler.java`) — Receives responses from backends and routes them back to the original client
4. **LoadBalancer** (`loadbalancer/`) — Selects a backend for new client sessions using one of three strategies:
   - `roundrobin` — Even distribution across backends (respects weights)
   - `leastconn` — Routes to backend with fewest active connections
   - `random` — Random selection (respects weights)
5. **SessionManager** (`session/SessionManager.java`) — Tracks client→backend mappings with automatic timeout and cleanup
6. **HAProxyUtil** (`protocol/HAProxyUtil.java`) — Encodes/decodes HAProxy PROXY protocol v2 binary headers for UDP (DGRAM type)

### Sticky Sessions

Once a client establishes a session with a backend, all subsequent packets from that client are routed to the same backend until the session expires (configurable via `session-timeout-seconds`). This is critical for stateful protocols.

## Configuration

The proxy is configured via `config.yml` in YAML format. Key settings:

- **`bind-address`** / **`bind-port`** — Where the proxy listens for client connections
- **`remote-address`** / **`remote-port`** — Fallback single backend (if `backends` list is not specified)
- **`backends`** — List of backend servers with optional weights for load balancing
- **`load-balancing-strategy`** — `roundrobin`, `leastconn`, or `random`
- **`use-proxy-protocol`** — Send PROXY protocol v2 headers to backends
- **`receive-proxy-protocol`** — Expect PROXY protocol v2 headers from clients/frontend
- **`session-timeout-seconds`** — Session idle timeout
- **`max-sessions`** — Maximum concurrent sessions (0 = unlimited)
- **`debug-mode`** — Verbose packet logging

## Common Development Tasks

### Making Code Changes

1. **Always run tests first** to establish a baseline:
   ```bash
   ./gradlew test
   ```

2. Make your changes to the source files

3. **Build and test** to validate changes:
   ```bash
   ./gradlew clean build
   ```

4. If adding new features, add corresponding tests in `src/test/java/`

### Adding New Load Balancing Strategies

1. Create a new class in `src/main/java/com/github/smashyalts/udpproxy/loadbalancer/` implementing the `LoadBalancer` interface
2. Register the strategy in `LoadBalancerFactory.java`
3. Add test coverage in `src/test/java/com/github/smashyalts/udpproxy/loadbalancer/LoadBalancerTest.java`
4. Update `config.yml` documentation to include the new strategy

### Modifying Protocol Handling

Protocol-related changes (e.g., PROXY protocol v2) should be made in:
- **Encoding/decoding:** `src/main/java/com/github/smashyalts/udpproxy/protocol/HAProxyUtil.java`
- **Integration:** `src/main/java/com/github/smashyalts/udpproxy/network/UpstreamHandler.java`
- **Tests:** `src/test/java/com/github/smashyalts/udpproxy/protocol/HAProxyUtilTest.java`

## Dependencies

- **Netty 4.1.107.Final** — Asynchronous event-driven network framework
- **netty-codec-haproxy** — PROXY protocol codec (we extend this for UDP support)
- **SnakeYAML 2.2** — YAML parsing for configuration
- **SLF4J 2.0.12 + Logback 1.4.14** — Logging
- **JUnit 5.10.2** — Testing framework

## Important Notes

### Gradle Shadow Plugin

This project uses the **Shadow plugin** to create a fat JAR (all dependencies included). The distribution tasks (`distZip`, `distTar`, `startScripts`) explicitly depend on `shadowJar` to avoid build ordering issues. Do not remove these dependencies in `build.gradle` lines 53-56.

### Session Management

Sessions are stored in memory and tracked by client address. They automatically expire after `session-timeout-seconds` of inactivity. The `SessionManager` maintains counters for the `leastconn` load balancer strategy.

### UDP Specifics

UDP is connectionless, so "sessions" are synthetic constructs based on client source address/port. Keep this in mind when debugging connection issues — NAT, firewalls, and client reconnections can all affect session tracking.

### Netty Event Loop

All network operations use Netty's non-blocking event loop. Do not perform blocking operations in handlers (`UpstreamHandler`, `DownstreamHandler`) as this will degrade performance.

## Validation Before Committing

Always run the full build and test suite before committing:

```bash
./gradlew clean build
```

Ensure:
- All tests pass (no failures or skipped tests due to errors)
- The build completes successfully
- The generated JAR file exists at `build/libs/UdpHaproxyReverseProxy-1.0.0-SNAPSHOT.jar`

## Trust These Instructions

These instructions have been validated by running the full build and test suite. When working on this repository, trust these steps and only search for additional information if something fails or if the instructions are incomplete.
