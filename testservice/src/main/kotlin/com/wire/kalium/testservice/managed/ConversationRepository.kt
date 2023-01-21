package com.wire.kalium.testservice.managed

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.feature.asset.ScheduleNewAssetMessageResult
import com.wire.kalium.logic.feature.conversation.GetConversationsUseCase
import com.wire.kalium.logic.feature.debug.BrokenState
import com.wire.kalium.logic.feature.debug.SendBrokenAssetMessageResult
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.logic.functional.isLeft
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.testservice.models.Instance
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import okio.Path.Companion.toOkioPath
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Base64
import javax.ws.rs.WebApplicationException

sealed class ConversationRepository {

    companion object {
        private val log = LoggerFactory.getLogger(ConversationRepository::class.java.name)
        private val scope = CoroutineScope(Dispatchers.Default)

        fun deleteConversation(
            instance: Instance,
            conversationId: ConversationId,
            messageId: String,
            deleteForEveryone: Boolean
        ) {
            instance.coreLogic?.globalScope {
                scope.launch {
                    val result = session.currentSession()
                    if (result is CurrentSessionResult.Success) {
                        instance.coreLogic.sessionScope(result.accountInfo.userId) {
                            log.info("Instance ${instance.instanceId}: Delete message everywhere")
                            runBlocking {
                                messages.deleteMessage(conversationId, messageId, deleteForEveryone)
                            }
                        }
                    }
                }
            }
        }

        fun sendReaction(
            instance: Instance,
            conversationId: ConversationId,
            originalMessageId: String,
            type: String
        ) {
            instance.coreLogic?.globalScope {
                scope.launch {
                    val result = session.currentSession()
                    if (result is CurrentSessionResult.Success) {
                        instance.coreLogic.sessionScope(result.accountInfo.userId) {
                            log.info("Instance ${instance.instanceId}: Send reaction $type")
                            runBlocking {
                                messages.toggleReaction(conversationId, originalMessageId, type)
                            }
                        }
                    }
                }
            }
        }

        fun sendTextMessage(instance: Instance, conversationId: ConversationId, text: String?) {
            instance.coreLogic?.globalScope {
                scope.launch {
                    val result = session.currentSession()
                    if (result is CurrentSessionResult.Success) {
                        instance.coreLogic.sessionScope(result.accountInfo.userId) {
                            text?.let {
                                log.info("Instance ${instance.instanceId}: Send text message '$text'")
                                runBlocking {
                                    val sendResult = messages.sendTextMessage(conversationId, text)
                                    if (sendResult.isLeft()) {
                                        throw WebApplicationException(
                                            "Instance ${instance.instanceId}: Sending failed with ${sendResult.value}"
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        fun sendPing(instance: Instance, conversationId: ConversationId) {
            instance.coreLogic?.globalScope {
                scope.launch {
                    val result = session.currentSession()
                    if (result is CurrentSessionResult.Success) {
                        instance.coreLogic.sessionScope(result.accountInfo.userId) {
                            log.info("Instance ${instance.instanceId}: Send ping")
                            runBlocking {
                                messages.sendKnock(conversationId, false)
                            }
                        }
                    }
                }
            }
        }

        suspend fun getMessages(instance: Instance, conversationId: ConversationId): List<Message> = scope.async {
            instance.coreLogic?.globalScope {
                val result = session.currentSession()
                if (result is CurrentSessionResult.Success) {
                    instance.coreLogic.sessionScope(result.accountInfo.userId) {
                        val recentMessages = runBlocking {
                            log.info("Instance ${instance.instanceId}: Get recent messages...")
                            messages.getRecentMessages(conversationId).first()
                        }
                        return@async recentMessages
                    }
                }
            }
            throw WebApplicationException("Instance ${instance.instanceId}: Could not get recent messages")
        }.await()

        @Suppress("LongParameterList", "LongMethod", "ThrowsCount")
        fun sendFile(
            instance: Instance,
            conversationId: ConversationId,
            data: String,
            fileName: String,
            type: String,
            invalidHash: Boolean,
            otherAlgorithm: Boolean,
            otherHash: Boolean
        ) {
            val temp: File = Files.createTempFile("asset", ".data").toFile()
            val byteArray = Base64.getDecoder().decode(data)
            FileOutputStream(temp).use { outputStream -> outputStream.write(byteArray) }
            log.info("Instance ${instance.instanceId}: Send file $fileName")
            instance.coreLogic?.globalScope {
                scope.launch {
                    val result = session.currentSession()
                    if (result is CurrentSessionResult.Success) {
                        instance.coreLogic.sessionScope(result.accountInfo.userId) {
                            runBlocking {
                                log.info("Instance ${instance.instanceId}: Wait until alive")
                                if (syncManager.isSlowSyncOngoing()) {
                                    log.info("Instance ${instance.instanceId}: Slow sync is ongoing")
                                }
                                syncManager.waitUntilLiveOrFailure().onFailure {
                                    log.info("Instance ${instance.instanceId}: Sync failed with $it")
                                }
                                log.info("Instance ${instance.instanceId}: List conversations:")
                                val convos = conversations.getConversations()
                                if (convos is GetConversationsUseCase.Result.Success) {
                                    for (convo in convos.convFlow.first()) {
                                        log.info("${convo.name} (${convo.id})")
                                    }
                                }
                                val sendResult = if (invalidHash || otherAlgorithm || otherHash) {
                                    val brokenState = BrokenState(invalidHash, otherHash, otherAlgorithm)
                                    @Suppress("IMPLICIT_CAST_TO_ANY")
                                    debug.sendBrokenAssetMessage(
                                        conversationId,
                                        temp.toOkioPath(),
                                        byteArray.size.toLong(),
                                        fileName,
                                        type,
                                        brokenState
                                    )
                                } else {
                                    @Suppress("IMPLICIT_CAST_TO_ANY")
                                    messages.sendAssetMessage(
                                        conversationId,
                                        temp.toOkioPath(),
                                        byteArray.size.toLong(),
                                        fileName,
                                        type,
                                        null,
                                        null
                                    )
                                }
                                when (sendResult) {
                                    is ScheduleNewAssetMessageResult.Failure -> {
                                        if (sendResult.coreFailure is StorageFailure.Generic) {
                                            val rootCause = (sendResult.coreFailure as StorageFailure.Generic).rootCause.message
                                            throw WebApplicationException(
                                                "Instance ${instance.instanceId}: Sending failed with $rootCause"
                                            )
                                        } else {
                                            throw WebApplicationException("Instance ${instance.instanceId}: Sending file $fileName failed")
                                        }
                                    }

                                    is SendBrokenAssetMessageResult.Failure -> {
                                        throw WebApplicationException("Instance ${instance.instanceId}: " +
                                                "Sending broken file $fileName failed")
                                    }

                                    else -> {
                                        log.info("Instance ${instance.instanceId}: Sending file $fileName was successful")
                                    }
                                }
                            }
                        }
                    }
                }

            }
        }

        @Suppress("LongParameterList")
        fun sendImage(
            instance: Instance,
            conversationId: ConversationId,
            data: String,
            type: String,
            width: Int,
            height: Int
        ) {
            val temp: File = Files.createTempFile("asset", ".data").toFile()
            val byteArray = Base64.getDecoder().decode(data)
            FileOutputStream(temp).use { outputStream -> outputStream.write(byteArray) }

            instance.coreLogic?.globalScope {
                scope.launch {
                    val result = session.currentSession()
                    if (result is CurrentSessionResult.Success) {
                        instance.coreLogic.sessionScope(result.accountInfo.userId) {
                            log.info("Instance ${instance.instanceId}: Send file")
                            runBlocking {
                                log.info("Instance ${instance.instanceId}: Wait until alive")
                                if (syncManager.isSlowSyncOngoing()) {
                                    log.info("Instance ${instance.instanceId}: Slow sync is ongoing")
                                }
                                syncManager.waitUntilLiveOrFailure().onFailure {
                                    log.info("Instance ${instance.instanceId}: Sync failed with $it")
                                }
                                log.info("Instance ${instance.instanceId}: List conversations:")
                                val convos = conversations.getConversations()
                                if (convos is GetConversationsUseCase.Result.Success) {
                                    for (convo in convos.convFlow.first()) {
                                        log.info("${convo.name} (${convo.id})")
                                    }
                                }
                                val sendResult = messages.sendAssetMessage(
                                    conversationId,
                                    temp.toOkioPath(),
                                    byteArray.size.toLong(),
                                    "image", type,
                                    width,
                                    height
                                )
                                if (sendResult is ScheduleNewAssetMessageResult.Failure) {
                                    if (sendResult.coreFailure is StorageFailure.Generic) {
                                        val rootCause = (sendResult.coreFailure as StorageFailure.Generic).rootCause.message
                                        throw WebApplicationException(
                                            "Instance ${instance.instanceId}: Sending failed with $rootCause"
                                        )
                                    } else {
                                        throw WebApplicationException("Instance ${instance.instanceId}: Sending failed")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
