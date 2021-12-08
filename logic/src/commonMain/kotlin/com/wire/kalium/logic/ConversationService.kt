package com.wire.kalium.logic

import com.wire.kalium.network.AuthenticatedNetworkContainer

class ConversationService(
    userSession: UserSession,
    authenticatedNetworkContainer: AuthenticatedNetworkContainer
) :
    AuthenticatedService(userSession, authenticatedNetworkContainer) {

    suspend fun getConversations()
}
