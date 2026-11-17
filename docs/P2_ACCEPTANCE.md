# P2 Acceptance — Direct BYOK Inference

P2 is the direct, user-owned AI provider path. Account authentication, backend proxying, and server-side inference functions are not part of the product.

## Product contract

- The user explicitly configures a personal DeepSeek API key in Settings.
- The key is encrypted at rest with Android Keystore-backed storage.
- The app sends inference requests directly to the official DeepSeek HTTPS endpoint.
- The key is not embedded in the APK, source control, CI, app logs, backups, or any shared server secret.
- No request is routed through a Miss Minutes backend or account service.
- If no key is configured, remote inference reports a clear missing-key state.
- An optional local provider is usable only when a supported runtime and model are actually installed.

## Automated acceptance

- Request validation bounds message count, text length, output tokens, and response size.
- Direct provider tests cover authorization headers, status mapping, response parsing, and malformed responses.
- Local provider tests prove that an unavailable runtime does not fabricate a response.
- No account/session state is required to open the local workspace.

## Not yet accepted

- A live DeepSeek request with a real user-supplied key has not been performed or claimed.
- No local inference backend/model is bundled or target-device verified.
- Fast, Standard, Max, Extra, and Auto profiles still need distinct runtime implementations and tests.
- Model tool-schema injection and end-to-end plugin dispatch remain a separate release gate.
- Production Release signing and physical-device acceptance remain open.
