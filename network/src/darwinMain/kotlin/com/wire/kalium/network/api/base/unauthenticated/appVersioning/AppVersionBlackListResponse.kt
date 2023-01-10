package com.wire.kalium.network.api.base.unauthenticated.appVersioning

actual class AppVersionBlackListResponse {
    actual fun isAppNeedsToBeUpdated(currentAppVersion: Int): Boolean = false // TODO
}

actual fun appVersioningUrlPlatformPath(): String = "ios"
