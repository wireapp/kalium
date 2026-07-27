# iOS NSE AVS extension-safety blocker

**Status:** Production blocker
**Scope:** Kalium notification service extension (NSE)
**Required owner:** AVS artifact maintainers, with Kalium integration follow-up

## Problem

The real Kalium NSE call-notification path links the precompiled
`com.wire:avs-kmp:10.4.9` artifact through `:logic:notification-extension-avs` and
`:domain:calling-notifications`.

The final Release `KaliumNotificationExtensionAvs` binary still:

- imports the undefined Objective-C symbol `_OBJC_CLASS_$_UIApplication`;
- contains and uses the `sharedApplication` selector;
- links AVFoundation audio-session and playback functionality that is not needed by a
  notification-only extension.

`UIApplication.sharedApplication` is unavailable to app extensions. The host target's
`APPLICATION_EXTENSION_API_ONLY=YES` setting cannot make a precompiled dependency safe.
Wrapping the AVS calls in extension-safe Swift or Kotlin also cannot remove those symbols
from the embedded AVS binary.

The cached `com.wire:avs-kmp:10.4.28` artifact contains the same forbidden UIKit references,
so upgrading to that version alone does not fix the issue.

## Impact

The app and NSE correctly build and embed the real Kalium and AVS frameworks, but the NSE
cannot be considered App Store production-ready while this AVS artifact is present. Apple
extension validation can reject the bundle, and the extension may load code that assumes a
full application process.

This spike intentionally has no mock engine, alternate processor, or privacy fallback for
call handling. Real Kalium AVS processing remains mandatory, so this issue must be fixed in
the AVS artifact rather than bypassed in the iOS host.

## Required fix

Publish a dedicated extension-safe, notification-only AVS KMP artifact that:

1. excludes application lifecycle, media capture, audio playback, and other full-app-only
   object files;
2. has no reference to `UIApplication`, `sharedApplication`, or APIs marked unavailable to
   app extensions;
3. exposes only the call-event parsing and incoming/missed/closed notification callbacks
   needed by `notification-extension-avs`;
4. can be linked with app-extension-only enforcement enabled for the complete dependency
   graph.

After publication, update the Kalium version catalog and
`:domain:calling-notifications` dependency to consume that artifact.

## Acceptance criteria

- `nm -u KaliumNotificationExtensionAvs` reports no `UIApplication` symbols.
- `strings KaliumNotificationExtensionAvs` reports no `sharedApplication` selector.
- The NSE and every nested framework build with app-extension-only enforcement.
- A provisioned Release archive passes strict nested-code-signature and embedded-extension
  validation.
- App Store upload validation accepts the archive.
- Physical-device tests prove incoming, missed, and closed call notifications using real
  encrypted payloads and the real AVS callbacks.

## Non-fixes

- Disabling app-extension-only enforcement.
- Hiding AVS behind a wrapper framework.
- Shipping a mock or alternate NSE processor.
- Updating to another full AVS release without verifying its linked symbols.
