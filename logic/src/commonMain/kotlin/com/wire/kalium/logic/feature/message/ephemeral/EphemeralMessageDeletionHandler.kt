package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext

internal interface EphemeralMessageDeletionHandler {

    fun startSelfDeletion(conversationId: ConversationId, messageId: String)
    fun enqueueSelfDeletion(message: Message, expirationData: Message.ExpirationData)
    suspend fun enqueuePendingSelfDeletionMessages()
}

internal class EphemeralMessageDeletionHandlerImpl(
    private val messageRepository: MessageRepository,
    private val selfUserId: UserId,
    private val kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl,
    private val deleteEphemeralMessageForSelfUserAsReceiver: DeleteEphemeralMessageForSelfUserAsReceiverUseCase,
    private val deleteEphemeralMessageForSelfUserAsSender: DeleteEphemeralMessageForSelfUserAsSenderUseCase,
    userSessionCoroutineScope: CoroutineScope
) : EphemeralMessageDeletionHandler, CoroutineScope by userSessionCoroutineScope {
    override val coroutineContext: CoroutineContext
        get() = kaliumDispatcher.default

    private val ongoingSelfDeletionMessagesMutex = Mutex()
    private val ongoingSelfDeletionMessages = mutableMapOf<Pair<ConversationId, String>, Unit>()
    override fun startSelfDeletion(conversationId: ConversationId, messageId: String) {
        launch {
            messageRepository.getMessageById(conversationId, messageId).map { message ->
                if (message.expirationData != null && message.status != Message.Status.Pending) {
                    enqueueSelfDeletion(
                        message = message,
                        expirationData = message.expirationData!!
                    )
                } else {
                    kaliumLogger.i(
                        "Self deletion requested for message without expiration data: ${message.content.getType()}"
                    )
                }
            }
        }
    }

    override fun enqueueSelfDeletion(message: Message, expirationData: Message.ExpirationData) {
        SelfDeletionEventLogger.log(
            LoggingSelfDeletionEvent.InvalidMessageStatus(
                message,
                expirationData
            )
        )

        launch {
            ongoingSelfDeletionMessagesMutex.withLock {
                val isSelfDeletionOutgoing = ongoingSelfDeletionMessages[message.conversationId to message.id] != null

                SelfDeletionEventLogger.log(
                    LoggingSelfDeletionEvent.SelfSelfDeletionAlreadyRequested(
                        message,
                        expirationData
                    )
                )

                if (isSelfDeletionOutgoing) return@launch

                addToOutgoingDeletion(message)
            }
            markDeletionDateAndWait(message, expirationData)

            SelfDeletionEventLogger.log(
                LoggingSelfDeletionEvent.StartingSelfSelfDeletion(
                    message,
                    expirationData
                )
            )

            deleteMessage(message, expirationData)
        }
    }

    private suspend fun deleteMessage(message: Message, expirationData: Message.ExpirationData) {
        removeFromOutgoingDeletion(message)

        if (message.senderUserId == selfUserId) {
            SelfDeletionEventLogger.log(
                LoggingSelfDeletionEvent.AttemptingToDelete(
                    message,
                    expirationData,
                )
            )

            when (val result = deleteEphemeralMessageForSelfUserAsSender(message.conversationId, message.id)) {
                is Either.Left -> {
                    SelfDeletionEventLogger.log(
                        LoggingSelfDeletionEvent.SelfDeletionFailed(
                            message,
                            expirationData,
                            result.value
                        )
                    )
                }

                is Either.Right -> {
                    SelfDeletionEventLogger.log(
                        LoggingSelfDeletionEvent.SuccessfullyDeleted(
                            message,
                            expirationData,
                        )
                    )
                }
            }
        } else {
            SelfDeletionEventLogger.log(
                LoggingSelfDeletionEvent.AttemptingToDelete(
                    message,
                    expirationData,
                )
            )

            when (val result = deleteEphemeralMessageForSelfUserAsReceiver(message.conversationId, message.id)) {
                is Either.Left -> {
                    SelfDeletionEventLogger.log(
                        LoggingSelfDeletionEvent.SelfDeletionFailed(
                            message,
                            expirationData,
                            result.value
                        )
                    )
                }

                is Either.Right -> {
                    SelfDeletionEventLogger.log(
                        LoggingSelfDeletionEvent.SuccessfullyDeleted(
                            message,
                            expirationData
                        )
                    )
                }
            }
        }
    }

    private suspend fun removeFromOutgoingDeletion(message: Message) {
        ongoingSelfDeletionMessagesMutex.withLock {
            ongoingSelfDeletionMessages - message.conversationId to message.id
        }
    }

    private suspend fun markDeletionDateAndWait(message: Message, expirationData: Message.ExpirationData) {
        with(expirationData) {
            if (selfDeletionStatus is Message.ExpirationData.SelfDeletionStatus.NotStarted) {
                SelfDeletionEventLogger.log(
                    LoggingSelfDeletionEvent.StartingSelfSelfDeletion(
                        message,
                        expirationData
                    )
                )

                val deletionStartMark = Clock.System.now()

                messageRepository.markSelfDeletionStartDate(
                    conversationId = message.conversationId,
                    messageUuid = message.id,
                    deletionStartDate = deletionStartMark
                )

                SelfDeletionEventLogger.log(
                    LoggingSelfDeletionEvent.MarkingSelfSelfDeletionStartDate(
                        message,
                        expirationData,
                        deletionStartMark
                    )
                )
            }

            val delayWaitingTime = timeLeftForDeletion()

            SelfDeletionEventLogger.log(
                LoggingSelfDeletionEvent.WaitingForSelfDeletion(
                    message,
                    expirationData,
                    delayWaitingTime
                )
            )

            delay(timeLeftForDeletion())
        }
    }

    private fun addToOutgoingDeletion(message: Message) {
        ongoingSelfDeletionMessages[message.conversationId to message.id] = Unit
    }

    override suspend fun enqueuePendingSelfDeletionMessages() {
        messageRepository.getEphemeralMessagesMarkedForDeletion()
            .onSuccess { ephemeralMessages ->
                ephemeralMessages.forEach { ephemeralMessage ->
                    ephemeralMessage.expirationData?.let { expirationData ->
                        enqueueSelfDeletion(
                            message = ephemeralMessage,
                            expirationData = expirationData
                        )
                    }
                }
            }
    }
}
