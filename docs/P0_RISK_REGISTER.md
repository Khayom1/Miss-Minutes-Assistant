# P0 Risk Register

| ID | Risk | Severity | Mitigation / gate |
|---|---|---:|---|
| R-01 | Android does not allow unrestricted third-party UI automation | Critical | Native APIs, AppFunctions, official APIs; explicit user-directed narrow automation |
| R-02 | Google Play limits AccessibilityService automation for general assistants | Critical | Do not use it as autonomous agent control; policy review before any use |
| R-03 | Provider keys extracted from APK | Critical | Server-side provider proxy and secret storage only |
| R-04 | Model prompt injection invokes unsafe tools | Critical | External policy engine, schemas, allowlists, confirmation |
| R-05 | Local LLM exceeds A35 memory/thermal budget | High | Small quantized model, runtime budget, optional fallback |
| R-06 | Hosted voice incurs cost or network latency | Medium | Hybrid voice, quotas, offline fallback |
| R-07 | Background work restricted by Android lifecycle/battery policy | High | WorkManager/foreground service only for eligible work, visible status |
| R-08 | Cloud memory privacy/sync conflicts | High | Encryption, user controls, conflict strategy, audit |
| R-09 | Plugin supply-chain compromise | Critical | Signed/verified manifests, sandbox, least privilege, revocation |
| R-10 | CI emulator environment differs from Samsung device | High | Keep emulator and physical-device gates distinct |
| R-11 | API/provider behavior changes | Medium | Versioned adapters, contract tests, provider fallback |
