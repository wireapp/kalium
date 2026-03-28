package com.wire.kalium.usernetwork.di

public typealias ProviderCacheScope = com.wire.kalium.buildflags.ProviderCacheScope

internal val PROVIDER_CACHE_SCOPE: ProviderCacheScope
    get() = UserNetworkBuildConfig.PROVIDER_CACHE_SCOPE
