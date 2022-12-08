package com.wire.kalium.network.api.base.unauthenticated.appVersioning

expect class AppVersionBlackListResponse {
    fun isAppNeedsToBeUpdated(currentAppVersion: Int): Boolean
}

expect fun appVersioningUrlPlatformPath(): String
