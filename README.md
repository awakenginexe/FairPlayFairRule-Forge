# FairPlayFairRule — Play Fair Together (Forge)

FairPlayFairRule (FPFR) is a lightweight, privacy‑respecting companion for fair multiplayer. It verifies consent, collects a clear manifest of client mods and resource packs, and posts human‑readable join summaries with complete manifest attachments to Discord. An optional best-effort Hastebin mirror is available. Built for large modpacks and busy servers.

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
- Disabled by default for backward compatibility; banned-mod checks remain active
- Required, globally optional, per-player optional, server-downloaded, and explicitly banned hash policies
- Exact ordered policy-controlled baseline established only after successful initial validation
- Added, removed, modified, replaced, or reordered custom/server-downloaded packs require reconnecting
- Trusted Minecraft built-ins and proven Forge/mod-bundled resources are diagnostic-only and excluded from the baseline
- User directory and unresolved packs fail closed whenever integrity or pack-ban enforcement requires hashes
- Raw ZIP hashes use a bounded, identity-aware cache with mutation checks

### 🚫 Auto-Ban System
- Server-side configuration for banned mod IDs
- Automatic player kick when banned mods are detected
- Sends high-priority Discord alerts for ban events

### 🔔 Discord Notifications
- **Player Join Notification**: Basic join alert with player info
- **Player Manifest**: Detailed mod and resource pack report attached directly as UTF-8 text
- **Ban Notification**: High-priority alert when banned mods are detected
- **Resource Pack Integrity Violation**: Red high-priority login/runtime rejection alert with authenticated identity and normalized evidence when safe
- Manifest attachments are split on line boundaries at 9 MiB, up to five ordered parts and 45 MiB total
- Optional Hastebin mirroring is disabled by default and never replaces or blocks Discord attachment delivery

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
   hastebinMirrorEnabled = false
   resourcePackIntegrityEnabled = true
   logResourcePackViolations = true
   bannedModIds = ["examplehackmod", "examplecheatmod"]
   bannedPackHashes = ["aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"]
   requiredPackHashes = ["0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"]
   globalApprovedPackHashes = ["1111111111111111111111111111111111111111111111111111111111111111"]
   playerApprovedPackHashes = [
       "550e8400-e29b-41d4-a716-446655440000=2222222222222222222222222222222222222222222222222222222222222222",
       "550e8400-e29b-41d4-a716-446655440000=3333333333333333333333333333333333333333333333333333333333333333"
   ]
   serverDownloadedPackHashes = ["4444444444444444444444444444444444444444444444444444444444444444"]
   ```
4. Save the file. A valid Forge config reload atomically replaces the policy; a malformed or unsafe candidate is rejected and the last-known-good policy stays active.
5. Have already-connected players reconnect so they receive a baseline under the new policy.

### Client Setup
1. Place the mod JAR in the client's `mods/` folder
2. Launch the game
3. Accept the consent screen when prompted

## Configuration

The server configuration file (`config/fairplayfairrule-common.toml`) contains:

- **webhookUrl**: Discord webhook URL for notifications (leave empty to disable)
- **hastebinMirrorEnabled**: Optional best-effort Hastebin mirror; defaults to `false`
- **resourcePackIntegrityEnabled**: Enforces custom/server-downloaded pack policy and session locking; defaults to `false`
- **logResourcePackViolations**: Queues dedicated Discord violation alerts; defaults to `true` and never changes enforcement
- **bannedModIds**: List of mod IDs that trigger automatic bans (case-insensitive)
- **bannedPackHashes**: Explicitly rejected raw ZIP hashes; bans override every approval list
- **requiredPackHashes**: Raw SHA-256 hashes every player must have active
- **globalApprovedPackHashes**: Optional ZIP hashes allowed for every player
- **playerApprovedPackHashes**: Bounded `UUID=SHA256` entries; repeat a UUID to approve multiple hashes
- **serverDownloadedPackHashes**: Expected hashes for packs downloaded from the current server

All configured hashes are trimmed, normalized to lowercase, and must then be exactly 64 hexadecimal characters. Invalid characters are never stripped to manufacture a hash. Duplicate values, invalid UUIDs, and oversized lists make the candidate policy invalid, so the previous last-known-good snapshot remains active.

With `resourcePackIntegrityEnabled = false`, custom approval lists are inactive, no custom-pack session baseline is created, and empty approval lists do not cause an integrity violation. A concise warning is logged if inactive approval entries exist. Explicit pack bans still take precedence and banned-mod behavior remains active.

With `resourcePackIntegrityEnabled = true`, an empty approval policy is intentionally strict: no user-controlled ZIP is accepted. Exact trusted Minecraft built-ins and proven Forge/mod-bundled resources remain diagnostic-only. User ZIPs named `vanilla.zip`, `mod_resources.zip`, `Forge Mods.zip`, or `Programmer Art.zip` are still user packs. Directory packs and active packs whose backing identity cannot be proven are rejected.

Every successful startup/reload logs a bounded summary such as:

```text
Resource-pack integrity policy loaded: enabled=true, required=1, global=1, per-player=2, server-downloaded=1, banned=1, errors=0
```

Connected sessions retain the policy and exact ordered baseline accepted when they joined. Policy reloads apply to future connections. Any policy-controlled pack change—including reordering to another globally approved ZIP—requires reconnecting. Trusted built-in or mod-bundled diagnostic profile changes do not alter that baseline.

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
6. Save `config/fairplayfairrule-common.toml` and check the server log for the sanitized `Resource-pack integrity policy loaded:` summary.
7. Have affected connected players reconnect. Saving reloads the allowlist automatically, but an existing player's validated session baseline remains locked until reconnect.

The command never edits the TOML file, scans the directory, accepts uploads, or accepts an arbitrary filesystem path. Hashing runs on one bounded background worker and that worker is stopped with the server lifecycle.

You can independently calculate the same raw whole-file SHA-256 without starting Minecraft.

PowerShell:

```powershell
(Get-FileHash -Algorithm SHA256 -LiteralPath '.\Faithful 32x.zip').Hash.ToLowerInvariant()
```

Linux:

```bash
sha256sum -- 'Faithful 32x.zip'
```

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
- A strict protocol-3 `ClientInfoPacket` codec with bounded structured resource-pack manifests

### External Services
- **Discord Webhooks** for real-time summary notifications and complete UTF-8 manifest attachments
- **Hastebin** (`https://hst.sh/`) as an optional best-effort mirror when `hastebinMirrorEnabled = true`

Discord delivery uses safe UUID/timestamp filenames and suppresses all allowed mentions. Manifests up to 9 MiB use one `.txt` attachment. Larger manifests are split at existing UTF-8 line boundaries into numbered parts of at most 9 MiB each; concatenating the parts in order reconstructs the original bytes. At most five parts and 45 MiB total are accepted. Beyond that bound, Discord receives the normal summary with a clear attachment error and no manifest or mirror upload is attempted.

Structurally valid rejected resource-pack manifests use the same attachment limits. Malformed, oversized, or unsafe reports generate summary-only alerts and arbitrary raw packet bytes are never echoed. Identical Discord-only alerts are suppressed for 30 seconds per authenticated UUID, phase, violation code, and received identity; enforcement still runs every time.

Enabling Hastebin sends a best-effort copy of manifest/evidence text to a public third-party service. Review that privacy tradeoff before opting in. When disabled, no Hastebin request or wait is created.

## Integrity limitations

FPFR is a cooperative client/server integrity and compliance tool, not undefeatable anti-cheat. It can detect and enforce the state reported by a correctly running client using narrowly classified pack sources, raw ZIP hashes, authenticated server UUIDs, and connection-bound session baselines. A hostile client or modified runtime can falsify client-originated reporting; use server-side controls and moderation appropriate to your threat model.

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
