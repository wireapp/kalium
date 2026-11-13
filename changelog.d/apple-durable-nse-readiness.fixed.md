On Apple, notification readiness now survives the app process. Opening, resuming and releasing an account no longer revoke or republish it, so the notification and share extensions keep receiving after the app crashes, is killed or is stopped during a background release. Registration, credential replacement, logout and session removal still revoke it first; preparation publishes it.

  - ABI: unchanged.
  - Source: unchanged.
  - Behavior: background release no longer runs readiness checks. Hosts should call `CoreLogic.prepareSessionForNotifications` each time sync reaches live, so readiness follows later MLS registration, E2EI enrollment and server configuration changes.
