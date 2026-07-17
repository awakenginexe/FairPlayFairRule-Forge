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
- Collects active resource pack list
- Automatically re-sends data when resource packs change

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
   [General Settings]
   webhookUrl = "YOUR_DISCORD_WEBHOOK_URL_HERE"
   bannedModIds = ["examplehackmod", "examplecheatmod"]
   ```
4. Restart the server

### Client Setup
1. Place the mod JAR in the client's `mods/` folder
2. Launch the game
3. Accept the consent screen when prompted

## Configuration

The server configuration file (`config/fairplayfairrule-common.toml`) contains:

- **webhookUrl**: Discord webhook URL for notifications (leave empty to disable)
- **bannedModIds**: List of mod IDs that trigger automatic bans (case-insensitive)

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
- A bounded `ClientInfoPacket` codec with manual `FriendlyByteBuf` encoding

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
