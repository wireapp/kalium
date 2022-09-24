package com.wire.kalium.network.networkContainer

import com.wire.kalium.network.UnboundNetworkClient
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApi
import com.wire.kalium.network.api.base.unbound.configuration.ServerConfigApiImpl
import com.wire.kalium.network.api.base.unbound.versioning.VersionApi
import com.wire.kalium.network.api.base.unbound.versioning.VersionApiImpl
import com.wire.kalium.network.defaultHttpEngine
import io.ktor.client.engine.HttpClientEngine

interface UnboundNetworkContainer {
    val serverConfigApi: ServerConfigApi
    val remoteVersion: VersionApi
}

private interface UnboundNetworkClientProvider {
    val unboundNetworkClient: UnboundNetworkClient
}

internal class UnboundNetworkClientProviderImpl(
    val developmentApiEnabled: Boolean,
    engine: HttpClientEngine = defaultHttpEngine()
) : UnboundNetworkClientProvider {
    override val unboundNetworkClient by lazy {
        UnboundNetworkClient(engine)
    }
}

class UnboundNetworkContainerCommon(
    val developmentApiEnabled: Boolean
) : UnboundNetworkContainer, UnboundNetworkClientProvider by UnboundNetworkClientProviderImpl(developmentApiEnabled) {
    override val serverConfigApi: ServerConfigApi get() = ServerConfigApiImpl(unboundNetworkClient)
    override val remoteVersion: VersionApi get() = VersionApiImpl(unboundNetworkClient, developmentApiEnabled)
}
