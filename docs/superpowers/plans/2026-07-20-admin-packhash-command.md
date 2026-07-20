# Administrator Pack Hash Command Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add an operator-only `/fpfr packhash "<file-name>" [player-uuid]` command that safely hashes direct-child ZIP files from the dedicated administrator input directory.

**Architecture:** A pure-Java command service owns filename validation, real-path containment, ZIP validation, raw hashing, formatting, and one bounded asynchronous worker. A shared Forge event handler registers the command and closes the service on server shutdown. Two tiny compatibility adapters bridge the pre-1.20 and 1.20+ `CommandSourceStack.sendSuccess` signatures.

**Tech Stack:** Java 17/21, Forge 40-52, Brigadier, JUnit 5, existing `ResourcePackHashService`.

## Global Constraints

- Read only direct-child ZIP files from `config/fairplayfairrule/pack-hash-input/`.
- Never accept arbitrary paths, uploads, symlinks, directories, or non-ZIP files.
- Hash the raw whole ZIP asynchronously on one bounded worker.
- Never edit the TOML configuration or scan the input directory automatically.
- Preserve all eight supported Forge targets and avoid unrelated refactoring.

---

### Task 1: Pure-Java command boundary and tests

**Files:**
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/ResourcePackHashCommandService.java`
- Create: `src/main/java/com/example/fairplayfairrule/resourcepack/ResourcePackHashCommandResult.java`
- Test: `src/test/java/com/example/fairplayfairrule/resourcepack/ResourcePackHashCommandServiceTest.java`

**Interfaces:**
- Consumes: `ResourcePackHashService.hash(Path)`.
- Produces: `CompletableFuture<ResourcePackHashCommandResult> hash(String fileName, String playerUuid)` and `close()`.

- [ ] **Step 1: Write failing tests**

Cover valid hashing, `/`, `\\`, `..`, drive prefixes, subdirectories, non-regular files, non-ZIP extensions, malformed ZIP content, real-path containment, symlink rejection when supported, UUID rejection, lowercase output, file size, hash-list formatting, and optional per-player formatting.

- [ ] **Step 2: Run the focused test and verify RED**

Run: `./gradlew test --tests '*ResourcePackHashCommandServiceTest' -Ptarget=1.20.1`

Expected: compilation failure because the command service and result do not exist.

- [ ] **Step 3: Implement the minimal service**

Use a single-thread `ThreadPoolExecutor` with an `ArrayBlockingQueue`, validate the filename before resolution, resolve both root and candidate with `toRealPath`, require the candidate's real parent to equal the real root, reject symlinks and non-regular files, validate content with `ZipFile`, and delegate raw hashing to `ResourcePackHashService`.

Format the result as:

```text
File: Faithful32x.zip
Size: 18734219 bytes
SHA-256: abcdef...
Hash-list entry: "abcdef..."
Per-player entry: "550e8400-e29b-41d4-a716-446655440000=abcdef..."
```

- [ ] **Step 4: Run focused and complete tests and verify GREEN**

Run: `./gradlew test --tests '*ResourcePackHashCommandServiceTest' -Ptarget=1.20.1`

Run: `./gradlew test -Ptarget=1.20.1`

Expected: all tests pass.

### Task 2: Forge command and lifecycle wiring

**Files:**
- Create: `src/main/java/com/example/fairplayfairrule/server/ResourcePackAdminCommandHandler.java`
- Create: `src/compat/command-legacy/java/com/example/fairplayfairrule/compat/CommandFeedback.java`
- Create: `src/compat/command-modern/java/com/example/fairplayfairrule/compat/CommandFeedback.java`
- Modify: `src/main/java/com/example/fairplayfairrule/FairPlayFairRule.java`
- Modify: `build.gradle`

**Interfaces:**
- Consumes: command service from Task 1 and `FMLPaths.CONFIGDIR`.
- Produces: operator-only `/fpfr packhash "<file-name>" [player-uuid]`, async feedback, and server-stop shutdown.

- [ ] **Step 1: Add source-set selection for the two feedback signatures**

Select the legacy adapter for 1.18.2, 1.19.2, and 1.19.4, and the supplier-based adapter for 1.20.1 and later.

- [ ] **Step 2: Register the Brigadier command**

Require `source.hasPermission(4)`, use `StringArgumentType.string()` for the file name and an optional `StringArgumentType.word()` UUID, and resolve only against `FMLPaths.CONFIGDIR/fairplayfairrule/pack-hash-input`.

- [ ] **Step 3: Marshal async completion onto the server thread**

Send success through `CommandFeedback`; send sanitized failures without exposing absolute paths. Register a `ServerStoppedEvent` handler that closes and clears the singleton command service so a later in-process server can create a new worker.

- [ ] **Step 4: Compile the oldest, signature-boundary, and newest targets**

Run: `./gradlew compileJava -Ptarget=1.18.2`

Run: `./gradlew compileJava -Ptarget=1.20.1`

Run: `./gradlew compileJava -Ptarget=1.21.1`

Expected: all compile successfully.

### Task 3: Administrator documentation and full verification

**Files:**
- Modify: `README.md`

**Interfaces:**
- Documents the directory workflow, four exact keys, command syntax, automatic config reload, reconnect semantics, and raw distributed-ZIP hashing.

- [ ] **Step 1: Update README administrator workflow**

Document creating `config/fairplayfairrule/pack-hash-input/`, copying the exact distributed ZIP into it, running the command, copying the generated list element, saving the TOML, checking the reload log, and reconnecting affected players.

- [ ] **Step 2: Run formatting and security scans**

Run: `git diff --check`

Run focused searches confirming there is no directory scan, arbitrary path argument, upload, periodic task, or unbounded executor.

- [ ] **Step 3: Build all eight targets**

Run: `./scripts/build-all.ps1`

Expected: successful builds for 1.18.2, 1.19.2, 1.19.4, 1.20.1, 1.20.2, 1.20.4, 1.20.6, and 1.21.1.

- [ ] **Step 4: Commit the completed command**

Stage only command, tests, compatibility adapters, README, build selection, and this plan; then commit with `feat: add administrator resource pack hash command`.
