# P7 — Bounded Agent Orchestration

Status: core engine, encrypted task-state adapter, P6 registry adapter and a visible two-step diagnostic task panel implemented; lifecycle-worker integration and CI acceptance pending.

## Delivered in this phase branch

- Typed task plans and dependency graphs. Unknown dependencies, duplicate identifiers, cycles, invalid budgets and expired/overlong deadlines are rejected before execution.
- Sequential execution through an injected executor. The executor receives only a typed `PluginExecutionRequest`; model text, shell commands, arbitrary URLs and dynamic code are never executed by this engine.
- Explicit per-node approval gate. Approval is stored as a state transition and is required again only for a node that is still waiting for approval.
- User controls for pause, resume and cancel. Cancellation prevents pending nodes from being dispatched.
- Stable `taskId:nodeId` idempotency keys are supplied to the execution adapter.
- Bounded graph size (24 nodes), step budget, 24-hour deadline, result length, task count and encrypted storage size.
- State is persisted before dispatch and after the result. A task found in RUNNING after process restart is changed to RECOVERY_REQUIRED and is never automatically replayed, avoiding blind duplicate side effects.
- Android SharedPreferences adapter encrypts the complete bounded task snapshot using the existing Android Keystore AES-GCM ConversationCipher. The in-memory store is for deterministic tests only.
- JVM tests cover dependency order, approval, pause/cancel, graph validation, deadlines, step budgets, failed actions and interrupted-execution recovery.\n- Settings exposes a localized Tajik/Russian/English diagnostic task panel behind an explicit expand control; it is composed only when opened. It provides start, next-step, pause, resume and cancel controls.

## Explicit limits

- The engine is a deterministic orchestration core, not a free-form model planner. A future planner must convert model suggestions into this typed graph and pass the same validation and approval gates.
- The execution adapter must route each request through PluginRegistry (or another explicitly allowlisted capability adapter) and must propagate idempotency keys to any remote side effect. No generic shell, arbitrary Android intent or unrestricted accessibility executor is provided.
- This phase does not claim unattended multi-hour work. A lifecycle-aware Android worker/scheduler, foreground-service UX where justified, visible task UI, and adapter wiring are still required before end-to-end P7 acceptance.
- A recovered RUNNING action requires human verification; the engine deliberately does not guess whether an external side effect occurred.

## Acceptance evidence

P7 is not accepted until the exact PR head has passing JVM tests, APK build/lint, API 35 instrumentation, artifact upload and reviewed code diff. Physical Galaxy A35 / Android 17 behavior remains a P8 device check.
