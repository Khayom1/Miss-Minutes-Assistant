# P8 Floating Overlay — User-Controlled Launcher

The floating overlay is a lightweight launcher for Miss Minutes. It is separate from the microphone service, plugin runtime, and task scheduler.

## Contract

- The user explicitly enables Android overlay permission in system settings.
- The overlay appears only after an explicit in-app user action.
- Tapping it opens the existing MainActivity; it does not execute tools, start a task, or begin listening.
- It has a visible close/stop path and does not restart at boot.
- It does not require an account or sign-in.
- It is not an animated character or 3D avatar renderer. A 3D avatar is not part of this product.

## Lifecycle

- Overlay permission is checked before display.
- The overlay service is stopped when the user disables it or revokes permission.
- The launcher is not a bypass for Android background or microphone restrictions.
- Force Stop and OS process management can stop the service; the app must not claim otherwise.

## Acceptance

- Manifest contract and permission-gate tests pass.
- Physical Samsung Galaxy A35 / Android 17 testing must verify drag bounds, touch targets, permission UX, stop behavior, and behavior after process recreation.