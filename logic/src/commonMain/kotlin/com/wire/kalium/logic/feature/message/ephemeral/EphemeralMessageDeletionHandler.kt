package com.wire.kalium.logic.feature.message.ephemeral

import com.wire.kalium.logic.CoreFailure
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
import kotlinx.datetime.Instant
import kotlin.coroutines.CoroutineContext
import kotlin.time.Duration

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
                    val isSelfDeletionOutgoing =
                        ongoingSelfDeletionMessages[message.conversationId to message.id] != null

                    logEphemeralEvent(
                        LoggingDeletionEvent.SelfDeletionAlreadyRequested(
                            message,
                            message.expirationData
                        )
                    )

                    if (isSelfDeletionOutgoing) return@launch

                    addToOutgoingDeletion(message)
                }
                markDeletionDateAndWait(message, message.expirationData)

                logEphemeralEvent(
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
            logEphemeralEvent(
                LoggingDeletionEvent.AttemptingToDelete(
                    message,
                    message.expirationData!!,
                )
            )

            when (val result = deleteEphemeralMessageForSelfUserAsSender(message.conversationId, message.id)) {
                is Either.Left -> {
                    logEphemeralEvent(
                        LoggingDeletionEvent.DeletionFailed(
                            message,
                            message.expirationData,
                            result.value
                        )
                    )
                }

                is Either.Right -> {
                    logEphemeralEvent(
                        LoggingDeletionEvent.SuccessFullyDeleted(
                            message,
                            message.expirationData,
                        )
                    )
                }
            }
        } else {
            logEphemeralEvent(
                LoggingDeletionEvent.AttemptingToDelete(
                    message,
                    message.expirationData!!,
                )
            )

            when (val result = deleteEphemeralMessageForSelfUserAsReceiver(message.conversationId, message.id)) {
                is Either.Left -> {
                    logEphemeralEvent(
                        LoggingDeletionEvent.DeletionFailed(
                            message,
                            message.expirationData,
                            result.value
                        )
                    )
                }

                is Either.Right -> {
                    logEphemeralEvent(
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

                logEphemeralEvent(
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

                logEphemeralEvent(
                    LoggingDeletionEvent.MarkingSelfDeletionStartDate(
                        message,
                        expirationData,
                        deletionStartMark
                    )
                )
            }

            val delayWaitingTime = timeLeftForDeletion()

            logEphemeralEvent(
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

private fun logEphemeralEvent(
    loggingDeletionEvent: LoggingDeletionEvent
) {
    kaliumLogger.i(loggingDeletionEvent.toJson())
}

sealed class LoggingDeletionEvent(
    open val message: Message.Regular,
    open val expirationData: Message.ExpirationData
) {
    companion object {
        const val EPHEMERAL_LOG_TAG = "Ephemeral"
    }

    fun toJson(): String {
        return EPHEMERAL_LOG_TAG + mapOf(
            "message-id" to message.id,
            "conversation-id" to message.conversationId.toLogString(),
            "expire-after" to expirationData.expireAfter.inWholeSeconds.toString(),
            "expire-start-time" to expireStartTimeElement().toString()
        ).toMutableMap().plus(eventJsonMap()).toJsonElement().toString()
    }

    abstract fun eventJsonMap(): Map<String, String>

    private fun expireStartTimeElement(): String? {
        return when (val selfDeletionStatus = expirationData.selfDeletionStatus) {
            Message.ExpirationData.SelfDeletionStatus.NotStarted -> null
            is Message.ExpirationData.SelfDeletionStatus.Started -> selfDeletionStatus.selfDeletionStartDate.toIsoDateTimeString()
        }
    }

    data class SelfDeletionAlreadyRequested(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData
    ) : LoggingDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "self-deletion-already-requested"
            )
        }
    }

    data class MarkingSelfDeletionStartDate(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
        val startDate: Instant
    ) : LoggingDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "marking-self_deletion_start_date",
                "start-date-mark" to startDate.toIsoDateTimeString()
            )
        }
    }

    data class WaitingForDeletion(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
        val delayWaitTime: Duration
    ) : LoggingDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "waiting-for-deletion",
                "delay-wait-time" to delayWaitTime.inWholeSeconds.toString()
            )
        }
    }

    data class StartingSelfDeletion(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
    ) : LoggingDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "starting-self-deletion"
            )
        }
    }

    data class AttemptingToDelete(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData
    ) : LoggingDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "attempting-to-delete",
                "is-user-sender" to message.isSelfMessage.toString()
            )
        }
    }

    data class SuccessFullyDeleted(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
    ) : LoggingDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "self-deletion-succeed",
                "is-user-sender" to message.isSelfMessage.toString()
            )
        }
    }

    data class DeletionFailed(
        override val message: Message.Regular,
        override val expirationData: Message.ExpirationData,
        val coreFailure: CoreFailure
    ) : LoggingDeletionEvent(message, expirationData) {
        override fun eventJsonMap(): Map<String, String> {
            return mapOf(
                "deletion-status" to "self-deletion-failed",
                "is-user-sender" to message.isSelfMessage.toString(),
                "reason" to coreFailure.toString()
            )
        }
    }
}
