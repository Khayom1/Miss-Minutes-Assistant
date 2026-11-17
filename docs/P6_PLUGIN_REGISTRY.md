# P6 Plugin Registry — Local Connected Tools

Plugins are local declarative manifests paired with compiled application handlers. A manifest describes metadata and a bounded action schema; it never carries executable code.

## Lifecycle

1. Validate manifest identity, version, capabilities, action IDs, argument schemas, and size limits.
2. Install only when every action maps to a compiled allowlisted handler.
3. Ask the user to connect a plugin once. Persist that local consent grant.
4. Discover connected tools automatically for each task. Do not ask the user to activate a plugin on every turn.
5. Recheck Android permission and provider credential state at execution time.
6. Revoke connection and uninstall locally; revocation must persist and be auditable.

## Model integration

- Only connected tools are converted to provider-neutral JSON Schema and supplied to the model.
- The dispatcher accepts only a name in the current connected catalog.
- Arguments must be bounded JSON objects containing only declared primitive fields.
- Unknown names, unknown fields, wrong types, invalid permissions, missing credentials, and unavailable handlers fail closed.
- Consequential writes require explicit user confirmation.
- Audit entries record action metadata and outcomes only; raw arguments and credentials are not logged.

## Security restrictions

No DEX/JAR loading, scripts, shell commands, reflection, arbitrary URLs, unrestricted Android intents, dynamic code downloads, or hidden background actions. A GitHub plugin is not supported until a real authorized GitHub handler exists and passes dedicated tests.

## Current status

The connected catalog, runtime access checker, schema generation, DeepSeek tool-call dispatch, and bounded execution tests are being validated in the current release PR. Permission-request UX for future non-device capabilities and any write-capable connector remain open.

Compiled handlers may impose a mandatory confirmation floor. An imported manifest can add confirmation, but cannot turn confirmation off for a mutating handler. The dispatcher checks the compiled handler policy immediately before execution.
