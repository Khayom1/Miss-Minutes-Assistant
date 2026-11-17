# P2 Acceptance Evidence

## Result

P2 implementation and automated CI gates passed and the code was merged to `main` through PR #10. **P2 acceptance remains in progress** because the required live DeepSeek request with a real user-owned key has not been performed.

- PR: https://github.com/Khayom1/Miss-Minutes-Assistant/pull/10
- Tested head: `80619230db5259ab159c6ca66c61de14523482ce`
- Merge commit: `42a36b13abd572ddab1df8b31f224aa76efdf0af`
- CI run: https://github.com/Khayom1/Miss-Minutes-Assistant/actions/runs/44016203449
- CI result: success

## Verified CI steps

- Gitleaks tracked-tree scan
- Supabase Edge Function regression tests
- JVM unit tests
- Debug APK assembly
- Android lint
- API 35 instrumentation tests
- Debug APK artifact upload

## Artifact

- Name: `miss-minutes-foundation-debug`
- SHA-256: `82a86bc02cd9d3bbbea79e722006f3075a4154f26c60f1d5d11887d9f6e927ef`
- Artifact retention ended: 2026-12-01

## Legacy authenticated-gateway experiment

The earlier server-side authenticated gateway work in PRs #6 and #7 was not merged and is superseded by the approved direct BYOK architecture. Its CI covered Deno unit tests and an unauthenticated request rejected with HTTP 401; there is no evidence of an authenticated live DeepSeek invocation. The current P2 path does not use that gateway.

## Scope and limitations

- DeepSeek inference uses the user's own API key, encrypted locally with AES-256-GCM and Android Keystore.
- The API key is sent directly to the official DeepSeek HTTPS endpoint; it is not bundled in the APK or sent to Supabase.
- Supabase authentication remains separate from DeepSeek inference.
- Automated tests verify the direct provider contract and encrypted storage.
- A live DeepSeek request with a real user-supplied key has **not** been performed or claimed.
- Physical Samsung Galaxy A35 5G / Android 17 testing has **not** been performed or claimed.

P2 is not accepted until a real DeepSeek response is recorded from the app with a valid user-supplied key. Physical Samsung testing is not claimed and remains a later device-validation item.
