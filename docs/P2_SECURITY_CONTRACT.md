# P2 AI Gateway — Security Contract

## Request path

Android client → Supabase Edge Function `miss-minutes-ai` → Supabase Auth verification → server-only quota RPC → DeepSeek Chat Completions.

The Android application must never call `api.deepseek.com` directly.

## Authentication and authorization

- The Edge Function is deployed with `verify_jwt = true`. The incoming `Authorization: Bearer ...` header is the signed Supabase user JWT, not an API key.
- The Android client sends the user JWT in `Authorization` and the public publishable key in `apikey`.
- The handler separately calls Supabase Auth `/auth/v1/user` to verify the current session and obtain the immutable user ID.
- Missing/invalid sessions, unsupported roles, empty input, oversized bodies and requests whose final message is not from the user are rejected.
- Session access/refresh tokens are encrypted with AES-256-GCM using a non-exportable Android Keystore key. SharedPreferences contain ciphertext only; sign-out invalidates the key and removes the stored ciphertext.
- Passwords are transient UI state only. They are not written to SharedPreferences, SavedState or logs.

## Supabase server credentials

- Prefer the platform-injected `SUPABASE_SECRET_KEYS.default` value when available.
- A modern `sb_secret_...` key is not a JWT: it is sent only in the `apikey` header and must never be placed in `Authorization: Bearer`.
- The legacy `SUPABASE_SERVICE_ROLE_KEY` fallback is a JWT and is sent as both `apikey` and `Authorization: Bearer`.
- These privileged credentials remain server-side. They are never embedded in Android source/BuildConfig or returned to the client.
- The quota RPC is called only after authentication and provider configuration checks.

## DeepSeek provider secret

- DeepSeek uses the server-selected model `deepseek-chat`.
- `DEEPSEEK_API_KEY` is read only from Supabase Edge Function secrets.
- It is not returned, logged, embedded in Android source/BuildConfig or included in responses.
- Missing provider configuration returns sanitized `503 ai_provider_not_configured` without consuming the user's quota.

## Persistent request quota

Remote migration: `20261116181610_miss_minutes_ai_rate_limits`.

- Table: `private.miss_minutes_ai_rate_limits`.
- RLS is enabled with no client policies; the `private` schema is not granted to `anon` or `authenticated`.
- `public.consume_miss_minutes_ai_rate_limit(uuid, integer)` is SECURITY INVOKER, not SECURITY DEFINER.
- Execute is revoked from PUBLIC, `anon` and `authenticated`; only `service_role` can invoke it.
- The Edge Function invokes the RPC with a server-only Supabase secret key using the correct API-key header semantics above.
- Current limit: 12 accepted requests per user per minute; stale buckets older than 24 hours are removed during quota checks.
- Supabase security advisor's sole `INFO rls_enabled_no_policy` finding is intentional deny-by-default on this private table.

## Input and output controls

- Request bodies are streamed and capped at 24,000 bytes before JSON parsing.
- Maximum 20 user/assistant messages; maximum 8,000 characters per message and 16,000 combined.
- Client-supplied system/tool/function roles are rejected; the system instruction and 768-token output ceiling are server-controlled.
- Upstream calls have a 45-second timeout.
- Error bodies are normalized; provider errors and credentials are not returned or logged.

## Deployment and operational status

- Only the new `miss-minutes-ai` Edge Function may be deployed/updated for this project.
- Existing `veyrost-auth` and `veyrost-proxy` functions must remain untouched.
- The private quota migration is applied and its RLS/privilege state has been inspected against the live project.
- The Edge Function is deployed with `verify_jwt=true`; the service-key header compatibility fix must be deployed before P2 acceptance.
- Android email/password sign-in, sign-up, session restore/refresh, encrypted storage, sign-out and authenticated chat wiring are implemented on the P2 branch. CI must still pass on the exact final P2 head.
- A live provider invocation requires a real authenticated Supabase user and `DEEPSEEK_API_KEY` configured in Edge Function secrets. The available Supabase connection does not expose secret management or a real user session, so neither secret presence nor a live provider call can be verified in this run.
- If `DEEPSEEK_API_KEY` is not already configured, the owner must add it through Supabase Dashboard → Edge Functions → Secrets. Never share the value in chat or commit it.
- No physical Samsung Galaxy A35 5G / Android 17 test is claimed.
