# SSH Tunnel Manager

A Java 21 desktop application for local SSH tunnels. Each saved profile has a stable ID, and each active tunnel has its own SSH session and local listener.

The application is licensed under the [Apache License 2.0](LICENSE). Licenses and attributions for bundled dependencies are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).

## Run and build

With Java 21 available:

```sh
./gradlew run
```

On Windows, use `gradlew.bat run`. Run `./gradlew build` to compile and test. The wrapper downloads Gradle 8.13, and Maven Central supplies dependencies on the first build, so the first build needs an Internet connection.

## Use

1. Right-click an empty area of the connection list and choose **New**. Enter a profile name, SSH host, SSH port, SSH username, remote destination host and port, and the path to a readable private key. The remote destination must be reachable **from the SSH server**. Your OS username is only an editable suggestion. **Test** checks the SSH connection and local listener without saving the profile or opening a browser, then closes the test tunnel. If you accept an unknown host key during a test, it is saved in `known_hosts`.
2. Choose a fixed local port or automatic port selection. The listener always binds to `127.0.0.1`.
3. Right-click a tunnel row and choose **Connect**. The context menu also provides **Edit**, **Duplicate**, **Delete**, **Disconnect**, and **Open in browser**. On macOS, use a secondary click; with a keyboard, select a row and press Shift+F10. For an unknown host, compare the displayed fingerprint with a trusted source before accepting it. A changed host key is rejected.
4. After the tunnel starts, the application can open `http://127.0.0.1:<actual-local-port>/` in your default browser. **Open in browser** is also available in the context menu.

You can connect multiple profiles at the same time. Changes to a connected profile take effect on its next connection. Deleting an active profile asks for confirmation and disconnects its tunnel first. Disconnecting never removes key files or `known_hosts` entries.

The upper pane lists tunnels; the lower, scrollable pane shows the selected tunnel's connection log. A red status dot means disconnected or error, green means connected, and amber means connecting. Text remains visible next to the dot. Log events are filtered by profile ID and include earlier sessions while those events remain in the rotating log files.

Profiles are stored as versioned UTF-8 JSON in `%APPDATA%/SSH Tunnel Manager/profiles.json` on Windows, `~/Library/Application Support/SSH Tunnel Manager/profiles.json` on macOS, and `${XDG_CONFIG_HOME:-~/.config}/ssh-tunnel-manager/profiles.json` on Linux. Only key paths are saved; key contents and passphrases are not. The initial schema version is 1. Unknown versions are rejected without overwriting the file.

For diagnostics, see `diagnostic.log` beside `profiles.json`. Each entry records the time, profile ID, stage (`SERVER`, `IDENTITY`, `AUTHENTICATION`, or `FORWARD`), outcome, and exception class. The log does not record hosts, usernames, key paths, passphrases, or stack traces. It is limited to about 1 MB with one `.old` archive.

## Security and current limitations

- Authentication uses one explicitly selected private-key file. Encrypted keys prompt for a passphrase. There is no automatic identity scan, SSH password authentication, or SSH agent support.
- Key loading uses PEM/OpenSSH formats supported by Apache MINA SSHD. OpenSSH Ed25519 keys, including encrypted keys, are supported through Bouncy Castle and covered by a local test. Hardware keys, PuTTY PPK files, and unrecognized formats are unsupported.
- The client uses standard `known_hosts` and rejects changed keys. Accept an unknown key only after independently verifying its fingerprint.
- The JSON schema is versioned; there are no older versions to migrate yet.
- A dropped connection is detected when the SSH session closes. A network outage that leaves a socket open may take longer to appear in the UI.

## Native packages

To create a Debian package on Linux amd64 with JDK 21 and Debian packaging tools available:

```sh
./gradlew installDist
jpackage --type deb --name ssh-tunnel-manager --app-version 0.1.0 \
  --vendor Flossconsulting \
  --description 'Desktop SSH local port forward manager' \
  --input build/install/ssh-tunnel-manager/lib \
  --main-jar ssh-tunnel-manager-0.1.0.jar \
  --main-class it.flossconsulting.sshtunnel.Main \
  --dest build/packages --linux-shortcut \
  --linux-app-category Network --linux-menu-group Network \
  --add-modules java.base,java.desktop,java.management,java.naming,java.rmi,java.security.jgss,java.sql,java.logging,jdk.crypto.ec,jdk.unsupported
```

The result is `build/packages/ssh-tunnel-manager_0.1.0_amd64.deb` with a bundled Java runtime. Build it on a Linux distribution compatible with the target system because native dependencies depend on the build OS. `jpackage` can also create `.rpm` on Linux, `.msi`/`.exe` on Windows, and `.dmg`/`.pkg` on macOS. Build native packages on their respective operating systems. Public distribution on macOS and Windows requires signing procedures; macOS may also require notarization.

### Windows

On Windows with JDK 21 and a compatible WiX Toolset available, run these commands in PowerShell from the project directory. Ensure `java`, `jpackage`, and the WiX tools are on `PATH`:

```powershell
.\gradlew.bat test installDist
New-Item -ItemType Directory -Force build\packages | Out-Null
jpackage --type exe --name ssh-tunnel-manager --app-version 0.1.0 `
  --vendor Flossconsulting `
  --description "Desktop SSH local port forward manager" `
  --input build\install\ssh-tunnel-manager\lib `
  --main-jar ssh-tunnel-manager-0.1.0.jar `
  --main-class it.flossconsulting.sshtunnel.Main `
  --dest build\packages --win-menu --win-shortcut `
  --add-modules java.base,java.desktop,java.management,java.naming,java.rmi,java.security.jgss,java.sql,java.logging,jdk.crypto.ec,jdk.unsupported
```

This creates a Windows `.exe` installer with a bundled runtime in `build\packages`. Use `--type msi` for MSI instead. Run the build on the target Windows architecture. The command has not been executed on Windows in this workspace; sign an installer before public distribution.

### macOS

On a Mac with JDK 21 (Intel or Apple Silicon), run from the project directory:

```sh
bash scripts/package-macos.sh
```

The script runs tests, builds the Java distribution, and creates a `.dmg` with a bundled runtime in `build/packages/`. If `JAVA_HOME` is unset, it locates JDK 21 with `/usr/libexec/java_home`. The package targets the architecture of the Mac used to build it; supporting both Intel and Apple Silicon requires two builds. The generated DMG is unsigned and unnotarized. Public distribution requires Apple Developer ID certificates, signing, and notarization. `jpackage` cannot create a macOS DMG on Linux.
