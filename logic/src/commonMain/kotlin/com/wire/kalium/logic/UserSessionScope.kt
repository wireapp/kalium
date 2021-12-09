package com.wire.kalium.logic

import com.wire.kalium.network.AuthenticatedNetworkContainer

class UserSessionScope(
    private val userSession: AuthSession,
    private val authenticatedNetworkContainer: AuthenticatedNetworkContainer
) {

    val conversations: ConversationScope get() = ConversationScope(authenticatedNetworkContainer)
}
