package com.wire.kalium.network.api.v3.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapper
import com.wire.kalium.network.api.v2.authenticated.MessageApiV2

internal open class MessageApiV3 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    envelopeProtoMapper: EnvelopeProtoMapper
) : MessageApiV2(authenticatedNetworkClient, envelopeProtoMapper)
