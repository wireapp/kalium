package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.v0.authenticated.ConversationApiV0

internal open class ConversationApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : ConversationApiV0(authenticatedNetworkClient)
