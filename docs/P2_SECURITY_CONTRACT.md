# P2 AI Provider — Security Contract

## Request path

Android Settings → user enters their DeepSeek API key → Android Keystore encrypts it → Android app sends an HTTPS request directly to DeepSeek Chat Completions.

The Android client calls only `https://api.deepseek.com/chat/completions` for DeepSeek inference. The AI request does not pass through Supabase Edge Functions and does not send Supabase credentials to DeepSeek.

## User-owned API key

- The user enters the personal DeepSeek API key in the app's Settings screen.
- The input is masked and the app does not put the key in Compose saved state, navigation arguments, logs, analytics, crash messages or network diagnostics.
- The key is encrypted using AES-256-GCM with a non-exportable Android Keystore key.
- SharedPreferences contain ciphertext and the IV only, never plaintext.
- The user can replace or remove the key from Settings.
- The key is decrypted only when a request is about to be sent, on an IO dispatcher.
- The key is transmitted only as `Authorization: Bearer ...` over HTTPS to the official DeepSeek API endpoint.
- The key is never embedded in BuildConfig, resources, APK constants, source control, GitHub Actions secrets, Supabase secrets or Supabase database rows.
- A distributed APK cannot safely contain a shared developer API key. Each user must supply their own credential.

## Supabase account session

- Existing Supabase email/password sign-in and encrypted session persistence remain a separate account feature in this increment.
- Supabase session tokens continue to be encrypted with AES-256-GCM using a non-exportable Android Keystore key.
- Supabase JWTs and publishable keys are not sent to DeepSeek.
- Supabase authentication is not used as a proxy for DeepSeek inference.

## Input and output controls

- Maximum 20 user/assistant messages; maximum 8,000 characters per message and 16,000 combined.
- Output is limited by the existing client request contract to at most 768 tokens.
- Requests use HTTPS and bounded connect/read/call timeouts.
- HTTP 401/403 maps to an invalid-key error without signing the user out of Supabase.
- HTTP 429 maps to a rate-limit error; upstream 5xx maps to a sanitized unavailable error.
- Provider response parsing is bounded and malformed responses fail closed.
- API keys and provider error bodies are never returned to the UI or logged.

## Supabase Edge Function status

The older `miss-minutes-ai` function and its server-side DeepSeek secret are no longer part of the Android AI request path. They may remain deployed during migration but are not invoked by the app. They can be retired separately after verifying no other client depends on them.

## Acceptance

- Unit tests verify direct endpoint, key header, missing-key behavior, invalid-key behavior, rate limits and malformed responses.
- Android instrumentation verifies the key round-trips through Keystore encryption and that the persisted preference does not contain plaintext.
- Gitleaks, JVM tests, APK build, lint, API 35 instrumentation and artifact upload must pass on the exact final branch head.
- A real request with a valid user-supplied key is required before claiming live DeepSeek acceptance.
- Physical Samsung Galaxy A35 5G / Android 17 testing is reported only after an actual device test.
