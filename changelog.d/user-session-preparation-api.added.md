Added `CoreLogic.prepareUserSession` and `observeUserSessionPreparation` so apps can open and migrate
user databases away from the main thread before using a session.

  - ABI: additive.
  - Source: additive.
  - Behavior: existing `getSessionScope` behavior is unchanged in this release.
  - Migration: apps may adopt the new preparation API before creating database-dependent UI or work.
