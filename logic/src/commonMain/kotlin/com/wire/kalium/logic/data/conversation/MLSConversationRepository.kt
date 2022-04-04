package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.suspending
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.flow.first

interface MLSConversationRepository {

    suspend fun establishMLSGroup(conversation: Conversation): Either<CoreFailure, Unit>

}

class MLSConversationDataSource(
    val keyPackageRepository: KeyPackageRepository,
    val mlsClientProvider: MLSClientProvider,
    val mlsMessageApi: MLSMessageApi,
    val conversationDAO: ConversationDAO,
    val idMapper: IdMapper = MapperProvider.idMapper()
): MLSConversationRepository {

    override suspend fun establishMLSGroup(conversation: Conversation): Either<CoreFailure, Unit> = suspending  {
        val groupID: String = conversation.groupId?.let { it } ?: run { return@suspending Either.Left(CoreFailure.MissingClientRegistration) }

        getConversationMembers(conversation.id).flatMap { members ->
            establishMLSGroup(groupID, members)
        }
    }

    private suspend fun establishMLSGroup(groupID: String, members: List<UserId>): Either<CoreFailure, Unit> = suspending {
        keyPackageRepository.claimKeyPackages(members).flatMap { keyPackages ->
            mlsClientProvider.getMLSClient().flatMap { client ->
                val clientKeyPackageList = keyPackages
                    .map {
                        Pair(
                            CryptoQualifiedClientId(it.clientID, CryptoQualifiedID(it.userId, it.domain)),
                            it.keyPackage.decodeBase64Bytes()
                        )
                    }

                // TODO: send handshake when API is available
                client.createConversation(groupID, clientKeyPackageList)?.let { (handshake, welcome) ->
                    wrapApiRequest {
                        mlsMessageApi.sendWelcomeMessage(MLSMessageApi.WelcomeMessage(welcome))
                    }.flatMap {
                        wrapStorageRequest {
                            conversationDAO.updateConversationGroupState(ConversationEntity.GroupState.ESTABLISHED, groupID)
                        }
                    }.flatMap {
                        Either.Right(Unit)
                    }
                } ?: run {
                    Either.Right(Unit)
                }
            }
        }
    }

    private suspend fun getConversationMembers(conversationId: ConversationId): Either<StorageFailure, List<UserId>> = wrapStorageRequest {
        conversationDAO.getAllMembers(idMapper.toDaoModel(conversationId)).first().map { idMapper.fromDaoModel(it.user) }
    }

}
