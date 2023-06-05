package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
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
    fun enqueueSelfDeletion(message: Message.Regular)
    fun enqueuePendingSelfDeletionMessages()
}

internal class EphemeralMessageDeletionHandlerImpl(
    private val messageRepository: MessageRepository,
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
                if (message is Message.Regular && message.expirationData != null) enqueueSelfDeletion(message)
            }
        }
    }

    override fun enqueueSelfDeletion(message: Message.Regular) {
        val canBeDeleted = when (message.status) {
            Message.Status.PENDING -> false
            Message.Status.SENT,
            Message.Status.READ,
            Message.Status.FAILED,
            Message.Status.FAILED_REMOTELY -> true
        }
        if (!canBeDeleted) return
        launch {
            if (message.expirationData != null) {
                ongoingSelfDeletionMessagesMutex.withLock {
                    val isSelfDeletionOutgoing =
                        ongoingSelfDeletionMessages[message.conversationId to message.id] != null

                    EphemeralEventLogger.log(
                        LoggingDeletionEvent.SelfDeletionAlreadyRequested(
                            message,
                            message.expirationData
                        )
                    )

                    if (isSelfDeletionOutgoing) return@launch

                    addToOutgoingDeletion(message)
                }
                markDeletionDateAndWait(message, message.expirationData)

                EphemeralEventLogger.log(
                    LoggingDeletionEvent.StartingSelfDeletion(
                        message,
                        message.expirationData
                    )
                )

                deleteMessage(message)
            } else {
                kaliumLogger.i("Self deletion requested for message without expiration data")
            }
        }
    }

    private suspend fun deleteMessage(message: Message.Regular) {
        removeFromOutgoingDeletion(message)

        if (message.isSelfMessage) {
            EphemeralEventLogger.log(
                LoggingDeletionEvent.AttemptingToDelete(
                    message,
                    message.expirationData!!,
                )
            )

            when (val result = deleteEphemeralMessageForSelfUserAsSender(message.conversationId, message.id)) {
                is Either.Left -> {
                    EphemeralEventLogger.log(
                        LoggingDeletionEvent.DeletionFailed(
                            message,
                            message.expirationData,
                            result.value
                        )
                    )
                }

                is Either.Right -> {
                    EphemeralEventLogger.log(
                        LoggingDeletionEvent.SuccessFullyDeleted(
                            message,
                            message.expirationData,
                        )
                    )
                }
            }
        } else {
            EphemeralEventLogger.log(
                LoggingDeletionEvent.AttemptingToDelete(
                    message,
                    message.expirationData!!,
                )
            )

            when (val result = deleteEphemeralMessageForSelfUserAsReceiver(message.conversationId, message.id)) {
                is Either.Left -> {
                    EphemeralEventLogger.log(
                        LoggingDeletionEvent.DeletionFailed(
                            message,
                            message.expirationData,
                            result.value
                        )
                    )
                }

                is Either.Right -> {
                    EphemeralEventLogger.log(
                        LoggingDeletionEvent.SuccessFullyDeleted(
                            message,
                            message.expirationData,
                        )
                    )
                }
            }
        }
    }

    private suspend fun removeFromOutgoingDeletion(message: Message.Regular) {
        ongoingSelfDeletionMessagesMutex.withLock {
            ongoingSelfDeletionMessages - message.conversationId to message.id
        }
    }

    private suspend fun markDeletionDateAndWait(message: Message.Regular, expirationData: Message.ExpirationData) {
        with(expirationData) {
            if (selfDeletionStatus is Message.ExpirationData.SelfDeletionStatus.NotStarted) {
                EphemeralEventLogger.log(
                    LoggingDeletionEvent.StartingSelfDeletion(
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

                EphemeralEventLogger.log(
                    LoggingDeletionEvent.MarkingSelfDeletionStartDate(
                        message,
                        expirationData,
                        deletionStartMark
                    )
                )
            }

            val delayWaitingTime = timeLeftForDeletion()

            EphemeralEventLogger.log(
                LoggingDeletionEvent.WaitingForDeletion(
                    message,
                    expirationData,
                    delayWaitingTime
                )
            )

            delay(timeLeftForDeletion())
        }
    }

    private fun addToOutgoingDeletion(message: Message.Regular) {
        ongoingSelfDeletionMessages[message.conversationId to message.id] = Unit
    }

    override fun enqueuePendingSelfDeletionMessages() {
        launch {
            messageRepository.getEphemeralMessagesMarkedForDeletion()
                .onSuccess { ephemeralMessages ->
                    ephemeralMessages.forEach { ephemeralMessage ->
                        if (ephemeralMessage is Message.Regular) enqueueSelfDeletion(ephemeralMessage)
                    }
                }
        }
    }
}
