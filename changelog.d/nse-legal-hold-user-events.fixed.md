The NSE no longer acknowledges legal-hold user events without applying them. It stores the self user's legal-hold request as the app does, and leaves `user.legalhold-enable` and `user.legalhold-disable` for the app with `appWorkPending = LEGAL_HOLD`, so the app refreshes the user's clients, updates conversations and writes the legal-hold notices. While legal-hold work left for the app stops receiving before a push's own event, `presentGenericAlert` is set unless `appPresentsAlerts`.

  - ABI: unchanged.
  - Source: unchanged.
  - Behavior: after a user's legal hold is enabled or disabled, later pushes stop at that event until the app runs, and ask for the generic alert.
  - Migration: none for hosts that already present `presentGenericAlert`.
