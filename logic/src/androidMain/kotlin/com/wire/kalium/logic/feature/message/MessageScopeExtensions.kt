package com.wire.kalium.logic.feature.message

val MessageScope.getPaginatedFlowOfMessagesByConversation
    get() = GetPaginatedFlowOfMessagesByConversationUseCase(messageRepository, slowSyncRepository)
