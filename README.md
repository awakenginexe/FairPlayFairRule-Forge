# FairPlayFairRule

A comprehensive client-side verification tool for Minecraft 1.20.1 servers using Forge.

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
  - `com.example.fairplayfairrule.network` - SimpleChannel-based networking
  - `com.example.fairplayfairrule.config` - Configuration system

### Networking
- Uses Forge 1.20.1 SimpleChannel networking
- Custom `ClientInfoPacket` with manual encode/decode via `FriendlyByteBuf`
- Client-to-server packet direction

### External Services
- **Hastebin** (`https://hst.sh/`) for uploading large mod/pack lists
- **Discord Webhooks** for real-time notifications

## Building from Source

```bash
./gradlew build
```

The compiled JAR will be in `build/libs/`.

## Requirements

- Minecraft 1.20.1
- Forge 47.2.0+
- Java 17+

## License

MIT License

## Support

For issues or questions, please refer to the project repository.
