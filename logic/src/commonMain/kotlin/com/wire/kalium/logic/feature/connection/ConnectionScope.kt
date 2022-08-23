package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository

class ConnectionScope(
    private val connectionRepository: ConnectionRepository,
    private val conversationRepository: ConversationRepository,
) {
    val sendConnectionRequest: SendConnectionRequestUseCase get() = SendConnectionRequestUseCaseImpl(connectionRepository)

    val acceptConnectionRequest: AcceptConnectionRequestUseCase
        get() = AcceptConnectionRequestUseCaseImpl(
            connectionRepository,
            conversationRepository
        )

    val cancelConnectionRequest: CancelConnectionRequestUseCase get() = CancelConnectionRequestUseCaseImpl(connectionRepository)

    val ignoreConnectionRequest: IgnoreConnectionRequestUseCase get() = IgnoreConnectionRequestUseCaseImpl(connectionRepository)

    val markConnectionRequestAsNotified: MarkConnectionRequestAsNotifiedUseCase
        get() = MarkConnectionRequestAsNotifiedUseCaseImpl(connectionRepository)

    val blockUser: BlockUserUseCase
        get() = BlockUserUseCaseImpl(connectionRepository)

    val unblockUser: UnblockUserUseCase
        get() = UnblockUserUseCaseImpl(connectionRepository)

}
