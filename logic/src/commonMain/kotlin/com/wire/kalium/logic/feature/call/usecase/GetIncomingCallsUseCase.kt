package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.conversation.ConversationDetails
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.notification.LocalNotificationCall
import com.wire.kalium.logic.feature.call.CallManager
import com.wire.kalium.logic.feature.call.CallStatus
import com.wire.kalium.logic.feature.call.incomingCalls
import com.wire.kalium.logic.functional.flatMapFromIterable
import com.wire.kalium.logic.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map

interface GetIncomingCallsUseCase {
    suspend operator fun invoke(): Flow<List<LocalNotificationCall>>
}

internal class GetIncomingCallsUseCaseImpl(
    private val callManager: CallManager,
    private val syncManager: SyncManager,
    private val conversationRepository: ConversationRepository
) : GetIncomingCallsUseCase {

    //TODO add tests!
    override suspend operator fun invoke(): Flow<List<LocalNotificationCall>> {
        syncManager.waitForSlowSyncToComplete()
        return callManager.incomingCalls
            .map {
                it.map { call ->
                    LocalNotificationCall(call.conversationId, call.status, "notificationTitle", "colling")
                }
            }
//            .flatMapLatest {
//                val incoming = it.filter { call -> call.status == CallStatus.INCOMING }
//
//                println("cyka calls fetched: ")
//                println("cyka all calls:  $it")
//                println("cyka incoming :  $incoming")
//                incoming.flatMapFromIterable { call ->
//                    conversationRepository.getConversationDetailsById(call.conversationId)
//                        .map { conversationsDetails ->
//                            val notificationTitle: String
//                            val notificationBody: String?
//                            when (conversationsDetails) {
//                                is ConversationDetails.OneOne -> {
//                                    val usersTeam = "" //TODO fetch team
//                                    notificationTitle = "${conversationsDetails.otherUser.name ?: "Someone"} $usersTeam"
//                                    notificationBody = null
//                                }
//                                else -> {
//                                    notificationTitle = conversationsDetails.conversation.name ?: "Somewhere"
//                                    notificationBody = "Someone" //TODO
//                                }
//                            }
//                            LocalNotificationCall(call.conversationId, call.status, notificationTitle, notificationBody)
//                        }
//                }
//            }
    }
}
