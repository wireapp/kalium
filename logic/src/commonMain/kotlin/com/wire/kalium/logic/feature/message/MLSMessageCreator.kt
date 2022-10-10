package com.wire.kalium.logic.feature.message

import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.ProtoContent
import com.wire.kalium.logic.data.message.ProtoContentMapper
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi

interface MLSMessageCreator {

    suspend fun createOutgoingMLSMessage(
        groupId: GroupID,
        message: Message.Regular
    ): Either<CoreFailure, MLSMessageApi.Message>

}

class MLSMessageCreatorImpl(
    private val mlsClientProvider: MLSClientProvider,
    private val protoContentMapper: ProtoContentMapper = MapperProvider.protoContentMapper(),
    private val idMapper: IdMapper = MapperProvider.idMapper()
) : MLSMessageCreator {

    override suspend fun createOutgoingMLSMessage(groupId: GroupID, message: Message.Regular): Either<CoreFailure, MLSMessageApi.Message> {
        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            kaliumLogger.i("Creating outgoing MLS message (groupID = $groupId)")
            val content = protoContentMapper.encodeToProtobuf(ProtoContent.Readable(message.id, message.content))
            wrapMLSRequest { MLSMessageApi.Message(mlsClient.encryptMessage(idMapper.toCryptoModel(groupId), content.data)) }
        }
    }

}
