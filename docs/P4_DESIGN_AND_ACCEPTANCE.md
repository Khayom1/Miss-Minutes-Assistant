# P4 Design and Acceptance — Local Memory

P4 is local-only conversation history and user-controlled memory. It does not introduce accounts, cloud synchronization, remote PostgreSQL, or server-side retrieval.

## Storage model

- A local guest workspace owns conversations and memory records.
- Conversation and memory content is encrypted before persistence using app-managed keys protected by Android Keystore.
- Queries are scoped to the local workspace; no authenticated server user ID is required.
- No conversation or memory is uploaded automatically.
- Memory sharing with a configured external model is off by default and requires a separate explicit user setting.
- Shared memory is bounded and sent as untrusted context, never as tool instructions.

## User controls

- View, add, edit, and delete user-authored memory.
- Clear conversation history and configure retention.
- Export and restore supported local data with conflict handling.
- Backup archives exclude provider API keys, plugin connection grants, Android permission state, and other credentials.

## Acceptance evidence

- Unit/instrumentation tests cover encryption/decryption, owner isolation within the local guest store, retention, deletion, backup exclusion, restore conflict policy, and memory-context opt-in.
- No cloud sync or account-based recovery is claimed.
- Physical-device persistence and restore remain part of final Samsung Galaxy A35 / Android 17 acceptance.