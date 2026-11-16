# P0 Test Strategy

## Continuous integration

Every push and pull request to main must run:
- JDK 17 setup.
- Android SDK platform/build tools installation.
- Gradle 8.13 configuration.
- Kotlin/Android compilation.
- Unit tests.
- Debug APK assembly.
- Android Lint with errors treated as failures.

## Test layers

- Unit: identity/configuration, provider routing, memory policy, permission policy, plugin manifest validation, planner and tool schemas.
- Integration: backend authentication, provider proxy, memory synchronization, retries, idempotency, and plugin execution boundaries.
- Android instrumentation: lifecycle, permission state, audio routing, background constraints, AppFunctions availability, and target-device compatibility.
- Security: secret scanning, prompt-injection tool-boundary tests, unauthorized tool rejection, plugin capability escalation, and sensitive-log redaction.
- Device: Samsung Galaxy A35 5G, Android 17, real network transitions, battery/thermal behavior, microphone, audio interruption, and accessibility-policy behavior.

## P0 baseline checks

- `gradle --no-daemon clean testDebugUnitTest assembleDebug lintDebug`
- Verify no legacy model assets or SceneView dependency.
- Verify manifest requests only permissions needed by the foundation.
- Verify cleartext traffic and Android backup are disabled.
- Verify no provider secrets or production credentials are present.
- Upload the debug APK as a CI artifact.

## Evidence

A passing unit-test/build/lint job is not equivalent to a passing emulator or physical-device test. Record each result separately. A missing or cancelled test is not a pass.
