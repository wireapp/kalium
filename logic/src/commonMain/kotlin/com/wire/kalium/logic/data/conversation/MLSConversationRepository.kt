package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
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

    suspend fun establishMLSGroup(groupID: String): Either<CoreFailure, Unit>
    suspend fun establishMLSGroupFromWelcome(welcomeEvent: Event.Conversation.MLSWelcome): Either<CoreFailure, Unit>

}

class MLSConversationDataSource(
    private val keyPackageRepository: KeyPackageRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val mlsMessageApi: MLSMessageApi,
    private val conversationDAO: ConversationDAO,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper()
): MLSConversationRepository {

    override suspend fun establishMLSGroupFromWelcome(welcomeEvent: Event.Conversation.MLSWelcome): Either<CoreFailure, Unit> = suspending {
        mlsClientProvider.getMLSClient().flatMap { client ->
            val groupID = client.processWelcomeMessage(welcomeEvent.message.decodeBase64Bytes())

            wrapStorageRequest {
                if (conversationDAO.getConversationByGroupID(groupID).first() == null) {
                    // Welcome arrived before the conversation create event, insert empty conversation.
                    conversationDAO.insertConversation(conversationMapper.toDaoModel(welcomeEvent, groupID))
                } else {
                    // Welcome arrived after the conversation create event, update existing conversation.
                    conversationDAO.updateConversationGroupState(ConversationEntity.GroupState.ESTABLISHED, groupID)
                }
            }
        }
    }

    override suspend fun establishMLSGroup(groupID: String): Either<CoreFailure, Unit> = suspending  {
        getConversationMembers(groupID).flatMap { members ->
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

    private suspend fun getConversationMembers(groupID: String): Either<StorageFailure, List<UserId>> = wrapStorageRequest {
        val conversationID = conversationDAO.getConversationByGroupID(groupID).first()?.id ?: return Either.Left(StorageFailure.DataNotFound)
        conversationDAO.getAllMembers(conversationID).first().map { idMapper.fromDaoModel(it.user) }
    }

}
