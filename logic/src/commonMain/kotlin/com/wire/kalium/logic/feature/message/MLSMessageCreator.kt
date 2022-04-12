package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.network.api.message.MLSMessageApi

interface MLSMessageCreator {

    suspend fun createOutgoingMLSMessage(
        groupId: String,
        message: Message
    ): Either<CoreFailure, MLSMessageApi.Message>

}

class MLSMessageCreatorImpl(
    private val mlsClientProvider: MLSClientProvider,
    private val protoContentMapper: ProtoContentMapper,
): MLSMessageCreator {

    override suspend fun createOutgoingMLSMessage(groupId: String, message: Message): Either<CoreFailure, MLSMessageApi.Message> {
        return mlsClientProvider.getMLSClient().flatMap { client ->
            val content = protoContentMapper.encodeToProtobuf(ProtoContent(message.id, message.content))
            val encryptedContent = client.encryptMessage(groupId, content.data)
            Either.Right(MLSMessageApi.Message(encryptedContent))
        }
    }

}
