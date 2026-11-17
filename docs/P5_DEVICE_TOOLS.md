# P5 — Safe Android Device Tools

## Scope

P5 introduces a small, explicit Android capability surface for Miss Minutes. The initial implementation intentionally supports only read-only device information, read-only battery status, opening Android Wi-Fi settings, opening this app's system settings, and sharing text that the user manually entered.

This is not a general-purpose Android automation API. It does not provide shell access, root, arbitrary intents, arbitrary file access, unrestricted AccessibilityService, hidden UI control, or silent device-setting mutation.

## Capability model

Every capability has an independent persistent grant stored in app-private SharedPreferences. Missing values are denied. All capabilities start disabled. Revoking one capability does not change any other capability.

| Capability | Android operation | Confirmation |
|---|---|---|
| DEVICE_INFO | Read manufacturer, model, Android release and API level | No |
| BATTERY_STATUS | Read battery percentage through BatteryManager | No |
| OPEN_WIFI_SETTINGS | Launch the documented Wi-Fi settings screen | Required |
| OPEN_APP_SETTINGS | Launch this app's application-details screen | Required |
| SHARE_TEXT | Pass user-entered text (maximum 1,000 characters) to Android Sharesheet | Required |

The Android OS owns the destination UI for settings and sharing. Miss Minutes does not toggle Wi-Fi, change system settings, or choose a receiving app on the user's behalf.

## Authorization and execution policy

- A request must match one of the typed sealed commands; there is no dynamic intent or method dispatch.
- The corresponding capability grant must be enabled before execution.
- External actions require a separate visible confirmation for each invocation.
- Shared text must be non-blank and at most 1,000 characters.
- The engine allows at most 12 tool calls per rolling minute.
- Each host call is measured against a 1.5-second execution budget. Current host operations are local Android system reads or documented intent launches; no network or unbounded background work is performed.
- SecurityException and host failures are converted to safe user-facing results.

## Audit and data minimization

- Audit records contain timestamp, capability identifier and outcome only.
- Command arguments, shared text, conversation content, credentials and returned device values are never written to the audit log.
- The app-private audit file is capped at 64 KiB and rotated by truncation.
- The audit log is not synced to a server or analytics provider.

## User interface

Settings exposes a separate on/off switch for each capability in Tajik, Russian and English. Read-only information and battery actions can be invoked after enabling their capability. Settings launches and text sharing display an explicit confirmation dialog immediately before the OS action.

## Automated tests

JVM:
- default-deny authorization;
- explicit confirmation for external actions;
- confirmed action dispatch;
- oversized share payload rejection before host execution;
- rolling per-minute rate limit;
- over-budget host call reporting.

Android instrumentation:
- all capabilities disabled by default;
- independent grant and revocation persistence;
- audit log metadata is present and user-provided content is absent.

## Acceptance gates

- Gitleaks, Deno regression suites, JVM tests, APK build, lint, API 35 instrumentation and artifact upload must all pass on the exact final P5 head.
- Tests must verify that ungranted or unconfirmed operations never reach the Android host.
- No root, unrestricted accessibility, arbitrary intent dispatch, or hidden background action may be introduced.
- Any future capability that requires a dangerous Android permission must declare it individually, request it only at point of use, explain why it is needed, and fail closed on denial.
- Physical Samsung Galaxy A35 / Android 17 behavior remains deferred to the final P8 user test and is not claimed as verified.
