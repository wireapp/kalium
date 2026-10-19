Added `Conversation.isGuestAccessEnabled()` to check whether guest links are enabled. It requires both `GUEST` and `NON_TEAM_MEMBER` roles and `CODE` access.

  - ABI: additive on JVM and Kotlin/Native in `core:data`; existing APIs remain compatible.
  - Source: additive.
  - Behavior: existing role helpers retain their meaning. The new helper reports disabled when roles permit guests but code access is absent.
  - Migration: use `isGuestAccessEnabled()` when displaying guest-link availability; continue using the role helpers for individual role permissions.
