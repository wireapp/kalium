package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MigratedMessage
import com.wire.kalium.logic.data.message.PlainMessageBlob
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.receiver.conversation.message.ApplicationMessageHandler
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * Persist migrated messages from old datasource
 */
fun interface PersistMigratedMessagesUseCase {
    suspend operator fun invoke(messages: List<MigratedMessage>): Either<CoreFailure, Unit>
}

internal class PersistMigratedMessagesUseCaseImpl(
    private val applicationMessageHandler: ApplicationMessageHandler,
    private val protoContentMapper: ProtoContentMapper,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : PersistMigratedMessagesUseCase {
    override suspend fun invoke(messages: List<MigratedMessage>): Either<CoreFailure, Unit> = withContext(dispatchers.default) {
        messages.filter { it.encryptedProto != null }
            .map { migratedMessage ->
                migratedMessage to protoContentMapper.decodeFromProtobuf(PlainMessageBlob(migratedMessage.encryptedProto!!))
            }.forEach { (message, proto) ->
                when (proto) {
                    is ProtoContent.ExternalMessageInstructions -> kaliumLogger.w("Ignoring external message")
                    is ProtoContent.Readable -> {
                        val updatedProto =
                            if (message.assetSize != null && message.assetName != null && proto.messageContent is MessageContent.Asset) {
                                proto.copy(
                                    messageContent = proto.messageContent.copy(
                                        value = proto.messageContent.value.copy(
                                            name = message.assetName,
                                            sizeInBytes = message.assetSize.toLong()
                                        )
                                    )
                                )
                            } else proto
                        applicationMessageHandler.handleContent(
                            message.conversationId,
                            message.timestampIso,
                            message.senderUserId,
                            message.senderClientId,
                            updatedProto
                        )
                    }
                }
            }
        Either.Right(Unit)
    }
}
