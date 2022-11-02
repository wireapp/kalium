package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.AuthenticatedWebSocketClient
import com.wire.kalium.network.api.v2.authenticated.NotificationApiV2
import com.wire.kalium.network.tools.ServerConfigDTO

internal open class NotificationApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    authenticatedWebSocketClient: AuthenticatedWebSocketClient,
    serverLinks: ServerConfigDTO.Links
) : NotificationApiV2(authenticatedNetworkClient, authenticatedWebSocketClient, serverLinks)
