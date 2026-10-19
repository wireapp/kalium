Meeting calls no longer create missed-call history messages, or notifications derived from those messages, when cancelled or closed before joining on Android, JVM, and Apple clients. Ordinary one-to-one and group calls retain their missed-call behavior. Call status updates and MLS conference cleanup still run.

  - ABI and source: unchanged.
  - Migration: no consumer changes required. Raw NSE call lifecycle callbacks remain available to the host; this fix does not change how the host presents those callbacks.
