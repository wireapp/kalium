package com.wire.kalium.logic.di

import com.wire.kalium.logic.data.user.UserId

abstract class RootPathsProvider(val rootPath: String) {
    abstract fun rootAccountPath(userId: UserId): String
    abstract fun rootProteusPath(userId: UserId): String
}

expect class PlatformRootPathsProvider(rootPath: String) : RootPathsProvider
