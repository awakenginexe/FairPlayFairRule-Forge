# Direct Discord Manifest Attachments Design

## Objective

Deliver every validated player or ban manifest directly to the configured Discord webhook as UTF-8 text attachments while preserving the existing embed summary. Hastebin remains an opt-in, best-effort mirror and never gates Discord delivery.

## Delivery model

`DiscordWebhookService` captures only server-authoritative player UUID, display name, and immutable manifest inputs on the server thread. It hands those values to a shared, version-independent delivery service backed by bounded executors. No Minecraft or Forge object is accessed by HTTP workers.

The delivery service starts the optional Hastebin request immediately, waits at most 2.5 seconds for a mirror URL, and then submits Discord regardless of Hastebin success, failure, or timeout. Discord uses `wait=true`; success requires a bounded 2xx response containing a message ID and the expected attachment filenames. All HTTP response bodies are capped before buffering.

## Manifest and attachment limits

The manifest is encoded once as UTF-8 with its existing deterministic `\n` layout. Limits are:

- 9 MiB maximum attachment content per part.
- 5 attachment parts maximum.
- 45 MiB maximum total UTF-8 manifest content.

At or below 9 MiB, one file is attached. Above 9 MiB, a greedy splitter cuts only immediately after an existing LF byte. UTF-8 continuation bytes cannot equal LF, so this preserves code points and line endings. Concatenating numbered parts reconstructs the original UTF-8 bytes exactly. A single line larger than 9 MiB, more than five required parts, or more than 45 MiB total produces a summary-only Discord notification with a clear oversized-manifest field; no attachment or Hastebin upload is attempted.

Single-file names use `manifest-<uuid>-<UTC timestamp>.txt`. Multipart names use `manifest-<uuid>-<UTC timestamp>-part-001-of-003.txt`. The UUID comes from `ServerPlayer#getUUID`; the timestamp is UTC `yyyyMMdd'T'HHmmssSSS'Z'`. Player names never enter filenames.

## Multipart safety

One Discord request carries the embed and up to five parts using `payload_json` and ordered `files[0]` through `files[4]` parts. The JSON includes matching attachment metadata and `"allowed_mentions":{"parse":[]}`. Boundaries are high-entropy ASCII tokens; generated filenames contain only fixed ASCII text, UUID characters, timestamp digits, and separators. CRLF framing is constructed from fixed constants, and attachment bytes are supplied as separate body-publisher segments to avoid another combined 45 MiB allocation.

The webhook URI is modified by appending `wait=true` while preserving existing query parameters. Webhook URLs, response bodies, manifest content, local paths, and player names are never logged on failure.

## Optional Hastebin mirror

Add `hastebinMirrorEnabled = false` to the COMMON Forge config. When disabled, no Hastebin request is created. When enabled, upload begins concurrently and may add an optional link to the embed only if a valid response arrives inside 2.5 seconds. Timeout, cancellation, malformed response, non-2xx status, or transport failure is isolated from Discord.

No dpaste or additional public paste service is introduced.

## Lifecycle and compatibility

HTTP, UTF-8 manifest construction, splitting, multipart construction, response verification, and optional mirroring are shared Java services. Forge-specific code remains limited to the existing event registration and server-stop hook. Bounded delivery and HTTP executors shut down on `ServerStoppedEvent` and can be recreated for a later in-process server.

The change does not alter resource-pack validation, session baselines, the administrator hash command, client consent, packet formats, or banned-mod decisions.

## Tests

Pure JUnit tests cover exact multipart structure, exact attachment UTF-8, safe deterministic filenames, mention suppression, exactly 9 MiB, one byte over 9 MiB, UTF-8 line-safe splitting, ordered reconstruction, five-part maximum, 45 MiB total maximum, Hastebin failure/timeout isolation during multipart delivery, Discord non-2xx/malformed responses, bounded responses, and summary-only oversized errors.
