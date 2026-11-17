# P2 Security Contract — User-Owned BYOK

## Data flow

Android Settings → user enters a personal DeepSeek API key → Android Keystore-backed encryption protects it at rest → the app sends an HTTPS request directly to https://api.deepseek.com/chat/completions.

There is no account service, provider proxy, application backend, or shared provider key.

## Credential controls

- The key is entered by the user and stored only in app-private encrypted storage protected by Android Keystore.
- Plaintext exists briefly in process memory while the HTTPS Authorization header is constructed.
- The key is sent only to the official DeepSeek HTTPS endpoint.
- The key is never embedded in BuildConfig, resources, source control, CI, backup archives, chat history, or diagnostic logs.
- The user can replace or clear the key in Settings and revoke it in their DeepSeek account.
- Invalid-key and rate-limit responses are mapped to typed errors without exposing the key.

## Offline behavior

- Missing key means remote inference is unavailable; there is no hidden server fallback.
- Local inference is available only when a supported runtime and model are installed.
- If the local runtime is absent, the app reports unavailable instead of generating a fake local response.
- Android TTS and local conversation/memory storage remain usable without network access.

## Plugin boundary

The local plugin registry is separate from provider networking. It accepts declarative manifests only and validates actions against compiled allowlisted handlers. Model tool-schema injection and end-to-end dispatch must not be claimed until their dedicated implementation and tests are merged.
