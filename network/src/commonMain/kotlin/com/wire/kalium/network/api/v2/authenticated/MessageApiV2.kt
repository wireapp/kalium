package com.wire.kalium.network.api.v2.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.base.authenticated.message.EnvelopeProtoMapper
import com.wire.kalium.network.api.v0.authenticated.MessageApiV0

internal open class MessageApiV2 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient,
    envelopeProtoMapper: EnvelopeProtoMapper
) : MessageApiV0(authenticatedNetworkClient, envelopeProtoMapper)
