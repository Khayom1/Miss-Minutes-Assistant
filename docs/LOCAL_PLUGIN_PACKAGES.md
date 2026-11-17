# Local plugin packages

Miss Minutes accepts portable, declarative JSON plugin packages. A package describes metadata and action schemas only; it is not executable code.

## Package limits

- File extension: `.missplugin.json`
- Archive schema version: `1`
- Maximum file size: 256,000 bytes
- Maximum manifests per package: 100
- Maximum actions per manifest: 16
- Maximum arguments per action: 8
- Maximum calls per minute: 12
- Maximum handler execution budget: 5,000 ms

Unknown fields, enum values, malformed types, duplicate plugin IDs and unsupported schema versions are rejected.

## Example: alias an existing compiled device-information handler

The following package is valid because `device_info` is already backed by a compiled, read-only Android handler. A manifest cannot introduce a new handler or change the handler's argument schema.

```json
{
  "schemaVersion": 1,
  "plugins": [
    {
      "id": "my_device_reader",
      "version": "1.0.0",
      "displayName": "My device reader",
      "description": "Read Android device information.",
      "capabilities": ["DEVICE_INFORMATION"],
      "maxCallsPerMinute": 6,
      "maxExecutionMillis": 1500,
      "actions": [
        {
          "id": "device_info",
          "capability": "DEVICE_INFORMATION",
          "requiresConfirmation": false,
          "arguments": {}
        }
      ]
    }
  ]
}
```

## Import and connection lifecycle

1. Open Settings and choose **Import plugin package**.
2. Select a local JSON package using Android's document picker.
3. Review the plugin ID, display name, actions and capabilities.
4. Confirm installation. Unsupported handlers and IDs already installed are skipped; an existing manifest is never silently overwritten.
5. The imported plugin remains disconnected. Turn on its persistent connection in Settings only after reviewing its capabilities.
6. The grant is stored locally and checked again immediately before every tool execution.
7. Uninstalling removes the manifest and revokes the persistent grant. If storage fails, the manifest is restored disconnected.

Export writes one selected manifest to a user-chosen local document. The encrypted backup remains a separate workflow and is not required for package import/export.

## Security contract

- Packages contain no Kotlin, DEX, JAR, JavaScript, shell commands, scripts, reflection targets or dynamic code.
- Only action IDs with compiled allowlisted handlers can be installed.
- A manifest's argument schema must exactly match the compiled handler schema.
- Imported packages never include or restore user consent, Android permission grants, API keys, credentials, runtime state or chat history.
- API keys and credentials are never requested in chat.
- Runtime Android permissions and provider credentials are checked at execution time.
- Read-only handlers may execute only when the plugin is connected and all access checks pass.
- Write/destructive capabilities always require explicit confirmation, even if a package sets `requiresConfirmation: false`.
- Uninstall revokes consent; importing a replacement requires uninstalling the existing ID first.

A plugin package is therefore a user-managed configuration of capabilities already shipped in the app, not a way to install new executable functionality.
