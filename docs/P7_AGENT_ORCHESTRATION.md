# P7 Agent Orchestration — Bounded Local Runtime

The agent runtime plans and executes bounded tasks through local state and the connected plugin catalog. It does not depend on a backend job queue or an always-on server.

## Execution contract

- Convert a user request into a bounded task with explicit steps and observable progress.
- Persist task state locally and recover safely after ordinary process recreation.
- Use Android WorkManager for deferrable work and a visible foreground service only when platform policy requires it.
- The DeepSeek tool loop is bounded to at most two tool rounds and four tool calls per model request.
- Only schemas for currently connected local plugins are sent to the model.
- Tool names are checked against the live connected catalog before dispatch.
- Arguments are strictly parsed and routed only to compiled allowlisted handlers.
- Consequential writes require explicit user confirmation; the current shipped device status tools are read-only.
- Fail closed on missing consent, runtime permission, credentials, audit storage, or handler implementation.

## Android limits

WorkManager is deferrable. Doze can delay work. Force Stop prevents scheduled work from running until the user opens the app again. A floating overlay is a launcher, not an always-on agent. No guarantee of indefinite execution after the UI is closed is made.

## Acceptance gaps

- Verify task progress, persistence, cancellation, and recovery in instrumentation.
- Verify no duplicate execution after retry/recreation.
- Add explicit user approval UI before any consequential write handler is shipped.
- Do not claim GitHub repository modification until a real authorized connector is implemented and tested.