# P2 AI Gateway — Security Contract

## Request path

Android client → Supabase Edge Function `miss-minutes-ai` → Supabase Auth verification → server-only quota RPC → DeepSeek Chat Completions.

The Android application must never call `api.deepseek.com` directly.

## Authentication and authorization

- The Edge Function is deployed with `verify_jwt = true`; the Supabase gateway verifies the signed user JWT.
- The handler separately calls Supabase Auth `/auth/v1/user` to ensure the session remains valid and to obtain the user's immutable Auth ID.
- The client sends a user session JWT in `Authorization: Bearer ...` and a publishable key in `apikey`.
- The function rejects missing/invalid sessions, unsupported message roles, empty input, oversized payloads and requests where the final message is not from the user.

## Server-side provider secret

- DeepSeek uses the fixed server-side model `deepseek-chat`.
- `DEEPSEEK_API_KEY` is read only from Edge Function environment secrets.
- The key is not returned, logged, embedded in Android source/BuildConfig, or included in responses.
- Missing key results in a sanitized `503 ai_provider_not_configured`.

## Persistent request quota

Remote migration: `20261116181610_miss_minutes_ai_rate_limits`.

- Table: `private.miss_minutes_ai_rate_limits`.
- RLS is enabled; there are no client policies, so normal client roles cannot read or mutate it.
- The `private` schema is not granted to `anon` or `authenticated`.
- Function `public.consume_miss_minutes_ai_rate_limit(uuid, integer)` is SECURITY INVOKER, not SECURITY DEFINER.
- Execute is revoked from PUBLIC, `anon` and `authenticated`; only `service_role` can invoke it.
- The Edge Function uses server-injected Supabase secret credentials to invoke this RPC.
- Current limit: 12 accepted requests per user per minute; stale buckets older than 24 hours are removed during quota checks.

## Input and output controls

- Maximum 20 user/assistant messages.
- Maximum 8,000 characters per message and 16,000 combined.
- System messages and tool/function roles are rejected from client payloads.
- Server owns the system instruction and max output token limit (768).
- Upstream calls have a 45-second timeout.
- Error bodies are normalized; upstream secrets/provider error details are not exposed.

## Current operational constraints

- The Supabase project already contains separate Veyrost functions. P2 adds a uniquely named Miss Minutes function and must not replace or edit `veyrost-auth` or `veyrost-proxy`.
- The rate-limit schema migration has been applied and verified.
- The Edge Function deployment and live invocation must be verified before P2 is accepted.
- A live DeepSeek call requires a valid Supabase Auth session and `DEEPSEEK_API_KEY` configured in Supabase Edge Function secrets. This credential cannot be provisioned from the available secret-management tools in this session.
- The Android auth/session UI and secure token persistence must be completed before the app can perform an end-to-end user request.
