# P2 Acceptance — Direct BYOK Inference

P2 is the direct, user-owned AI provider path. Supabase authentication and Edge Functions are not part of the product and have been removed from the Android implementation.

## Product contract

- The user explicitly configures a personal DeepSeek API key in Settings.
- The key is encrypted at rest with Android Keystore-backed storage.
- The app sends inference requests directly to the official DeepSeek HTTPS endpoint.
- The key is not embedded in the APK, source control, GitHub Actions, app logs, backups, or any shared server secret.
- No request is routed through a Miss Minutes backend or account service.
- If no key is configured, remote inference reports a clear missing-key state.
- An optional local provider is usable only when a supported runtime and model are actually installed.

## Automated acceptance

- Request validation bounds messages, output tokens, tool definitions, and response sizes.
- Direct provider tests cover authentication headers, status mapping, tool schemas, tool-call parsing, and tool-result serialization.
- Local provider tests prove that an unavailable runtime does not fabricate a response.
- Connected plugin tools are supplied to the provider only from the current local consent catalog.
- Tool calls are bounded and dispatched through compiled allowlisted handlers only.

## Not yet accepted

- A live DeepSeek request with a real user-supplied key has not been performed or claimed.
- No local inference backend/model is bundled or target-device verified.
- Fast, Standard, Max, Extra, and Auto profiles still need distinct runtime implementations and tests.
- Production Release signing and physical-device acceptance remain open.