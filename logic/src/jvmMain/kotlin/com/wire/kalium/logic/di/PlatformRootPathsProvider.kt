package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.user.UserId

actual class PlatformRootPathsProvider actual constructor(rootPath: String) : RootPathsProvider(rootPath) {
    override fun rootAccountPath(userId: UserId): String = "$rootPath/${userId.domain}/${userId.value}"
    override fun rootProteusPath(userId: UserId): String = "${rootAccountPath(userId)}/proteus"
}
