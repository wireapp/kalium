Added `CoreLogic.prepareUserSession` and `observeUserSessionPreparation` so apps can open and migrate
user databases away from the main thread before using a session. Typed preparation failures retain
the original storage exception, allowing apps to handle the failure category or rethrow the
underlying exception unchanged.

  - ABI: additive.
  - Source: additive.
  - Behavior: existing `getSessionScope` behavior is unchanged in this release.
  - Migration: apps may adopt the new preparation API before creating database-dependent UI or work.
