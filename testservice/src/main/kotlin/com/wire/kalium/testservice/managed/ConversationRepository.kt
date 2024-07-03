/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.testservice.managed

import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.mention.MessageMention
import com.wire.kalium.logic.data.message.receipt.ReceiptType
import com.wire.kalium.logic.feature.asset.ScheduleNewAssetMessageResult
import com.wire.kalium.logic.feature.conversation.ClearConversationContentUseCase
import com.wire.kalium.logic.feature.debug.BrokenState
import com.wire.kalium.logic.feature.debug.SendBrokenAssetMessageResult
import com.wire.kalium.logic.data.message.SelfDeletionTimer
import com.wire.kalium.logic.data.message.receipt.DetailedReceipt
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.feature.message.composite.SendButtonActionConfirmationMessageUseCase
import com.wire.kalium.logic.feature.message.composite.SendButtonActionMessageUseCase
import com.wire.kalium.logic.feature.session.CurrentSessionResult
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.testservice.models.Instance
import com.wire.kalium.testservice.models.SendTextResponse
import kotlinx.coroutines.flow.first
import okio.Path.Companion.toOkioPath
import org.slf4j.LoggerFactory
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.util.Base64
import java.util.Collections
import javax.ws.rs.WebApplicationException
import javax.ws.rs.core.Response
import kotlin.time.DurationUnit
import kotlin.time.toDuration

sealed class ConversationRepository {

    @Suppress("TooManyFunctions")
    companion object {
        private val log = LoggerFactory.getLogger(ConversationRepository::class.java.name)

        suspend fun clearConversation(
            instance: Instance,
            conversationId: ConversationId,
        ): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        log.info("Instance ${instance.instanceId}: Clear conversation content")
                        when (val result = conversations.clearConversationContent(conversationId)) {
                            is ClearConversationContentUseCase.Result.Failure ->
                                Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build()

                            is ClearConversationContentUseCase.Result.Success ->
                                Response.status(Response.Status.OK).build()
                        }
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        suspend fun deleteConversation(
            instance: Instance,
            conversationId: ConversationId,
            messageId: String,
            deleteForEveryone: Boolean
        ): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        log.info("Instance ${instance.instanceId}: Delete message everywhere")
                        messages.deleteMessage(conversationId, messageId, deleteForEveryone).fold({
                            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(it).build()
                        }, {
                            Response.status(Response.Status.OK).build()
                        })
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        suspend fun sendConfirmation(instance: Instance, conversationId: ConversationId, type: ReceiptType, messageId: String): Response =
            instance.coreLogic.globalScope {
                when (val session = session.currentSession()) {
                    is CurrentSessionResult.Success -> {
                        instance.coreLogic.sessionScope(session.accountInfo.userId) {
                            log.info("Instance ${instance.instanceId}: Send $type confirmation")
                            debug.sendConfirmation(conversationId, type, messageId, listOf()).fold({
                                Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                    .entity("Instance ${instance.instanceId}: $it").build()
                            }, {
                                Response.status(Response.Status.OK).build()
                            })
                        }
                    }

                    is CurrentSessionResult.Failure -> {
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                    }
                }
            }

        suspend fun sendButtonAction(
            instance: Instance,
            conversationId: ConversationId,
            referenceMessageId: String,
            buttonId: String
        ): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        log.info("Instance ${instance.instanceId}: Send button action for button $buttonId")
                        when (val result = messages.sendButtonActionMessage(conversationId, referenceMessageId, buttonId)) {
                            is SendButtonActionMessageUseCase.Result.Failure ->
                                Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build()

                            else -> {
                                Response.status(Response.Status.OK)
                                    .entity(SendTextResponse(instance.instanceId, "", "")).build()
                            }
                        }
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        suspend fun sendButtonActionConfirmation(
            instance: Instance,
            conversationId: ConversationId,
            referenceMessageId: String,
            buttonId: String,
            userIds: List<UserId>
        ): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        log.info("Instance ${instance.instanceId}: Send button action confirmation for button $buttonId")
                        when (val result = messages.sendButtonActionConfirmationMessage(
                            conversationId,
                            referenceMessageId,
                            buttonId,
                            userIds
                        )) {
                            is SendButtonActionConfirmationMessageUseCase.Result.Failure -> Response
                                .status(Response.Status.INTERNAL_SERVER_ERROR).entity(result).build()

                            else -> {
                                Response.status(Response.Status.OK).entity(SendTextResponse(instance.instanceId, "", "")).build()
                            }
                        }
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        suspend fun sendReaction(
            instance: Instance,
            conversationId: ConversationId,
            originalMessageId: String,
            type: String
        ): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        log.info("Instance ${instance.instanceId}: Send reaction $type")
                        messages.toggleReaction(conversationId, originalMessageId, type).fold({
                            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(it).build()
                        }, {
                            Response.status(Response.Status.OK)
                                .entity(SendTextResponse(instance.instanceId, "", "")).build()
                        })
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        @Suppress("LongParameterList")
        suspend fun sendTextMessage(
            instance: Instance,
            conversationId: ConversationId,
            text: String?,
            mentions: List<MessageMention>,
            messageTimer: Int?,
            quotedMessageId: String?,
            buttons: List<String> = listOf()
        ): Response = instance.coreLogic.globalScope {
            return when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        if (text != null) {
                            setMessageTimer(instance, conversationId, messageTimer)
                            log.info("Instance ${instance.instanceId}: Send text message '$text'")
                            val result = if (buttons.isEmpty()) {
                                messages.sendTextMessage(
                                    conversationId, text, mentions, quotedMessageId
                                )
                            } else {
                                messages.sendButtonMessage(
                                    conversationId, text, mentions, quotedMessageId, buttons
                                )
                            }
                            result.fold({
                                Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(it).build()
                            }, {
                                Response.status(Response.Status.OK)
                                    .entity(SendTextResponse(instance.instanceId, "", "")).build()
                            })
                        } else {
                            Response.status(Response.Status.EXPECTATION_FAILED).entity("No text to send").build()
                        }
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        @Suppress("LongParameterList")
        suspend fun updateTextMessage(
            instance: Instance,
            conversationId: ConversationId,
            text: String?,
            mentions: List<MessageMention>,
            firstMessageId: String,
            messageTimer: Int?
        ): Response = instance.coreLogic.globalScope {
            return when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        if (text != null) {
                            setMessageTimer(instance, conversationId, messageTimer)
                            log.info("Instance ${instance.instanceId}: Send text message '$text'")
                            messages.sendEditTextMessage(
                                conversationId, firstMessageId, text, mentions
                            ).fold({
                                Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(it).build()
                            }, {
                                Response.status(Response.Status.OK)
                                    .entity(SendTextResponse(instance.instanceId, "", "")).build()
                            })
                        } else {
                            Response.status(Response.Status.EXPECTATION_FAILED).entity("No text to send").build()
                        }
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        suspend fun sendTyping(
            instance: Instance,
            conversationId: ConversationId,
            status: String
        ): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        log.info("Instance ${instance.instanceId}: $status typing")
                        if (status.equals("started")) {
                            conversations.sendTypingEvent(conversationId, Conversation.TypingIndicatorMode.STARTED)
                        } else {
                            conversations.sendTypingEvent(conversationId, Conversation.TypingIndicatorMode.STOPPED)
                        }
                        Response.status(Response.Status.OK).build()
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        @Suppress("LongParameterList")
        suspend fun sendLocation(
            instance: Instance,
            conversationId: ConversationId,
            latitude: Float,
            longitude: Float,
            name: String,
            zoom: Int,
            messageTimer: Int?
        ): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        setMessageTimer(instance, conversationId, messageTimer)
                        log.info("Instance ${instance.instanceId}: Send ping")
                        messages.sendLocation(conversationId, latitude, longitude, name, zoom).fold({
                            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(it).build()
                        }, {
                            Response.status(Response.Status.OK).build()
                        })
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        suspend fun sendPing(instance: Instance, conversationId: ConversationId): Response = instance.coreLogic.globalScope {
            when (val session = session.currentSession()) {
                is CurrentSessionResult.Success -> {
                    instance.coreLogic.sessionScope(session.accountInfo.userId) {
                        log.info("Instance ${instance.instanceId}: Send ping")
                        messages.sendKnock(conversationId, false).fold({
                            Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(it).build()
                        }, {
                            Response.status(Response.Status.OK).build()
                        })
                    }
                }

                is CurrentSessionResult.Failure -> {
                    Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                }
            }
        }

        suspend fun getMessages(instance: Instance, conversationId: ConversationId): List<Message> {
            instance.coreLogic.globalScope {
                when (val session = session.currentSession()) {
                    is CurrentSessionResult.Success -> {
                        instance.coreLogic.sessionScope(session.accountInfo.userId) {
                            log.info("Instance ${instance.instanceId}: Get recent messages...")
                            val messages = messages.getRecentMessages(conversationId).first()
                            // We need to reverse order of messages because ETS did the same
                            Collections.reverse(messages)
                            return messages
                        }
                    }

                    is CurrentSessionResult.Failure -> {
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                    }
                }
            }
            throw WebApplicationException("Instance ${instance.instanceId}: Could not get recent messages")
        }

<<<<<<< HEAD
        suspend fun getMessageReceipts(
            instance: Instance,
            conversationId: ConversationId,
            messageId: String,
            type: ReceiptType
        ): List<DetailedReceipt> {
            instance.coreLogic.globalScope {
                when (val session = session.currentSession()) {
                    is CurrentSessionResult.Success -> {
                        instance.coreLogic.sessionScope(session.accountInfo.userId) {
                            log.info("Instance ${instance.instanceId}: Get receipts...")
                            val receipts = messages.observeMessageReceipts(conversationId, messageId, type).first()
                            return receipts
                        }
                    }

                    is CurrentSessionResult.Failure -> {
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                    }
                }
            }
            throw WebApplicationException("Instance ${instance.instanceId}: Could not get receipts from message")
        }

        @Suppress("LongParameterList", "LongMethod", "ThrowsCount")
=======
        @Suppress("LongParameterList", "LongMethod", "ThrowsCount", "ComplexMethod")
>>>>>>> 596eb022e3 (fix: asset restriction [WPB-9947] (#2831) (#2856) (#2861))
        suspend fun sendFile(
            instance: Instance,
            conversationId: ConversationId,
            data: String,
            fileName: String,
            type: String,
            messageTimer: Int?,
            invalidHash: Boolean,
            otherAlgorithm: Boolean,
            otherHash: Boolean
        ): Response {
            val temp: File = Files.createTempFile("asset", ".data").toFile()
            val byteArray = Base64.getDecoder().decode(data)
            FileOutputStream(temp).use { outputStream -> outputStream.write(byteArray) }
            log.info("Instance ${instance.instanceId}: Send file $fileName")
            instance.coreLogic.globalScope {
                return when (val session = session.currentSession()) {
                    is CurrentSessionResult.Success -> {
                        instance.coreLogic.sessionScope(session.accountInfo.userId) {

                            log.info("Instance ${instance.instanceId}: Wait until alive")
                            if (syncManager.isSlowSyncOngoing()) {
                                log.info("Instance ${instance.instanceId}: Slow sync is ongoing")
                            }
                            syncManager.waitUntilLiveOrFailure().onFailure {
                                log.info("Instance ${instance.instanceId}: Sync failed with $it")
                            }
                            setMessageTimer(instance, conversationId, messageTimer)
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
                                    null,
                                    0L
                                )
                            }
                            when (sendResult) {
                                is ScheduleNewAssetMessageResult.Failure -> {
                                    // if the IDE tels you that this casting is unnecessary
                                    // first check kotlin version
                                    // if version < 2 then casting is necessary
                                    // if version >= 2 then casting is unnecessary
                                    when (val result = sendResult as ScheduleNewAssetMessageResult.Failure) {
                                        ScheduleNewAssetMessageResult.Failure.RestrictedFileType,
                                        ScheduleNewAssetMessageResult.Failure.DisabledByTeam -> {
                                            throw WebApplicationException(
                                                "Instance ${instance.instanceId}: Sending failed with $sendResult"
                                            )
                                        }

                                        is ScheduleNewAssetMessageResult.Failure.Generic -> {
                                            if (result.coreFailure is StorageFailure.Generic) {
                                                val rootCause = (result.coreFailure as StorageFailure.Generic).rootCause.message
                                                throw WebApplicationException(
                                                    "Instance ${instance.instanceId}: Sending failed with $rootCause"
                                                )
                                            } else {
                                                throw WebApplicationException("Instance ${instance.instanceId}: Sending failed")
                                            }
                                        }
                                    }
                                }

                                is SendBrokenAssetMessageResult.Failure -> {
                                    Response.status(Response.Status.INTERNAL_SERVER_ERROR)
                                        .entity("Instance ${instance.instanceId}: Sending broken file $fileName failed")
                                        .build()
                                }

                                else -> {
                                    log.info("Instance ${instance.instanceId}: Sending file $fileName was successful")
                                    Response.status(Response.Status.OK).build()
                                }
                            }
                        }
                    }

                    is CurrentSessionResult.Failure -> {
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                    }
                }

            }
        }

        @Suppress("LongParameterList", "ThrowsCount")
        suspend fun sendImage(
            instance: Instance,
            conversationId: ConversationId,
            data: String,
            type: String,
            width: Int,
            height: Int,
            messageTimer: Int?
        ): Response {
            val temp: File = Files.createTempFile("asset", ".data").toFile()
            val byteArray = Base64.getDecoder().decode(data)
            FileOutputStream(temp).use { outputStream -> outputStream.write(byteArray) }

            instance.coreLogic.globalScope {
                return when (val session = session.currentSession()) {
                    is CurrentSessionResult.Success -> {
                        instance.coreLogic.sessionScope(session.accountInfo.userId) {
                            log.info("Instance ${instance.instanceId}: Send file")
                            log.info("Instance ${instance.instanceId}: Wait until alive")
                            if (syncManager.isSlowSyncOngoing()) {
                                log.info("Instance ${instance.instanceId}: Slow sync is ongoing")
                            }
                            syncManager.waitUntilLiveOrFailure().onFailure {
                                log.info("Instance ${instance.instanceId}: Sync failed with $it")
                            }
                            setMessageTimer(instance, conversationId, messageTimer)
                            val sendResult = messages.sendAssetMessage(
                                conversationId,
                                temp.toOkioPath(),
                                byteArray.size.toLong(),
                                "image", type,
                                width,
                                height,
                                0L
                            )
                            when (sendResult) {
                                ScheduleNewAssetMessageResult.Failure.RestrictedFileType,
                                ScheduleNewAssetMessageResult.Failure.DisabledByTeam -> {
                                    throw WebApplicationException("Instance ${instance.instanceId}: Sending failed with $sendResult")
                                }

                                is ScheduleNewAssetMessageResult.Failure.Generic -> {
                                    if (sendResult.coreFailure is StorageFailure.Generic) {
                                        val rootCause = (sendResult.coreFailure as StorageFailure.Generic).rootCause.message
                                        throw WebApplicationException(
                                            "Instance ${instance.instanceId}: Sending failed with $rootCause"
                                        )
                                    } else {
                                        throw WebApplicationException("Instance ${instance.instanceId}: Sending failed")
                                    }
                                }

                                is ScheduleNewAssetMessageResult.Success -> Response.status(Response.Status.OK).build()
                            }
                        }
                    }

                    is CurrentSessionResult.Failure -> {
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                    }
                }
            }
        }

        private suspend fun setMessageTimer(instance: Instance, conversationId: ConversationId, messageTimer: Int?) {
            instance.coreLogic.globalScope {
                when (val session = session.currentSession()) {
                    is CurrentSessionResult.Success -> {
                        instance.coreLogic.sessionScope(session.accountInfo.userId) {
                            val newSelfDeletionTimer = if (messageTimer != 0 && messageTimer != null) {
                                log.info("Instance ${instance.instanceId}: Enable self deletion timer")
                                SelfDeletionTimer.Enabled(messageTimer.toDuration(DurationUnit.MILLISECONDS))
                            } else {
                                log.info("Instance ${instance.instanceId}: Disable self deletion timer")
                                SelfDeletionTimer.Disabled
                            }
                            persistNewSelfDeletionStatus.invoke(conversationId, newSelfDeletionTimer)
                        }
                    }

                    is CurrentSessionResult.Failure -> {
                        Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity("Session failure").build()
                    }
                }
            }
        }
    }
}
