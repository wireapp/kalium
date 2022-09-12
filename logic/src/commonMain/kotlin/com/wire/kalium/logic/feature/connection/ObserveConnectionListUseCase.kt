package com.wire.kalium.logic.feature.connection

import com.wire.kalium.logic.data.connection.ConnectionRepository
import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.user.ConnectionState
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map

/**
 * Use Case that listen to any user connection changes
 */
fun interface ObserveConnectionListUseCase {
    /**
     * Use case [ObserveConnectionListUseCase] operation
     *
     * @return a [Flow<List<Connection>>] containing all current connections
     */
    suspend operator fun invoke(): Flow<List<ConversationDetails>>
}

internal class ObserveConnectionListUseCaseImpl internal constructor(
    private val connectionRepository: ConnectionRepository,
) : ObserveConnectionListUseCase {

    override suspend operator fun invoke(): Flow<List<ConversationDetails>> {
        return connectionRepository.observeConnectionRequestList()
            .map { conversationDetails ->
                /** Ignored connections are filtered because they should not be visible
                 *  in conversation list
                 */
                conversationDetails
                    .filter {
                        when (it) {
                            is ConversationDetails.Connection -> it.connection.status != ConnectionState.IGNORED
                            else -> false
                        }
                    }
            }
            .distinctUntilChanged()
    }
}
