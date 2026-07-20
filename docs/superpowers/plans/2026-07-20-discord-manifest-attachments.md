# Direct Discord Manifest Attachments Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Send bounded complete UTF-8 manifests to Discord as verified multipart attachments while making Hastebin an opt-in, time-bounded mirror.

**Architecture:** Pure Java builders plan manifest parts, create multipart requests, and coordinate injectable asynchronous HTTP transport. The existing Forge-facing facade snapshots player data, delegates off-thread, and owns lifecycle shutdown; no version-specific HTTP logic is required.

**Tech Stack:** Java 17/21, JDK HttpClient, Gson, Forge 40-52, JUnit 5.

## Global Constraints

- Maximum 9 MiB per attachment, 5 parts, and 45 MiB total.
- Split only after existing LF bytes and preserve exact UTF-8 bytes when concatenated.
- Discord attachment delivery is authoritative; Hastebin defaults disabled and never gates it.
- Use multipart `payload_json`, ordered `files[n]`, empty allowed mentions, `wait=true`, bounded responses, and sanitized logs.
- Keep all HTTP work outside the server tick thread and use only bounded executors/queues.
- Preserve validation, consent, administrator command, packet, and session behavior.

---

### Task 1: UTF-8 attachment planning

**Files:**
- Create: `src/main/java/com/example/fairplayfairrule/server/ManifestAttachmentPlanner.java`
- Create: `src/main/java/com/example/fairplayfairrule/server/ManifestAttachmentPlan.java`
- Test: `src/test/java/com/example/fairplayfairrule/server/ManifestAttachmentPlannerTest.java`

**Interfaces:**
- Produces `ManifestAttachmentPlan plan(UUID playerId, Instant timestamp, byte[] utf8Manifest)`.
- A successful plan contains one to five ordered filename/byte parts; an oversized plan contains a safe reason and no bytes.

- [x] Write failing tests for 9 MiB, 9 MiB plus one byte, multibyte UTF-8 lines, deterministic names/order, exact reconstruction, five parts, 45 MiB, and over-limit rejection.
- [x] Run `./gradlew test --tests '*ManifestAttachmentPlannerTest' -Ptarget=1.20.1` and verify RED because the planner does not exist.
- [x] Implement strict byte bounds and greedy LF-boundary splitting without decoding or rewriting attachment bytes.
- [x] Run the focused test and verify GREEN.

### Task 2: Safe multipart payloads

**Files:**
- Create: `src/main/java/com/example/fairplayfairrule/server/DiscordMultipartPayload.java`
- Create: `src/main/java/com/example/fairplayfairrule/server/DiscordPayloadFactory.java`
- Test: `src/test/java/com/example/fairplayfairrule/server/DiscordMultipartPayloadTest.java`

**Interfaces:**
- Consumes an embed JSON object and ordered attachment parts.
- Produces a bounded content type, body publisher segments, byte count, and test rendering.

- [x] Write failing tests asserting exact UTF-8 file bytes, ordered `files[n]`, safe filenames, attachment metadata, CRLF framing, and `allowed_mentions.parse = []`.
- [x] Run the focused test and verify RED.
- [x] Implement fixed-header multipart segments with a high-entropy boundary and no user-controlled header values.
- [x] Run focused tests and verify GREEN.

### Task 3: Concurrent mirror and verified Discord delivery

**Files:**
- Create: `src/main/java/com/example/fairplayfairrule/server/BoundedHttpTransport.java`
- Create: `src/main/java/com/example/fairplayfairrule/server/DiscordManifestDeliveryService.java`
- Test: `src/test/java/com/example/fairplayfairrule/server/DiscordManifestDeliveryServiceTest.java`

**Interfaces:**
- Injectable transport returns bounded status/body results.
- Delivery starts an optional Hastebin future, waits at most the configured duration, sends Discord with `wait=true`, and verifies message ID and attachment filenames.

- [x] Write failing tests for Hastebin timeout, failure, disabled mirror, multipart oversized delivery, Discord error/malformed response, response limit, query preservation, and summary-only total-limit errors.
- [x] Run focused tests and verify RED.
- [x] Implement bounded JDK HTTP response subscription, mirror isolation, Discord response verification, and sanitized result codes.
- [x] Run focused and complete tests and verify GREEN.

### Task 4: Forge facade, configuration, lifecycle, and documentation

**Files:**
- Modify: `src/main/java/com/example/fairplayfairrule/server/DiscordWebhookService.java`
- Create: `src/main/java/com/example/fairplayfairrule/server/DiscordWebhookLifecycleHandler.java`
- Modify: `src/main/java/com/example/fairplayfairrule/config/Config.java`
- Modify: `src/main/java/com/example/fairplayfairrule/FairPlayFairRule.java`
- Modify: `README.md`

**Interfaces:**
- Adds `hastebinMirrorEnabled = false`.
- Existing public notification methods keep their signatures and behavior but snapshot player values before off-thread delivery.

- [x] Replace blocking Hastebin-first implementation with the tested delivery service while retaining join, manifest, and ban embed summaries.
- [x] Register server-stop shutdown and safe lazy recreation.
- [x] Document attachment delivery, splitting limits, Hastebin opt-in, and failure behavior.
- [x] Run `git diff --check` and targeted scans for common-pool HTTP, unbounded bodies, webhook/body logging, and extra paste services.

### Task 5: Review, matrix verification, and separate commit

**Files:** all files above plus this design and plan.

- [x] Request an independent read-only code review and fix all Critical/Important findings test-first.
- [x] Run `./scripts/build-all.ps1` and require all eight target builds/tests to succeed.
- [x] Count tests/failures/skips, list eight JARs, and confirm a clean security scan.
- [x] Commit only this feature as `feat: attach manifests directly to Discord` and report the full SHA.
