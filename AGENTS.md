# AGENTS.md — SSH Tunnel Manager

## Mission
Build a small, dependable desktop application to create and manage SSH local port-forwarding tunnels on **Linux, Windows, and macOS**. The application must be usable by nontechnical users, and distributable as a native installer/app with a bundled Java runtime. Work incrementally, keep the interface simple, and prioritize SSH security and predictable connection lifecycle behavior.

## Technology decisions
- **Java 21 LTS** for the application.
- **Swing** for the desktop user interface (use native system look and feel where practical).
- **Apache MINA SSHD** client for SSH connections and local port forwarding; do not shell out to `ssh` for the main feature.
- **Gradle with Kotlin DSL** (`build.gradle.kts`) and checked-in **Gradle Wrapper**. Do not require a system-wide Gradle installation.
- **JUnit 5** for tests. Use a local/in-process SSH test server for integration tests.
- Use `jpackage` with a bundled runtime to produce platform-specific application packages. Build native packages on their respective OS; do not promise cross-compilation.
- Choose a small, maintained JSON library for persisted application settings. Keep dependencies few and justified.

## Reference use case
Example command to reproduce the intended behavior:

```bash
ssh -N -L 127.0.0.1:18000:127.0.0.1:18000 ssh.example.com
```

Then opens `http://127.0.0.1:18000/` in a web browser.

The app must make this possible through a straightforward graphical interface, without requiring users to type shell commands.

## Scope and UX
### MVP features
1. Enter an **SSH server** (DNS name or IP), **SSH port** (default 22), and **SSH username** (suggest the OS username only as an editable default; never assume it is correct).
2. Enter the **remote destination host** (default `127.0.0.1`) and **remote destination port** (required, 1–65535). The remote destination is resolved and reached from the SSH server, not from the desktop.
3. Choose a **local listening port** explicitly or enable **automatic local port selection**. Bind only to `127.0.0.1` by default; never expose the forwarded service on all network interfaces.
4. Choose a private-key identity from the user's local filesystem; support a key protected by a passphrase via an interactive prompt. Do not upload, copy, log, or persist the key material or passphrase.
5. Offer an explicitly selected **try configured keys** option: attempt only user-selected/imported identities in a deterministic order, with bounded attempts and understandable errors. Never indiscriminately scan and submit every key found on disk.
6. Show **Connect**, **Disconnect**, and a clear connection state (`Disconnected`, `Connecting`, `Connected`, `Error`). Display the effective local endpoint.
7. On successful connection **and after verifying the local forward has been established**, optionally launch the system default browser at `http://127.0.0.1:<actual-local-port>/` (enabled by default). The local port may differ from the remote port.
8. Display concise, actionable errors for authentication failure, host-key mismatch, unreachable server, port collision, unsupported key formats, and dropped connections.
9. Cleanly close the tunnel, listening socket, and SSH connection when disconnecting or quitting the app.
10. Keep networking off the Swing Event Dispatch Thread; update the GUI on the EDT.

### Multiple connections and saved profiles (core requirement)
- Provide a **connection list** as the main screen (e.g. table or sidebar), showing saved profile name, SSH host, remote destination, effective local endpoint, and state (`Disconnected`, `Connecting`, `Connected`, `Error`).
- Provide explicit **Add**, **Edit**, **Duplicate**, **Delete**, **Connect**, **Disconnect**, and **Open in browser** actions. Adding a profile must not automatically connect. Support selecting a profile from the list and opening its edit form.
- Persist multiple named connection profiles across restarts. Each profile has a **stable unique ID** that is independent of its name; display names need not be unique, although the UI should discourage ambiguous duplicates.
- Save friendly name, SSH host/port/username, remote host/port, local port preference (automatic or fixed), selected identity **file paths** or bounded identity fallback order, browser-launch preference, and host-key verification preferences. Never store credentials or private-key contents.
- Allow **multiple tunnels to be connected at the same time**, with independent SSH sessions, local listeners, state and error messages. A failure or disconnect in one connection must not affect others.
- Support independent connect/disconnect and reconnection; disable or guard duplicate concurrent Connect operations for the same profile. Do not block other profiles while one is connecting.
- **Delete profile** must ask for confirmation. If its tunnel is active, explain that deletion will first disconnect that tunnel, then remove the saved profile; never delete key files or `known_hosts` entries. Cancel leaves everything unchanged.
- Editing a connected profile must not silently mutate the running tunnel. Either require disconnect before applying connection-affecting edits, or use a clearly documented "changes apply on reconnect" policy.
- **Port conflicts**: automatic local ports are allocated independently per tunnel by the OS; fixed local-port collisions produce a clear per-profile error, never silently alter an explicitly requested port or disconnect another tunnel.
- Application shutdown must disconnect **all** active tunnels and release all sockets; reflect states reliably if a remote host disconnects unexpectedly.
- Keep connection storage schema versioned and allow migration. Prefer an MVP with connection-list management from the outset, rather than designing solely around a single connection form.


### Explicitly out of scope for MVP
- Remote (`-R`) and dynamic SOCKS (`-D`) forwarding.
- An integrated web browser, SSH terminal, VPN client, or remote command execution.
- SSH agent and hardware/security-key integration (document as potential later features rather than assuming universal platform support).
- Password-based SSH authentication unless explicitly requested in a later iteration.
- Arbitrary advanced `ssh_config` features or ProxyJump unless planned and tested separately.

## SSH and security requirements
- **Strict host-key verification is mandatory.** Support standard OpenSSH `known_hosts` behavior when feasible. Unknown hosts require an explicit first-use trust decision showing an informative fingerprint; changed or mismatched host keys must stop the connection with a conspicuous warning. Never silently disable verification or use accept-all verifiers.
- Use standards-based authentication and an SSH implementation that supports common modern key formats. Confirm exact key support with tests; explain unsupported formats instead of silently falling back to insecure authentication.
- Store no passphrases, private keys, or secrets in settings, caches, exception messages, telemetry, or logs. Avoid logging usernames/hostnames by default in verbose diagnostics unless deliberately enabled by the user.
- Preserve private key files as they are; “Import key” means selecting/registering an existing path, not copying the key into application storage. If copying is later required, make it an explicit separate operation with secure permissions.
- Ensure private-key file paths and `known_hosts` paths are handled safely on all supported OSes, including whitespace and Unicode characters.
- Establish the local listener atomically using an OS-assigned ephemeral port when automatic mode is selected (port `0` where supported by the forwarding API); **do not** select a supposedly free port by probing then releasing it.
- Bind local forwards to loopback explicitly and prevent remote endpoints from being mistaken for local listener addresses.
- Validate input values and reject invalid ports, blank server names, malformed remote targets, and paths that cannot be read.
- Do not add features that alter firewall configuration, system SSH settings, or global trust stores.
- No production-host connections in automated tests.

## Architecture
Maintain a clean separation of concerns, with simple interfaces and testable behavior:

- `model/`: immutable tunnel profiles, identity references, connection states, and validation.
- `ssh/`: SSH session management, host-key verification, key handling, local forwarding, tunnel lifecycle.
- `tunnels/`: multi-tunnel coordinator keyed by stable profile ID, independent lifecycle management, conflict handling, per-profile events and cleanup.
- `ui/`: Swing windows/dialogs and presentation logic; no blocking network calls.
- `config/`: versioned profile storage and loading; sensible per-user configuration location by OS.
- `browser/`: abstraction over opening the system browser (e.g. `Desktop.browse`) for testability.

Prefer explicit lifecycle ownership, idempotent disconnect, resource cleanup, and event/callback-based state updates. Do not create unnecessary frameworks, layers, or abstractions.

## Development workflow for Codex
1. **Inspect first.** Read this document, inspect repository files, identify existing conventions and dependencies, and summarize any major design conflicts before changing code.
2. **Plan a thin vertical slice.** Prioritize a minimal Swing connection list with Add/Delete and one working SSH local tunnel; keep the runtime capable of managing multiple tunnels independently from the start.
3. **Make targeted changes.** Keep patches small and relevant, preserve backward compatibility where applicable, avoid unrelated rewrites.
4. **Prefer secure defaults.** When requirements are ambiguous, choose the safer behavior and record the decision. Do not implement insecure shortcuts to “make it work.”
5. **Test the change.** Run applicable Gradle tasks, unit tests and local SSH integration tests; report precisely what was and was not exercised.
6. **Update documentation.** Keep README, packaging instructions, supported identity formats, security caveats, and user-visible behavior current.
7. **Report results.** At the end of each task, state changed files, behavioral changes, commands/tests run, unresolved risks, and next recommended step.
8. **No global installs without approval.** Use the repository workspace, Gradle Wrapper and project-managed tools/caches where feasible. Do not use `sudo`, change OS configuration, or install system-wide packages on behalf of the user.
9. **No destructive actions without explicit authorization.** Do not delete user data, overwrite existing keys, modify production infrastructure, or access live customer servers unless expressly requested.

## Delivery milestones
1. **Bootstrap and connection management UI**: Gradle Wrapper, Java 21 build, entry point, README, test scaffold, native-looking Swing window, connection list, Add/Edit/Delete actions, and profile model with stable IDs.
2. **SSH vertical slice**: local forwarding with one explicitly selected private key, strict host-key verification, manual local port, connect/disconnect, browser launch; perform operations against the selected profile.
3. **Persistent profiles and usability**: save/load profiles, automatic local-port allocation, robust errors, passphrase prompt, busy/progress states, and state-safe GUI.
4. **Concurrent connections**: multiple simultaneous tunnels, independent controls/state, conflict handling, per-profile cleanup, edit/delete-while-connected behavior, identity selection and bounded multi-key fallback, versioned profile migrations.
5. **Distribution**: Windows/macOS/Linux packaging, CI build matrix, smoke tests and release documentation.

## Test acceptance criteria
- The reference use case creates a localhost listener and forwards browser requests through the correct SSH destination.
- A local port different from the remote port works, including an OS-assigned ephemeral local port.
- Browser launch uses the **actual bound port** and happens only after a successful forward setup.
- Refusing an unknown host key prevents authentication and forward setup; changed known host keys fail closed.
- An incorrect key and incorrect passphrase surface clear errors without leaking sensitive data.
- A busy manually requested local port fails cleanly without opening the wrong browser URL.
- Two or more distinct profiles can be connected simultaneously and forward to their own destinations; stopping one does not interrupt the others.
- Add, edit, duplicate, delete and restart preserve the correct saved profiles and stable IDs; deletion of an active profile is confirmed and releases only its resources.
- A fixed local-port collision produces an error only for the conflicting profile; automatic local-port selection works across concurrent tunnels.
- Repeated rapid Connect/Disconnect actions cannot create duplicate sockets, orphaned sessions or cross-profile state changes.
- Disconnect/reconnect and closing the application release all resources reliably.
- Network operations do not freeze Swing, even on timeout or failed authentication.
- Parsing and persistence work with paths containing spaces and non-ASCII characters.
- Gradle wrapper builds and tests the project. Packaging documentation explains native-build OS constraints and signing/notarization requirements where applicable.

## First task for a new Codex session
Read this file, inspect the repository, and propose a succinct implementation plan for Milestone 1 and Milestone 2. Then implement Milestone 1 only, unless the user explicitly asks to continue. Do not connect to any real remote server while scaffolding.
