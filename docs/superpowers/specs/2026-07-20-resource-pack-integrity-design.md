# Resource-Pack Integrity and Allowlist Design

## Scope

FairPlayFairRule will replace resource-pack name reporting with structured manifests whose enforcement value is the raw SHA-256 of each complete active ZIP. Validation happens once when client play begins and again whenever Minecraft completes a resource reload. The system remains event-driven: it adds no polling, heartbeat, filesystem watcher, pack upload, canonical ZIP hashing, or local-path transmission.

The existing mod-list validation, consent screen, Discord reporting, and eight-target source-set architecture remain intact.

## Manifest model and trust boundary

Each selected pack becomes one `ResourcePackManifestEntry` containing a bounded display name, raw lowercase SHA-256, byte size, and a type: `ZIP`, `DIRECTORY`, `BUILT_IN`, `SERVER_DOWNLOADED`, or `UNRESOLVED`. Paths exist only inside the client resolver and hash cache; they are never encoded, logged, or reported.

`BUILT_IN` covers packs supplied by Minecraft or Forge/mod resources. It is determined from pack origin/source and known Forge-managed identifiers, not from an arbitrary user filename. A `file/` pack from the resource-pack directory is always user-provided. A current-server downloaded pack is classified separately and must resolve to a local ZIP and produce a hash. Custom and downloaded unresolved packs fail closed.

The client manifest is compliance evidence, not cryptographic attestation. The server validates every field, normalizes hashes, enforces bounds, rejects invalid or empty custom hashes, rejects unsupported directories, and rejects duplicate custom hashes.

## Client collection and cache

`ResourcePackManifestService` enumerates only `PackRepository.getSelectedPacks()`. A compatibility `ResourcePackResolver` converts each selected Minecraft `Pack` to an internal origin/type plus an optional local `Path`. User pack resolution is containment-checked beneath the normalized resource-pack directory. Reflection needed for downloaded pack internals is isolated in compatibility source sets and never appears in shared policy or hashing code.

`ResourcePackHashService` hashes a ZIP as raw bytes with SHA-256. Its bounded in-memory cache key is the normalized absolute path, and its value contains file size, last-modified timestamp, and SHA-256. A cached digest is reused only when both metadata fields match. Metadata is solely an invalidation optimization; the digest remains the enforcement identity.

The existing join event sends an initial report after consent. The existing reload-listener architecture sends a runtime report from reload completion. Hashing runs off the client game executor where the API allows, while packet send and selected-pack access remain safely coordinated with the client lifecycle.

## Network protocol

The protocol version increases from 1 to 2 on every transport, causing old clients/servers to receive Forge's explicit incompatible-channel response rather than silently using name-only packets. The packet carries a report kind (`JOIN` or `RELOAD`), a bounded mod list, and at most 128 manifest entries. Names, hashes, enum values, and sizes have independent bounds. The packet never contains a path or file content.

Codec failures remain bounded at decode. Semantic failures that reach the handler return a human-readable disconnect message. All transport implementations delegate to the same shared validation entry point.

## Server policy and configuration

The common configuration adds:

- `requiredPackHashes`: hashes every player must have active.
- `globalApprovedPackHashes`: optional hashes allowed for every player.
- `playerApprovedPackHashes`: bounded `UUID=SHA256` entries.
- `serverDownloadedPackHashes`: configured expected hashes for packs delivered by the current server.

Hash input accepts surrounding whitespace and upper/lowercase, then normalizes to 64 lowercase hexadecimal characters. Repeated `playerApprovedPackHashes` entries for one UUID accumulate. Malformed UUIDs, invalid hashes, and exact duplicate `UUID=SHA256` entries are rejected from the effective policy and logged clearly without exposing client paths. Configuration lists are bounded during policy loading even if a backend supplies more values.

The effective user-pack allowlist is required + global + hashes assigned to the player's UUID. Server-downloaded entries are checked against the separate configured server-downloaded set and are not silently admitted through the user-pack allowlist.

## Join and runtime state machine

On `JOIN`, `ResourcePackPolicyService` first validates manifest structure and types, then required hashes and allowed hashes. Only a successful result is stored by `PlayerPackSessionStore`, as the exact normalized set of all active non-built-in pack hashes. Failed joins never create or replace a baseline.

On `RELOAD`, a report arriving before a baseline exists is validated as a candidate join report and may establish a baseline only if it independently passes the full join policy. This prevents a race from causing an incorrect disconnect while still failing closed. Once a baseline exists, the new manifest must be structurally valid and its exact normalized non-built-in hash set must equal the baseline. Any added, removed, modified, replaced, unresolved, or directory pack disconnects the player and requires reconnection, even when the new hash is otherwise approved.

When one removed baseline entry and one added runtime entry share a diagnostic display name, the message may classify it as modified/replaced. Names are never used to accept a pack. Ambiguous cases are reported as added/unapproved or removed. Logout clears session state.

## Error messages and reporting

Validation returns structured failure codes and a complete human-readable disconnect component. Messages identify the display name when available and always include the relevant expected/received hash without any path. Directory, unresolved, missing-required, unapproved, duplicate, malformed, modified/replaced, added, and removed cases have distinct text.

Discord/Hastebin output retains mod and pack reporting but formats only safe manifest fields: display name, type, size, and hash. It never includes paths.

## Tests and compatibility verification

Pure shared services are tested independently of Minecraft classes. Coverage includes required/optional/per-player policy behavior, malformed and duplicate entries, UUID isolation, baseline lifecycle, runtime additions/removals/modifications, cache hits and invalidation, raw-byte hash changes, directory/unresolved rejection, and downloaded-pack policy separation.

Compatibility adapters remain limited to pack resolution, reload registration where APIs require it, and packet transports. The final verification builds Minecraft 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.2, 1.20.4, 1.20.6, and 1.21.1.
