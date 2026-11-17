# P3 TTS — Implementation Contract

## Scope
This increment implements text-to-speech only. It does not request microphone permission and does not implement speech-to-text or live voice conversations.

## Request path
Compose assistant message → authenticated Android TTS provider → Supabase Edge Function `miss-minutes-tts` → Supabase Auth verification → private per-user TTS quota → ElevenLabs text-to-speech API → MP3 response → Android MediaPlayer.

## Voice
- Provider: ElevenLabs.
- Voice: Daniel (`onwK4e9ZLuTAKqWW03F9`), natural male profile.
- Model: `eleven_multilingual_v2`.
- Output: MP3 44.1 kHz / 128 kbps.
- Voice ID and model are server-controlled; Android sends text only.

## Security and resource limits
- Edge Function requires JWT verification and independently validates the Supabase user session.
- `ELEVENLABS_API_KEY` remains in Supabase Edge Function secrets only.
- User text is trimmed and limited to 3,000 characters; request body is capped at 12 KB.
- Per-user quota is 5 accepted TTS requests per minute, enforced by a private table and service-role-only RPC.
- MP3 response is capped at 5 MiB; upstream timeout is 60 seconds.
- No provider error body or key is returned or logged.
- Audio is not persisted; Android uses a temporary cache file deleted on completion, stop, or error.
- No microphone permission is added in this TTS-only slice.

## Required deployment configuration
Add `ELEVENLABS_API_KEY` to Supabase Dashboard → Edge Functions → Secrets. Never add it to Android BuildConfig, source, CI logs or chat.

## Acceptance
- Deno tests cover authentication, validation, missing secret, quota, provider errors, bounded MP3 response and CORS.
- Android unit tests cover token forwarding, request limits and error mapping.
- Android CI must pass unit tests, APK build, lint and API 35 instrumentation.
- Live TTS acceptance requires a real Supabase user session and successful audio playback on a physical device. Emulator compile/test success alone does not prove audible playback.
