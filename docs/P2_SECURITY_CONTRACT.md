# P2 Security Contract — User-Owned BYOK

## Data flow

Android Settings → user enters a personal DeepSeek API key → Android Keystore-backed encryption protects it at rest → the app sends an HTTPS request directly to https://api.deepseek.com/chat/completions.

There is no Supabase account, provider proxy, application backend, or shared provider key.

## Credential controls

- The key is entered by the user and stored only in app-private encrypted storage protected by Android Keystore.
- The plaintext key exists briefly in process memory while the HTTPS Authorization header is constructed.
- The key is sent only to the official DeepSeek HTTPS endpoint.
- The key is never embedded in BuildConfig, resources, source control, CI secrets, backup archives, chat history, or diagnostic logs.
- The user can replace or clear the key in Settings and revoke it in their DeepSeek account.
- Invalid-key and rate-limit responses are mapped to typed errors without exposing the key.

## Offline behavior

- Missing key means remote inference is unavailable; there is no hidden server fallback.
- Local inference is available only when a supported runtime and model are installed.
- If the local runtime is absent, the app reports unavailable instead of generating a fake local response.
- Android TTS and local conversation/memory storage remain usable without network access.

## Tool-call boundary

- The model receives schemas only for locally connected plugins.
- A tool name must match the current connected catalog and a compiled handler.
- Arguments are strict JSON primitives validated against the local schema.
- Permission and credential state is checked at execution time.
- Consequential writes are blocked unless the user explicitly confirms.
- No GitHub repository mutation is supported until a real authorized GitHub handler exists and passes tests.