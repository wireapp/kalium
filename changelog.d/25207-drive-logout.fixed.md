Logging out cancels initialized Wire Drive uploads and their Cells scope. Account teardown waits for Cells work before closing Apple resources. Sessions that have not used Drive do not initialize it during logout.

  - ABI: additive (`UserSessionScope.isCellsInitialized`).
  - Source: additive.
  - Migration: no caller changes required.
