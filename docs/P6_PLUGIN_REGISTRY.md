# P6 — Secure Plugin Registry

## Security boundary

P6 implements a closed-world plugin registry, not an arbitrary code loader. Plugin manifests are declarative metadata and typed argument schemas. They cannot contain scripts, bytecode, class names, reflection targets, URLs, shell commands, or arbitrary Android intents. Only action handlers compiled into the trusted application can execute.

This boundary prevents an imported or model-generated manifest from introducing executable code. A future third-party plugin runtime would require a separately reviewed process sandbox and is not enabled by this phase.

## Manifest contract

- Plugin identifiers, semantic versions, action identifiers and field identifiers use strict allowlisted formats.
- Each action declares exactly one capability and a typed input schema.
- Unknown actions, unknown fields, missing required fields, type mismatches, oversized strings, out-of-range integers and values outside explicit enumerations are rejected before handler invocation.
- Manifest limits: 16 actions, 8 input fields per action, 12 calls/minute maximum, 5-second maximum declared execution budget.
- Runtime output is bounded to 4,000 characters; request input is bounded to 2,000 characters.

## Consent and execution

- Per-plugin user consent is stored in app-private SharedPreferences and defaults to denied.
- Consent is separate from the existing P5 per-device-capability grant.
- Actions requiring confirmation need a fresh per-invocation confirmation.
- A compiled handler must match the action identifier and capability declared by the manifest.
- A global rolling rate limit and per-plugin rolling rate limit apply.
- The audit request must be recorded before handler execution; audit failure blocks execution.
- Exceptions, Android permission failures, time-budget overruns and oversized output fail safely.

## Sandboxing

The registry itself does not execute plugin-supplied code. It dispatches only to a fixed set of compiled handlers supplied by the application. There is no dynamic class loading, WebView JavaScript bridge, arbitrary network client, file API, reflection, shell, or root capability exposed to plugins.

## Built-in integration

The initial built-in android_device_status plugin wraps the existing P5 device-info and battery tools. The plugin consent gate and existing device capability grants must both be enabled. This gives a concrete integration without widening Android permissions.

## Tests and acceptance

- JVM tests cover consent denial, handler allowlisting, typed schema validation, confirmation, rate limits, audit fail-closed behavior, output limits and execution budget.
- Android instrumentation covers persistent per-plugin consent and invalid identifier rejection.
- CI must pass Gitleaks, Deno, JVM tests, APK build, lint, API 35 instrumentation and artifact upload on the exact final P6 head.
