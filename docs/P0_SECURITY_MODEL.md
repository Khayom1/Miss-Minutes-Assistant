# P0 Security Model

## Trust boundaries

- Android application process.
- Authenticated backend/API gateway.
- AI model providers.
- Local model runtime and downloaded model files.
- Plugin/skill packages.
- Android system services and external applications.
- Local and cloud memory stores.

## Mandatory controls

1. Provider API keys are stored only in backend secret storage. The Android client receives short-lived, scoped application credentials and never receives a provider master key.
2. All client/backend requests use TLS. Cleartext traffic is disabled.
3. Authentication is required for personal memory and privileged tools.
4. Tool calls use typed schemas, allowlists, per-tool permission checks, deadlines, rate limits, and structured results.
5. Model output and retrieved documents are untrusted. Prompt injection cannot grant permissions or alter policy.
6. Plugins are declarative/capability-based, signed or verified, versioned, sandboxed, resource-limited, and revocable.
7. High-impact actions require explicit user confirmation immediately before execution: payments, purchases, money transfers, sending messages externally, deleting data, account/security changes, and irreversible actions.
8. Memory is minimized, inspectable, exportable, deletable, and encrypted at rest where supported. Sync is authenticated and conflict-aware.
9. Sensitive logs redact tokens, message content, and personal data by default.
10. Background tasks have visible status, cancellation, bounded retries, and a user-configurable permission boundary.

## Android-specific policy

Use the narrowest supported API for each operation. AccessibilityService is not a universal remote-control API. Google Play policy prohibits using AccessibilityService for autonomous agent planning and execution in general-purpose assistants; do not declare the app an accessibility tool unless its primary purpose genuinely meets the disability-support criteria. If a narrowly scoped accessibility feature is ever introduced, obtain policy review, provide prominent disclosure and affirmative consent, and keep the automation deterministic and user-directed.

Android AppFunctions may expose app capabilities to trusted agents, but the API surface and integration availability are evolving. Treat them as optional adapters behind feature detection and test on real target devices.

## Threats and mitigations

- Prompt injection → tool policy outside model context; schema validation; approval gates.
- API key extraction → server-side provider proxy; no secrets in APK or logs.
- Malicious plugin → signed manifests; capability allowlists; sandbox; quotas; revocation.
- Unauthorized device action → user-granted permissions; explicit per-action authorization; immutable audit event.
- Memory exfiltration → authenticated access, encryption, data minimization, user controls.
- Replay/duplicate transaction → idempotency keys and server-side transaction state.
- Background abuse → Android foreground/background rules, visible notifications where required, cancellation.
