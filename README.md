# FairPlayFairRule

A client/server verification tool with one shared Forge codebase for Minecraft 1.18.2 through 1.21.1.

## Supported versions

The project compiles a correctly-labelled JAR for each tested Minecraft target:

| Minecraft | Forge | Java | Status |
| --- | --- | --- | --- |
| 1.18.2 | 40.3.12 | 17 | Compiles |
| 1.19.2 | 43.5.2 | 17 | Compiles |
| 1.19.4 | 45.4.3 | 17 | Compiles |
| 1.20.1 | 47.4.10 | 17 | Compiles |
| 1.20.2 | 48.1.0 | 17 | Compiles |
| 1.20.4 | 49.2.8 | 17 | Compiles |
| 1.20.6 | 50.2.9 | 21 | Compiles |
| 1.21.1 | 52.1.15 | 21 | Compiles |

Minecraft's mod APIs change across releases. The build chooses a matching transport,
client-event, and text-component adapter while retaining the verification, validation,
and webhook logic in one shared source tree. Releases remain separate target-labelled
JARs, so Forge never tries to load classes intended for a different Minecraft release.

## Overview

FairPlayFairRule is a mod that must be installed on both the client and server to function. It collects client mod lists and resource pack information, validates them against a configurable banned mod list, and sends notifications to Discord via webhook.

## Features

### 🔒 Mandatory Consent Screen
- Displays before the main menu on first launch
- Explains data collection to players
- Blocks access until consent is given

### 📋 Data Collection
- Collects complete mod list with versions (format: `modId@version`)
- Collects a bounded manifest of active resource packs with display name, type, size, and raw whole-ZIP SHA-256
- Never uploads resource-pack files or transmits local filesystem paths
- Automatically re-validates after Minecraft resource reloads

### 🧾 Resource-Pack Integrity
- Required, globally optional, per-player optional, and server-downloaded hash policies
- Exact active hash-set session baseline established only after successful join validation
- Active pack changes require reconnecting, even when a newly enabled pack is otherwise approved
- Unsupported directory and unresolved custom packs fail closed
- Unchanged ZIP files reuse a path/size/last-modified SHA-256 cache

### 🚫 Auto-Ban System
- Server-side configuration for banned mod IDs
- Automatic player kick when banned mods are detected
- Sends high-priority Discord alerts for ban events

### 🔔 Discord Notifications
- **Player Join Notification**: Basic join alert with player info
- **Player Manifest**: Detailed mod and resource pack report
- **Ban Notification**: High-priority alert when banned mods are detected
- Full lists uploaded to Hastebin for easy viewing

### 🔄 Resource Pack Monitoring
- Detects resource pack changes in real-time
- Automatically re-validates and re-sends manifest
- Ensures continuous compliance

## Installation

### Server Setup
1. Place the mod JAR in the server's `mods/` folder
2. Start the server to generate the config file
3. Edit `config/fairplayfairrule-common.toml`:
   ```toml
   ["General Settings"]
   webhookUrl = "YOUR_DISCORD_WEBHOOK_URL_HERE"
   bannedModIds = ["examplehackmod", "examplecheatmod"]
   requiredPackHashes = ["0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"]
   globalApprovedPackHashes = ["1111111111111111111111111111111111111111111111111111111111111111"]
   playerApprovedPackHashes = [
       "550e8400-e29b-41d4-a716-446655440000=2222222222222222222222222222222222222222222222222222222222222222",
       "550e8400-e29b-41d4-a716-446655440000=3333333333333333333333333333333333333333333333333333333333333333"
   ]
   serverDownloadedPackHashes = ["4444444444444444444444444444444444444444444444444444444444444444"]
   ```
4. Save the file. Forge reloads the policy automatically; a full server restart is not required.
5. Have already-connected players reconnect so they receive a baseline under the new policy.

### Client Setup
1. Place the mod JAR in the client's `mods/` folder
2. Launch the game
3. Accept the consent screen when prompted

## Configuration

The server configuration file (`config/fairplayfairrule-common.toml`) contains:

- **webhookUrl**: Discord webhook URL for notifications (leave empty to disable)
- **bannedModIds**: List of mod IDs that trigger automatic bans (case-insensitive)
- **requiredPackHashes**: Raw SHA-256 hashes every player must have active
- **globalApprovedPackHashes**: Optional ZIP hashes allowed for every player
- **playerApprovedPackHashes**: Bounded `UUID=SHA256` entries; repeat a UUID to approve multiple hashes
- **serverDownloadedPackHashes**: Expected hashes for packs downloaded from the current server

All hashes are normalized to lowercase before comparison and must be exactly 64 hexadecimal characters. Built-in Minecraft/Forge resources do not require allowlist entries. User directory packs are rejected; compress them as ZIP files before approval.

### Administrator Resource-Pack Hash Workflow

The server cannot infer which custom ZIPs an administrator intends to approve. Hash the exact ZIP bytes that will be distributed to players; do not hash an extracted directory or rebuild the ZIP after approval.

1. Create the dedicated input directory if it does not exist:

   ```text
   config/fairplayfairrule/pack-hash-input/
   ```

2. Copy the exact distributed resource-pack ZIP directly into that directory. Subdirectories and symbolic links are not accepted.
3. From the server console, or as an operator with permission level 4, run:

   ```text
   /fpfr packhash "Faithful 32x.zip"
   ```

   To generate a per-player entry at the same time, supply the player's canonical UUID:

   ```text
   /fpfr packhash "Private Pack.zip" 550e8400-e29b-41d4-a716-446655440000
   ```

4. The command prints the file name, byte size, lowercase raw SHA-256, and a copy-ready quoted hash-list entry. With a UUID it also prints a copy-ready `UUID=SHA256` entry.
5. Paste the quoted hash into `requiredPackHashes`, `globalApprovedPackHashes`, or `serverDownloadedPackHashes`. Paste the `UUID=SHA256` value into `playerApprovedPackHashes`.
6. Save `config/fairplayfairrule-common.toml` and check the server log for `Loaded resource-pack integrity policy with 0 configuration error(s)`.
7. Have affected connected players reconnect. Saving reloads the allowlist automatically, but an existing player's validated session baseline remains locked until reconnect.

The command never edits the TOML file, scans the directory, accepts uploads, or accepts an arbitrary filesystem path. Hashing runs on one bounded background worker and that worker is stopped with the server lifecycle.

## Technical Details

### Architecture
- **Package Structure**:
  - `com.example.fairplayfairrule` - Main mod class
  - `com.example.fairplayfairrule.client` - Client-side handlers
  - `com.example.fairplayfairrule.server` - Server-side validation
  - `src/compat` - Forge API adapters selected by the build target
  - `com.example.fairplayfairrule.config` - Configuration system

### Networking
- Forge 40 transport for Minecraft 1.18.2
- Forge 43-47 legacy SimpleChannel transport for 1.19.2 through 1.20.1
- Forge 48-49 channel transport for 1.20.2 and 1.20.4
- Forge 50-52 payload-aware channel transport for 1.20.6 and 1.21.1
- A strict protocol-2 `ClientInfoPacket` codec with bounded structured resource-pack manifests

### External Services
- **Hastebin** (`https://hst.sh/`) for uploading large mod/pack lists
- **Discord Webhooks** for real-time notifications

## Building from Source

Build the default 1.20.1 target:

```powershell
.\gradlew.bat clean build
```

Build another target:

```powershell
.\gradlew.bat clean build -Ptarget=1.18.2
```

Build every target and collect the JARs in `releases/`:

```powershell
.\scripts\build-all.ps1
```

The compiled JAR will be in `build/libs/`.

## Requirements

- Java 17 for targets through Minecraft 1.20.4
- Java 21 for Minecraft 1.20.6 and 1.21.1
- The exact Forge version in the support table for the selected target

## License

MIT License

## Support

For issues or questions, please refer to the project repository.
