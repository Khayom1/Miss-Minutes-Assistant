# P4 — Local Conversation History and Memory

## Scope

P4 adds offline-first, account-scoped local conversation history and explicit user-authored memory. It does not introduce cloud synchronization or automatic memory extraction.

## Data model and migrations

- SQLite database: `miss_minutes_local_memory.db`.
- Current schema version: 2.
- Version 1: encrypted conversation titles and message rows, owner-scoped foreign keys and query indexes.
- Version 2 migration: adds encrypted `memory_items` and per-user `user_settings` with retention and memory-context preferences.
- Foreign-key enforcement is enabled; deleting a conversation cascades to its messages.
- Migration tests create a real version-1 database, upgrade it, and verify existing encrypted messages remain readable.

## Encryption and privacy

- Conversation titles, message bodies and saved memory content are encrypted before insertion using AES-256-GCM with a non-exportable Android Keystore key.
- Owner id is included as authenticated additional data, so ciphertext cannot be moved between user scopes and decrypted as another user.
- SQLite stores ciphertext only; the instrumentation suite checks that known plaintext markers do not occur in the database file.
- Conversation and memory queries require the authenticated Supabase user id. The database is local-only; no conversation or memory sync is implemented.
- Android app backup is disabled by the existing manifest.
- Saved memory is user-entered only; the app does not silently extract facts from conversations.
- Sending saved memory to DeepSeek is off by default and requires a separate explicit opt-in. When enabled, bounded saved notes are sent as untrusted context in the next AI request.
- No conversation text is written to analytics or diagnostic logs.
- Limits are explicit: 1,000 conversations, 500 messages per conversation and 1,000 memory items per account. Reaching a limit returns an error; older messages are never silently discarded.
- JSON export is bounded to 2 MB and fails with a user-visible explanation rather than allocating an unbounded export. SQLite secure-delete mode is enabled for row deletion.

## User controls

- History screen lists conversations and supports opening and creating a conversation.
- Individual conversation deletion and delete-all require explicit confirmation.
- Export produces a bounded JSON document containing the current user's local conversations and memory. The UI shows a preview and copies the complete export only after the user taps Copy.
- Settings expose saved-memory add/delete controls.
- Retention options: 30 days, 90 days (default), 365 days, or no age-based expiry. Expired records are pruned when the account's local data is loaded or the retention setting changes.
- Memory-context sharing with DeepSeek is separately controlled and defaults off.

## Automated tests

Instrumentation:
- encrypted message/memory persistence and account isolation;
- ciphertext-at-rest check;
- export content and owner isolation;
- retention deletion and no-expiry mode;
- delete-all behavior;
- memory-context opt-in default and persistence;
- version-1 to version-2 migration preserving data.

JVM:
- memory context omitted by default;
- memory context included only after opt-in;
- bounded message count and total prompt size;
- current user message remains the final message.

## Acceptance gates

- Gitleaks, Deno regression tests, JVM tests, APK assembly, lint, API 35 instrumentation and artifact upload must all pass on the exact P4 head.
- No provider credentials or private conversation content may appear in source, build configuration, logs or diagnostics.
- P4 remains a Draft PR stacked on the tested P3 head until all automated gates pass.
- Physical-device/provider checks deferred to P8 remain explicitly unverified; they do not block P4 implementation.
