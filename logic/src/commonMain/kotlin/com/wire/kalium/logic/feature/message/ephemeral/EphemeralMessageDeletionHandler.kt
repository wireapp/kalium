package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.util.DateTimeUtil.toIsoDateTimeString
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import com.wire.kalium.util.serialization.toJsonElement
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.coroutines.CoroutineContext

interface EphemeralMessageDeletionHandler {

    fun startSelfDeletion(conversationId: ConversationId, messageId: String)
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

    private suspend fun enqueueSelfDeletion(message: Message.Regular) {
        launch {
            if (message.expirationData != null) {
                ongoingSelfDeletionMessagesMutex.withLock {
                    val isSelfDeletionOutgoing = ongoingSelfDeletionMessages[message.conversationId to message.id] != null
                    kaliumLogger.i(
                        "Self deletion requested for outgoing deletion, skipping: ${
                            ephemeralLoggingDataAsJson(
                                message = message,
                                expirationData = message.expirationData,
                                loggingDeletionStatus = LoggingDeletionStatus.SELF_DELETION_ALREADY_REQUESTED
                            )
                        }"
                    )
                    if (isSelfDeletionOutgoing) return@launch

                    addToOutgoingDeletion(message)
                }
                markDeletionDateAndWait(message, message.expirationData)
                kaliumLogger.i(
                    "Delaying finished, starting the message deletion: ${
                        ephemeralLoggingDataAsJson(
                            message,
                            message.expirationData,
                            LoggingDeletionStatus.WAITING_FOR_DELETION
                        )
                    }"
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
            kaliumLogger.i(
                "Attempting to delete a message for self user as sender: ${
                    ephemeralLoggingDataAsJson(
                        message,
                        message.expirationData!!,
                        LoggingDeletionStatus.ATTEMPT_TO_DELETE
                    )
                }"
            )
            when (val result = deleteEphemeralMessageForSelfUserAsSender(message.conversationId, message.id)) {
                is Either.Left -> {
                    kaliumLogger.i(
                        "Deleting the message for self user as sender FAILED, due to ${result.value}: ${
                            ephemeralLoggingDataAsJson(
                                message,
                                message.expirationData,
                                LoggingDeletionStatus.FAILED_TO_DELETE
                            )
                        }"
                    )
                }

                is Either.Right -> {
                    kaliumLogger.i(
                        "Deleting the message for self user as sender succeed: ${
                            ephemeralLoggingDataAsJson(
                                message,
                                message.expirationData,
                                LoggingDeletionStatus.SUCCEED
                            )
                        }"
                    )
                }
            }
        } else {
            kaliumLogger.i(
                "Attempting to delete a message for self user as receiver: ${
                    ephemeralLoggingDataAsJson(
                        message,
                        message.expirationData!!,
                        LoggingDeletionStatus.ATTEMPT_TO_DELETE
                    )
                }"
            )
            when (val result = deleteEphemeralMessageForSelfUserAsReceiver(message.conversationId, message.id)) {
                is Either.Left -> {
                    kaliumLogger.i(
                        "Deleting the message for self user as sender FAILED, due to ${result.value}: ${
                            ephemeralLoggingDataAsJson(
                                message,
                                message.expirationData,
                                LoggingDeletionStatus.FAILED_TO_DELETE
                            )
                        }"
                    )
                }

                is Either.Right -> {
                    kaliumLogger.i(
                        "Deleting the message for self user as sender succeed: ${
                            ephemeralLoggingDataAsJson(
                                message,
                                message.expirationData,
                                LoggingDeletionStatus.SUCCEED
                            )
                        }"
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
                kaliumLogger.i(
                    "Self deletion not started yet, marking self deletion start date: ${
                        ephemeralLoggingDataAsJson(
                            message,
                            this,
                            LoggingDeletionStatus.SELF_DELETION_NOT_STARTED_YET
                        )
                    }"
                )
                messageRepository.markSelfDeletionStartDate(
                    conversationId = message.conversationId,
                    messageUuid = message.id,
                    deletionStartDate = Clock.System.now()
                )
            }
            kaliumLogger.i(
                "Delaying ${timeLeftForDeletion()} for the next step to delete the message : ${
                    ephemeralLoggingDataAsJson(
                        message,
                        this,
                        LoggingDeletionStatus.WAITING_FOR_DELETION
                    )
                }"
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

private fun ephemeralLoggingDataAsJson(
    message: Message.Regular,
    expirationData: Message.ExpirationData,
    loggingDeletionStatus: LoggingDeletionStatus
): String {
    with(message) {
        return mapOf(
            "message-id" to id,
            "conversation-id" to conversationId,
            "deletion-status" to loggingDeletionStatus.name,
            "expire-after" to expirationData.expireAfter.inWholeSeconds,
            "time-left" to expirationData.timeLeftForDeletion().toString(),
        ).toMutableMap().apply {
            val selfDeletionStatus = expirationData.selfDeletionStatus

            if (selfDeletionStatus is Message.ExpirationData.SelfDeletionStatus.Started) {
                plus("start-date" to selfDeletionStatus.selfDeletionStartDate.toIsoDateTimeString())

                if (loggingDeletionStatus == LoggingDeletionStatus.SUCCEED) {
                    plus("total-expiration-time-passed" to Clock.System.now() - selfDeletionStatus.selfDeletionStartDate)
                }
            }
        }.toJsonElement().toString()
    }
}

private enum class LoggingDeletionStatus {
    SELF_DELETION_ALREADY_REQUESTED,
    SELF_DELETION_NOT_STARTED_YET,
    WAITING_FOR_DELETION,
    ATTEMPT_TO_DELETE,
    FAILED_TO_DELETE,
    SUCCEED
}

