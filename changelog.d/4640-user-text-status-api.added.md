Added `UserScope.updateSelfTextStatus` and `UserScope.refreshSelfUser`, each returning `Result.Success` or `Result.Failure`.

Status text allows up to 256 Unicode code points; null and blank values clear it. Self-profile refreshes and status writes are coordinated within the account.
