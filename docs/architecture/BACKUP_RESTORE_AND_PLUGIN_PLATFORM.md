# Miss Minutes Backup & Restore Architecture

Status: implementation specification for the offline-first Android application.

## Product invariants

- Core app operation, local history, settings, Android capabilities, and the plugin manager must not require a vendor-operated backend.
- Local Android APIs are core capabilities, not external plugins. Runtime Android permissions remain controlled by Android and must be requested at the point of use.
- External-service plugins (GitHub, YouTube, Google, Telegram, user APIs/servers) are optional integrations. Their network/OAuth/API-key requirements belong to each plugin.
- Tajik, Russian, and English are first-class supported UI languages. Every new visible string, validation error, dialog, notification, and accessibility label must be localized.
- Backup/restore must work offline and survive uninstall/reinstall when the user has safely copied the archive outside app-private storage.

## Backup archive

Use a versioned ZIP container with a manifest and independent category payloads:

- `manifest.json`: format identifier, archive schema version, app version, creation timestamp, selected categories, payload checksums, encryption metadata.
- `chats.json`: conversation metadata, messages, and local memory records when the chat-history category is selected.
- `settings.json`: user preferences only; never include Android runtime permission grants as if they can be restored.
- `plugins/`: one validated package per plugin, including declarative manifest, workflow/source, metadata, requested capability scopes, configuration schema, and dependency references.
- `checksums.json`: SHA-256 for every payload, verified before parsing/import.

The archive must be encrypted using a password-derived key (Argon2id where available, otherwise a reviewed PBKDF2-HMAC-SHA-256 configuration) and authenticated encryption (AES-256-GCM). Never invent a home-grown cryptographic format. Use unique random salt and nonce, bounded KDF parameters, and strict archive size/entry-count limits.

## Export flow

Settings > Backup & Restore > Export:
1. Present exactly three independently selectable categories: Chat history, App settings, Plugins.
2. Show item counts and estimated archive size before export.
3. Ask for archive password and confirmation; explain that losing it means the archive cannot be decrypted.
4. Export to Android Storage Access Framework (user-selected destination), never only app-private cache.
5. Write to a temporary document, finalize atomically where supported, verify checksums, then report success and location.

Single-chat export is available from the chat overflow menu and contains only that chat. It must not bundle plugin packages or other conversations.

Single-plugin export is available from the plugin overflow menu and contains only that plugin package, metadata, config schema, and declared dependencies. It must not bundle chats or credentials.

## Import flow

Settings > Backup & Restore > Import:
1. Select archive through Storage Access Framework.
2. Verify archive signature/checksums, decrypt/authenticate, validate schema and resource limits before modifying app data.
3. Show a preview: archive/app version, timestamps, category availability, chat/plugin counts, compatibility warnings.
4. Let the user independently select Chat history, App settings, Plugins; default to none until explicit selection.
5. Offer explicit conflict policy: keep existing, replace selected category, or merge where safe. Never silently overwrite.
6. Stage all selected data, validate references/dependencies, then commit transactionally. On failure, roll back and preserve current data.
7. Never restore Android OS permission grants. Ask Android for permissions again when needed.
8. Provider credentials/tokens are excluded by default. If a future opt-in encrypted-secret export is implemented, require explicit consent and force OAuth reauthorization where provider policy requires it. Do not restore secrets into logs or plaintext.

## Data model and migration

- Stable IDs for chats and plugins; imported collisions must be handled by the selected conflict policy.
- Archive schema version is independent from app version.
- Provide deterministic migrations from supported older archive schemas.
- Unknown mandatory fields, unsupported future schema versions, malformed paths, duplicate ZIP entries, zip-slip paths, excessive decompression ratios, invalid plugin signatures, or checksum mismatch must fail closed.
- Never execute plugin code during import. Imported packages remain disabled until inspection and explicit user approval.

## Plugin lifecycle

- Plugin manager/catalog is local and works offline for installed packages.
- Plugin builder creates sandboxed declarative/JS/WASM packages, not arbitrary native DEX/APK code.
- Validate manifests, package integrity/signatures, declared capabilities, network host allowlists, timeouts, quotas, and version/dependency constraints.
- Credentials are stored per plugin in Android Keystore-backed encrypted storage and are never exported by default.
- Android device access remains a built-in core capability, outside the external plugin catalog.

## Chat-first UI

- Main chat screen has exactly three primary top-bar controls: navigation/history, new chat, settings. No bottom four-tab navigation.
- Navigation exposes chat history, plugin catalog/builder, and other secondary destinations.
- Settings is a category hub with focused subpages; Backup & Restore is a distinct category.
- Chat overflow: rename, export/share this chat, delete (confirmation).
- Plugin overflow: rename, configure, export/share this plugin package, uninstall/delete (confirmation).

## Required automated tests

- Round-trip export/import for each category and all categories.
- Partial export and partial import combinations.
- Password failure, tampered archive, checksum mismatch, corrupt ZIP, zip-slip, duplicate entry, oversized archive, unsupported schema.
- Merge/replace/keep-existing conflict behavior and rollback on failure.
- Migration from every supported older archive schema.
- Secret redaction; credentials never appear in plaintext archive or logs.
- Single-chat export excludes other chats and plugin files.
- Single-plugin export excludes chats and credentials.
- Imported plugins remain disabled and cannot execute before explicit approval.
- Localization coverage for all three languages, including accessibility labels and error states.
- UI tests for three top-bar controls, overflow actions, and import/export flows.

## Delivery sequence

1. Stabilize current main and repair failing CI.
2. Implement archive models, encryption envelope, validation, and unit tests.
3. Implement chat store export/import adapters and transactional restore.
4. Implement settings export/import adapters.
5. Implement plugin package export/import and local plugin manager.
6. Add chat/plugin overflow menus and Settings Backup & Restore UI.
7. Refactor chat-first navigation and settings category hub.
8. Complete three-language UI audit and API 35 instrumentation.
9. Merge only after exact-head CI passes; verify main CI and APK artifact.

README is intentionally out of scope until the product implementation and UX are stable.
