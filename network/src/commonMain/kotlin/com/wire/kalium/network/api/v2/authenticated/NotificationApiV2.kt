package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.v0.authenticated.NotificationApiV0
import com.wire.kalium.network.tools.ServerConfigDTO

internal open class NotificationApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    authenticatedWebSocketClient: AuthenticatedWebSocketClient,
    serverLinks: ServerConfigDTO.Links
) : NotificationApiV0(authenticatedNetworkClient, authenticatedWebSocketClient, serverLinks)
