# Resource-Pack Integrity Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Enforce raw SHA-256 allowlists for every active non-built-in resource-pack ZIP at join and after event-driven resource reloads on all eight Forge targets.

**Architecture:** Shared, Minecraft-independent domain services own manifest validation, policy parsing, hash caching, messages, and session baselines. Shared client/server integration composes those services, while duplicate-FQCN compatibility adapters contain only Forge pack resolution and the existing version-specific transports/events.

**Tech Stack:** Java 17 baseline, Java 21 toolchains for Forge 50+, ForgeGradle 6, JUnit Jupiter 5, Minecraft `FriendlyByteBuf`, Forge `SimpleChannel`.

## Global Constraints

- Hash complete ZIP bytes with raw SHA-256; do not canonicalize ZIP contents.
- Validate only selected/active packs at join and reload completion.
- Add no polling, heartbeat, filesystem watcher, upload, or local-path transmission/logging.
- Fail closed for custom/downloaded unresolved packs and all directory packs.
- Keep player approvals as bounded `UUID=SHA256` entries; accumulate repeated UUIDs and reject/log malformed or duplicate entries.
- Store a baseline only after full join-policy success; compare exact normalized non-built-in hash sets at runtime.
- Preserve consent, mod validation, Discord reporting, and all eight Forge targets.

---

## File structure

- `src/main/java/com/example/fairplayfairrule/resourcepack/*`: pure manifest records, limits, hashing/cache, policy parsing/validation, baseline store, and messages.
- `src/main/java/com/example/fairplayfairrule/client/ResourcePackManifestService.java`: selected-pack collection using the resolver contract and hash service.
- `src/compat/forge40/.../client/ResourcePackResolver.java`: Forge 40 pack-origin and local-path adapter.
- `src/compat/modern/.../client/ResourcePackResolver.java`: Forge 43-52 adapter with isolated reflection fallback for downloaded packs.
- `src/main/java/com/example/fairplayfairrule/network/*`: shared bounded payload and codec.
- Existing `PacketHandler` files: protocol registration only, delegating codec and validation.
- Existing `ClientDataService`, `ClientReloadHandler`, join handlers, `Config`, `ServerValidationService`, `DiscordWebhookService`, and mod entry point: orchestration changes only.
- `src/test/java/com/example/fairplayfairrule/resourcepack/*`: pure unit tests for every policy, baseline, cache, and malformed-manifest behavior.

### Task 1: Pure manifest, policy, and session behavior

**Files:**
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/ResourcePackType.java`
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/ResourcePackManifestEntry.java`
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/ResourcePackLimits.java`
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/HashNormalizer.java`
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/ValidationResult.java`
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/ResourcePackPolicyService.java`
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/PlayerPackSessionStore.java`
- Test: `src/test/java/com/example/fairplayfairrule/resourcepack/ResourcePackPolicyServiceTest.java`
- Test: `src/test/java/com/example/fairplayfairrule/resourcepack/PlayerPackSessionStoreTest.java`
- Modify: `build.gradle`

**Interfaces:**
- Produces: `ResourcePackPolicyService.load(Collection<String>, Collection<String>, Collection<String>, Collection<String>)`, `validateJoin(UUID, List<ResourcePackManifestEntry>)`, and `validateRuntime(SessionBaseline, List<ResourcePackManifestEntry>)`.
- Produces: thread-safe `PlayerPackSessionStore.get/put/clear` whose baseline contains an immutable exact hash set and diagnostic name map.

- [ ] **Step 1: Add JUnit Jupiter and write failing policy tests**

Cover required present/missing, unknown extra, global optional, correct/wrong UUID, repeated UUID accumulation, malformed UUID/hash, exact duplicate config entries, duplicate manifest hashes, empty/invalid hashes, directory/unresolved failures, and downloaded-pack separation. Use fixed 64-character hexadecimal fixtures and assert structured failure codes plus message fragments.

- [ ] **Step 2: Run the focused tests and confirm RED**

Run: `./gradlew test --tests '*ResourcePackPolicyServiceTest'`

Expected: compilation failure because the resource-pack domain types do not exist.

- [ ] **Step 3: Implement minimal immutable domain and join validation**

Normalize with `trim().toLowerCase(Locale.ROOT)` and `^[0-9a-f]{64}$`. Reject over-limit config/manifests, nulls, unsupported types, invalid sizes, empty custom hashes, and duplicate non-built-in hashes. Build the effective user set as required + global + UUID-specific; check `SERVER_DOWNLOADED` only against its dedicated configured set.

- [ ] **Step 4: Run policy tests and confirm GREEN**

Run: `./gradlew test --tests '*ResourcePackPolicyServiceTest'`

Expected: all policy tests pass.

- [ ] **Step 5: Write failing session/runtime tests**

Assert unchanged baseline acceptance, added/removed disconnection, one-name replacement classification, policy-approved additions still rejected, reload-before-baseline candidate join behavior at the orchestration boundary, and logout clearing.

- [ ] **Step 6: Run session tests and confirm RED**

Run: `./gradlew test --tests '*PlayerPackSessionStoreTest'`

Expected: failures for missing baseline/runtime behavior.

- [ ] **Step 7: Implement baseline and exact-set runtime comparison**

Store immutable normalized sets only after caller-supplied successful join validation. Compare sets with `Set.equals`; use names only to diagnose an unambiguous one-removed/one-added replacement.

- [ ] **Step 8: Run both test classes and confirm GREEN**

Run: `./gradlew test --tests 'com.example.fairplayfairrule.resourcepack.*'`

Expected: all domain tests pass.

### Task 2: Raw SHA-256 cache

**Files:**
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/ResourcePackHashService.java`
- Test: `src/test/java/com/example/fairplayfairrule/resourcepack/ResourcePackHashServiceTest.java`

**Interfaces:**
- Produces: `HashResult hash(Path)` with normalized path kept client-local, size, modification time, digest, and cache-hit flag.
- Consumes: only `java.nio.file` and `MessageDigest`; no Minecraft classes.

- [ ] **Step 1: Write failing raw-byte and cache tests**

Create temporary ZIP-shaped byte files, assert known SHA-256, same-metadata cache hit, byte/size invalidation, timestamp invalidation, and a changed byte producing a changed digest. Inject a counting hash function to prove cache reuse without timing assumptions.

- [ ] **Step 2: Run hash tests and confirm RED**

Run: `./gradlew test --tests '*ResourcePackHashServiceTest'`

Expected: compilation failure because the hash service does not exist.

- [ ] **Step 3: Implement bounded metadata cache and stable-read check**

Use normalized absolute paths as keys and access-ordered `LinkedHashMap` capped at 256. Read basic attributes before hashing and after hashing; retry once when size or mtime changes, then fail clearly if the file remains unstable.

- [ ] **Step 4: Run hash tests and confirm GREEN**

Run: `./gradlew test --tests '*ResourcePackHashServiceTest'`

Expected: all hash/cache tests pass.

### Task 3: Client manifest collection and resolution adapters

**Files:**
- Create: `src/main/java/com/example/fairplayfairrule/client/ResolvedResourcePack.java`
- Create: `src/main/java/com/example/fairplayfairrule/client/ResourcePackManifestService.java`
- Create: `src/compat/forge40/java/com/example/fairplayfairrule/client/ResourcePackResolver.java`
- Create: `src/compat/modern/java/com/example/fairplayfairrule/client/ResourcePackResolver.java`
- Modify: `src/main/java/com/example/fairplayfairrule/client/ClientDataService.java`
- Modify: both `ClientJoinHandler.java` files
- Modify: `src/main/java/com/example/fairplayfairrule/client/ClientReloadHandler.java`
- Test: `src/test/java/com/example/fairplayfairrule/resourcepack/ResourcePackManifestServiceBoundaryTest.java`

**Interfaces:**
- Resolver produces built-in, user ZIP/directory, server-downloaded, or unresolved origin plus an optional client-local normalized `Path`.
- Manifest service consumes resolver output and `ResourcePackHashService`; it never exposes a path in manifest entries or exceptions sent to the server.

- [ ] **Step 1: Write failing boundary tests for type-to-manifest conversion**

Use resolver-neutral `ResolvedResourcePack` fixtures to assert built-in exemption encoding, ZIP hashing, directory rejection entry, unresolved entry, downloaded classification, and safe names with no path leakage.

- [ ] **Step 2: Run boundary tests and confirm RED**

Run: `./gradlew test --tests '*ResourcePackManifestServiceBoundaryTest'`

Expected: compilation failure for missing collection types.

- [ ] **Step 3: Implement collection core and serialized single-thread hashing**

Snapshot selected packs on the client thread, perform ZIP reads on one daemon executor in report order, and marshal packet send back through `Minecraft.execute`. Join handlers pass `JOIN`; reload completion passes `RELOAD`.

- [ ] **Step 4: Implement narrow compatibility resolvers**

Classify `PackSource.BUILT_IN`, `vanilla`, `mod_resources`, and `mod:` as built-in; classify `file/` entries beneath `<gameDirectory>/resourcepacks` as user-provided with containment checks; classify `PackSource.SERVER` separately and inspect only its opened pack-resource fields for `Path`/`File` using bounded reflection. Never log or serialize discovered paths.

- [ ] **Step 5: Run boundary tests and compile representative API families**

Run: `./gradlew test --tests '*ResourcePackManifestServiceBoundaryTest'`

Run: `./gradlew compileJava -Ptarget=1.18.2`, `./gradlew compileJava -Ptarget=1.19.2`, and `./gradlew compileJava -Ptarget=1.21.1`

Expected: tests and all three compiles pass.

### Task 4: Shared bounded payload and all transports

**Files:**
- Create: `src/main/java/com/example/fairplayfairrule/network/ClientInfoPayload.java`
- Create: `src/main/java/com/example/fairplayfairrule/network/ClientInfoPayloadCodec.java`
- Modify: all four `src/compat/*/.../network/PacketHandler.java` files
- Modify: all four `ClientPacketSender.java` files
- Test: `src/test/java/com/example/fairplayfairrule/network/ClientInfoPayloadValidationTest.java`

**Interfaces:**
- Payload contains report kind, bounded mod entries, and manifest entries.
- Each transport wrapper delegates encode/decode to one shared codec and invokes `ServerValidationService.validatePlayer` on its required main/server thread.

- [ ] **Step 1: Write failing payload-limit tests**

Assert list caps, name/hash caps, invalid enum ordinal rejection, negative size rejection, and payload construction defensive copies without any path field.

- [ ] **Step 2: Run payload tests and confirm RED**

Run: `./gradlew test --tests '*ClientInfoPayloadValidationTest'`

Expected: compilation failure for missing payload classes.

- [ ] **Step 3: Implement payload/codec and protocol 2 transports**

Use byte report/type ordinals, VarInt list lengths, bounded UTF fields, and a long size. Set every channel protocol to 2 with strict equality. Keep only channel API differences in compatibility files.

- [ ] **Step 4: Run tests and compile all transport families**

Run: `./gradlew test --tests '*ClientInfoPayloadValidationTest'`

Run representative compiles for 1.18.2, 1.20.1, 1.20.4, and 1.21.1.

Expected: tests and compiles pass.

### Task 5: Configuration, server orchestration, messages, and reporting

**Files:**
- Modify: `src/main/java/com/example/fairplayfairrule/config/Config.java`
- Modify: `src/main/java/com/example/fairplayfairrule/server/ServerValidationService.java`
- Create: `src/main/java/com/example/fairplayfairrule/server/ServerPlayerLifecycleHandler.java`
- Modify: `src/main/java/com/example/fairplayfairrule/server/DiscordWebhookService.java`
- Modify: `src/main/java/com/example/fairplayfairrule/FairPlayFairRule.java`
- Modify: `README.md`
- Test: `src/test/java/com/example/fairplayfairrule/resourcepack/ResourcePackValidationFlowTest.java`

**Interfaces:**
- Config exposes four bounded lists and logs policy-load errors on Forge config loading/reloading.
- Server validation preserves banned-mod behavior, validates manifest before notifications, establishes baseline only after success, treats pre-baseline reload as a full join candidate, and clears baseline on logout.

- [ ] **Step 1: Write failing validation-flow tests**

Use a pure coordinator/helper to assert failed joins do not store baselines, successful joins do, pre-baseline reload does not produce a baseline-missing disconnect and stores only after full policy success, and post-baseline changes disconnect.

- [ ] **Step 2: Run flow tests and confirm RED**

Run: `./gradlew test --tests '*ResourcePackValidationFlowTest'`

Expected: failures for missing coordinator behavior.

- [ ] **Step 3: Implement config and server state machine**

Define empty-by-default hash lists with documented examples. Parse on config load/reload and log each rejected element or duplicate. On each report, use the current valid policy snapshot. Disconnect with structured messages; send Discord only safe display fields; register logout cleanup.

- [ ] **Step 4: Run all tests and confirm GREEN**

Run: `./gradlew test`

Expected: all tests pass with zero failures.

### Task 6: Eight-target verification and requirement audit

**Files:**
- Modify only files needed for compatibility fixes discovered by builds.

**Interfaces:**
- Produces eight successful Forge artifacts without changing shared enforcement semantics.

- [ ] **Step 1: Build every supported target from clean state**

Run: `./scripts/build-all.ps1`

Expected: exit 0 and artifacts for 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.2, 1.20.4, 1.20.6, and 1.21.1.

- [ ] **Step 2: Inspect repository diff and forbidden mechanisms**

Run: `git diff --check`

Run: `rg -n "WatchService|ScheduledExecutor|scheduleAtFixedRate|heartbeat|canonical" src`

Expected: no whitespace errors and no polling/watcher/canonical-hash implementation.

- [ ] **Step 3: Re-run fresh tests on the default target**

Run: `./gradlew clean test -Ptarget=1.20.1`

Expected: exit 0 with all tests passing.

- [ ] **Step 4: Audit acceptance criteria against tests and code**

Confirm raw-byte rename invariance, byte-change failure, required/global/player/downloaded policy independence, successful-join-only baseline, reload hook, exact-set lock, cache reuse, directory/unresolved failures, bounded packets, no paths, protocol 2, logout cleanup, and preserved consent/mod/Discord flows.
