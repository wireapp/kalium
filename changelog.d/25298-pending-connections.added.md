Search results and user profiles can now expose a pending-connection `ContactStatus`. `UserSearchDetails.contactStatus` carries it, `SearchScope.refreshContactStatuses` (`RefreshContactStatusesUseCase`) refreshes statuses for a set of users, and the feature is gated by the new `KaliumConfigs.pendingConnections` flag (off by default).

  - ABI: `UserSearchDetails` and `KaliumConfigs` constructor, `copy` and `componentN` signatures change; `SearchScope` gains the `refreshContactStatuses` getter. Recompile Kotlin/JVM consumers and rebuild Apple frameworks.
  - Source: existing constructor calls keep working through appended optional parameters. Exhaustive consumers of the user details API (v17+) need to handle the new contact status field.
  - Behavior: with `pendingConnections` disabled, search and profile behavior is unchanged.
  - Migration: set `KaliumConfigs.pendingConnections = true` to opt in and call `refreshContactStatuses` to update statuses of displayed users.
