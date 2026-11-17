# P0 Security Model — Offline-First

## Trust boundaries

- Android application process and app-private storage.
- Android Keystore for encryption of user-owned provider credentials and local data keys.
- Official DeepSeek HTTPS endpoint, reached only after the user configures a personal API key.
- Android OS permissions, foreground services, and user-visible system settings.
- Local plugin manifests, connected-tool catalog, compiled handlers, and audit records.

There is no required account, Supabase project, backend API, provider proxy, hosted voice service, or cloud memory store.

## Required controls

1. The user supplies their own DeepSeek key. It is encrypted at rest with Android Keystore-backed storage and transmitted only to the official DeepSeek HTTPS endpoint.
2. No shared developer API key, service-role key, Supabase token, or other privileged credential is packaged in the APK.
3. Credentials and permission/connection grants are never written to logs or backup archives.
4. Local conversations and memory are encrypted at rest, scoped to the local guest workspace, and controlled by the user.
5. Plugin connection is a durable local consent grant. Android permissions and provider credentials are rechecked at execution time.
6. The plugin registry is closed-world: a tool must be in the connected catalog and have a compiled handler. Unknown tool names, unknown argument fields, invalid types, and missing access are rejected.
7. Consequential writes require explicit confirmation. No arbitrary Kotlin, DEX, JavaScript, shell, reflection, or dynamic intent execution is permitted.
8. Background work is bounded by Android lifecycle and scheduling rules. No indefinite always-on behavior is promised.
9. Microphone use must be user-started, disclosed by Android UI/notification, stoppable, and cancelled on lifecycle shutdown.
10. Release signing material is owner-controlled and must never be committed to source control.

## Failure behavior

- Missing API key: show a typed configuration error; do not fall back to a hidden service.
- No local model installed: report local inference unavailable.
- Missing Android permission or credential: return a typed missing-access result and do not execute.
- Audit persistence failure: fail closed for plugin connection and action execution.
- Process death or Force Stop: preserve local data; resume only through Android-supported scheduling after the user reopens the app where required.

## Explicit non-goals

No account recovery, server-side provider proxy, remote memory synchronization, hosted TTS, 3D avatar renderer, or arbitrary-code plugin marketplace.