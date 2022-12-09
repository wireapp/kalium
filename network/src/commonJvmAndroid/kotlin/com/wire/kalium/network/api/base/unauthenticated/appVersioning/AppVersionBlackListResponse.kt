package com.wire.kalium.network.api.base.unauthenticated.appVersioning

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
actual data class AppVersionBlackListResponse(
    @SerialName("oldestAccepted") val oldestAccepted: Int,
    @SerialName("blacklisted") val blacklisted: List<Int>
) {
    actual fun isAppNeedsToBeUpdated(currentAppVersion: Int): Boolean =
        currentAppVersion < oldestAccepted || blacklisted.contains(currentAppVersion)
}

actual fun appVersioningUrlPlatformPath(): String = "android"
