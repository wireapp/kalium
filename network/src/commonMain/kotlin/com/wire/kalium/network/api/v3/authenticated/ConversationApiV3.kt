package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v2.authenticated.ConversationApiV2

internal open class ConversationApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConversationApiV2(authenticatedNetworkClient)
