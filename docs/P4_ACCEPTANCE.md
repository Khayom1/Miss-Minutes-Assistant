# P4 Acceptance Evidence

## Scope
Offline-first persistent conversation history and explicit user-managed memory, isolated by authenticated account owner.

## Implemented in this branch
- SQLite schema with versioned migrations (v1 → v2 → v3).
- Conversation titles, message content, memory keys and memory values are encrypted with AES-256-GCM using a non-exportable Android Keystore key.
- Authenticated owner scoping on all conversation and memory reads/writes/deletes.
- Conversation list, reopen, new conversation, per-conversation deletion and confirmed delete-all.
- Explicit JSON export initiated by the user through Android's share sheet.
- User-controlled retention options: 30 days, 90 days, 1 year, or never; cleanup is account-scoped.
- Explicit memory create/update/delete/clear API and UI; no automatic inference or silent saving of personal facts.
- No conversation or memory plaintext is written to analytics or diagnostic logs.
- Instrumentation coverage for encrypted-at-rest records, account isolation, deletion cascade, export, retention, memory management and schema migration.
- UI instrumentation coverage for chat history and user-managed memory controls.

## Security notes
- The SQLite database stores ciphertext for all free-text fields. Owner IDs and timestamps remain queryable metadata.
- AES-GCM associated data binds each encrypted field to its owner, record ID and field type.
- Export intentionally contains plaintext because the user explicitly requests an export; the Android share sheet hands it to a user-selected target.
- Cloud synchronization is not implemented in this phase; local-only operation remains available offline.
- Device backup policy and secure deletion guarantees are reviewed separately in P8. Deleting rows does not guarantee forensic erasure from flash storage.

## Automated acceptance
Pending exact-head CI: Gitleaks, Deno regressions, JVM tests, APK build, lint, API 35 instrumentation and artifact upload.

## Deferred final acceptance
- Physical Samsung Galaxy A35 / Android 17 testing.
- Live DeepSeek BYOK response with the user's own key.
- Hosted ElevenLabs credentials and live playback.
