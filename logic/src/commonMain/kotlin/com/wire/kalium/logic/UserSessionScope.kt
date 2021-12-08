package com.wire.kalium.logic

class UserSessionScope(
    val userSession: UserSession,
    val authenticatedDataSourcesProvider: AuthenticatedDataSourcesProvider
) {

    val conversations: ConversationService get() = ConversationService(userSession)
}
