# P2 Provider and Backend Design

## Trust boundaries

- Android contains only the Supabase project URL and its **publishable** client key. These values are designed for public mobile clients and do not grant provider access by themselves.
- DeepSeek credentials are read only by the Supabase Edge Function from the backend secret store as `DEEPSEEK_API_KEY`.
- The Android client sends a Supabase user access token to the Edge Function. The Edge Function validates the user through `@supabase/server` with `auth: "user"`; the function is also configured with JWT verification enabled.
- The function calls a fixed DeepSeek HTTPS endpoint and fixed model. The client cannot select an upstream host, model, system prompt, or tool.
- The per-user rate-limit table has RLS enabled and no client policies. Only the backend admin context can call the narrowly scoped rate-limit RPC.
- No provider key is returned to the app, included in error responses, or written to logs.

## Remote request contract

Android sends only:

- `messages`: 1–20 entries, each role is `user` or `assistant`, non-empty content up to 4,000 characters.
- Total message content is capped at 12,000 characters.
- Entire request body is capped at 16 KiB.

The backend fixes the upstream model to `deepseek-flash`, disables streaming and tool calls, sets a 1,024-token output cap, and applies a 25-second timeout. Redirects are rejected. Provider responses are capped at 32 KiB and assistant text at 16,000 characters. Upstream error bodies are never forwarded.

The current database rate limit is 20 requests per authenticated user per 60-second window. If the rate-limit database call fails, the function fails closed.

## Authentication and session lifecycle

The app supports Supabase email/password sign-in and sign-up. The password is sent only to the configured Supabase Auth HTTPS endpoint. Access and refresh tokens are held in memory for this phase; they are not written to preferences, files, logs, or analytics. Expired access tokens are not sent. The user signs in again after app restart or token expiry until P4 implements encrypted persistent session storage and refresh handling.

## Optional local inference

Local inference uses an OpenAI-compatible `/v1/chat/completions` endpoint restricted to loopback hosts (`127.0.0.1` or `localhost`). The default endpoint is `http://127.0.0.1:5000/v1/chat/completions`; it can be overridden with the Gradle property `-PlocalModelUrl=...`, but the runtime host policy still rejects LAN and public hosts.

Cleartext HTTP is disabled globally and permitted only for `localhost` and `127.0.0.1` to support an on-device local model server. Selecting Local never falls back to the cloud provider, preventing an unexpected data transfer. No model weights or native inference runtime are bundled; a compatible local server must already be running on the device.

## Deployment boundary

The implementation targets the existing free Supabase project and adds a dedicated `deepseek-proxy` function plus the `ai_request_limits` table/function. Existing Veyrost Edge Functions are not modified. No new project or paid service is created.

For live requests, the project secret store must contain `DEEPSEEK_API_KEY`. The repository includes only an empty variable-name template. Never paste the provider key into the Android app, Gradle properties, GitHub, source control, or ChatGPT. Configure it directly in the Supabase Edge Function secret store.

CI uses deterministic mocked upstream responses. It does not make paid/live DeepSeek requests and does not claim that a live provider call succeeded. The existing Supabase project and function deployment can be inspected independently; a successful mock test is not a live-provider test.

## Known P2 limitations

- Session tokens are memory-only; P4 owns encrypted persistence, refresh and cloud-memory lifecycle.
- The local adapter requires a compatible loopback server and bundled model/runtime work is not included.
- Live provider invocation requires the backend secret and a signed-in Supabase user.
- No user conversation history is persisted; that remains P4.
