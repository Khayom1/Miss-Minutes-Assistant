# P8 — Floating Assistant Overlay

## Scope

The floating overlay is a lightweight, user-controlled launcher for Miss Minutes. It is intentionally separate from the voice service, plugin runtime and agent task scheduler.

## Permission and lifecycle

1. The user opens Settings and taps Show bubble.
2. If Android's draw-over-other-apps permission is missing, the app opens the system permission page. Returning to Miss Minutes does not start the overlay automatically; the user must tap Show bubble again.
3. The app starts a private specialUse foreground service only from that explicit foreground interaction.
4. The service checks Settings.canDrawOverlays again before creating a TYPE_APPLICATION_OVERLAY window.
5. The persistent notification includes a Stop action. Stopping the service removes the window immediately.
6. If permission is revoked or window creation fails, the service exits without retrying or attempting to regain permission.

## Security boundaries

- The service is exported=false; other applications cannot start it directly.
- The overlay only opens MainActivity. It does not invoke plugins, device tools, agent tasks, microphone capture or speech recognition.
- The overlay does not request accessibility-service privileges or root access.
- The app does not start the overlay at boot or after sign-in.
- The overlay permission is not included in backups and is never inferred from plugin consent.
- The Android foreground-service notification exposes a direct stop action.

## Platform notes

- Requires Android's special Display over other apps permission.
- Uses the specialUse foreground-service type on Android 14+ and a low-importance persistent notification.
- Android may still stop the service under force-stop, permission revocation or system resource pressure.
- This component is only a user-started floating launcher surface. Character/avatar rendering is outside the approved product scope.
