Added `KaliumConfigs.notesToSelfEnabled` and a synced "show notes" user property (`WIRE_SHOW_NOTES`), exposed via `UserScope.observeShowNotesEnabled` and `UserScope.persistShowNotesStatusConfig`. This is the groundwork for the "Notes to self" conversation.

  - ABI: `KaliumConfigs` gains a constructor parameter, so its constructor and `copy` signatures change (binary-incompatible for prebuilt consumers). The new use cases are additive.
  - Source: additive
  - Behavior: `WIRE_SHOW_NOTES` is synced live across clients through `user.properties-set` / `user.properties-delete` events and on slow sync. It is disabled by default. `notesToSelfEnabled` is `false` by default and has no effect yet.
  - Migration: none.
