package com.wire.kalium.userstorage.di

public typealias ProviderCacheScope = com.wire.kalium.buildflags.ProviderCacheScope

internal val PROVIDER_CACHE_SCOPE: ProviderCacheScope
    get() = UserStorageBuildConfig.PROVIDER_CACHE_SCOPE
