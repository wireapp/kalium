package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.cryptography.DecryptedMessageBundle
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.message.MLSMessageApi
import com.wire.kalium.network.api.user.client.ClientApi
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import com.wire.kalium.persistence.dao.Member
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.datetime.Clock
import kotlin.time.Duration
import kotlinx.coroutines.flow.map

interface MLSConversationRepository {

    suspend fun establishMLSGroup(groupID: String): Either<CoreFailure, Unit>
    suspend fun establishMLSGroupFromWelcome(welcomeEvent: Event.Conversation.MLSWelcome): Either<CoreFailure, Unit>
    suspend fun hasEstablishedMLSGroup(groupID: String): Either<CoreFailure, Boolean>
    suspend fun messageFromMLSMessage(messageEvent: Event.Conversation.NewMLSMessage): Either<CoreFailure, DecryptedMessageBundle?>
    suspend fun addMemberToMLSGroup(groupID: String, userIdList: List<UserId>): Either<CoreFailure, Unit>
    suspend fun removeMembersFromMLSGroup(groupID: String, userIdList: List<UserId>): Either<CoreFailure, Unit>
    suspend fun requestToJoinGroup(groupID: String, epoch: ULong): Either<CoreFailure, Unit>
    suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<String>>
    suspend fun updateKeyingMaterial(groupID: String): Either<CoreFailure, Unit>
    suspend fun commitPendingProposals(groupID: String): Either<CoreFailure, Unit>
    suspend fun setProposalTimer(timer: ProposalTimer)
    suspend fun observeProposalTimers(): Flow<List<ProposalTimer>>
}

@Suppress("TooManyFunctions", "LongParameterList")
class MLSConversationDataSource(
    private val keyPackageRepository: KeyPackageRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val mlsMessageApi: MLSMessageApi,
    private val conversationDAO: ConversationDAO,
    private val clientApi: ClientApi,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper()
) : MLSConversationRepository {

    override suspend fun messageFromMLSMessage(messageEvent: Event.Conversation.NewMLSMessage): Either<CoreFailure, DecryptedMessageBundle?> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapStorageRequest {
                conversationDAO.observeGetConversationByQualifiedID(idMapper.toDaoModel(messageEvent.conversationId)).first()
            }.flatMap { conversation ->
                if (conversation.protocolInfo is ConversationEntity.ProtocolInfo.MLS) {
                    Either.Right(
                        mlsClient.decryptMessage(
                            (conversation.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId,
                            messageEvent.content.decodeBase64Bytes()
                        )
                    )
                } else {
                    Either.Right(null)
                }
            }
        }

    override suspend fun establishMLSGroupFromWelcome(welcomeEvent: Event.Conversation.MLSWelcome): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient().flatMap { client ->
            val groupID = client.processWelcomeMessage(welcomeEvent.message.decodeBase64Bytes())

            kaliumLogger.i("Created conversation from welcome message (groupID = $groupID)")

            wrapStorageRequest {
                if (conversationDAO.getConversationByGroupID(groupID).first() != null) {
                    // Welcome arrived after the conversation create event, updating existing conversation.
                    conversationDAO.updateConversationGroupState(ConversationEntity.GroupState.ESTABLISHED, groupID)
                    kaliumLogger.i("Updated conversation from welcome message (groupID = $groupID)")
                }
            }
        }

    override suspend fun hasEstablishedMLSGroup(groupID: String): Either<CoreFailure, Boolean> {
        return mlsClientProvider.getMLSClient().flatMap { Either.Right(it.conversationExists(groupID)) }
    }

    override suspend fun establishMLSGroup(groupID: String): Either<CoreFailure, Unit> =
        getConversationMembers(groupID).flatMap { members ->
            establishMLSGroup(groupID, members)
        }

    override suspend fun requestToJoinGroup(groupID: String, epoch: ULong): Either<CoreFailure, Unit> {
        kaliumLogger.d("Requesting to re-join MLS group $groupID with epoch $epoch")
        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapApiRequest {
                mlsMessageApi.sendMessage(MLSMessageApi.Message(mlsClient.joinConversation(groupID, epoch)))
            }.onSuccess {
                conversationDAO.updateConversationGroupState(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE, groupID)
            }
        }
    }

    override suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<String>> =
        wrapStorageRequest {
            conversationDAO.getConversationsByKeyingMaterialUpdate(threshold)
        }

    override suspend fun updateKeyingMaterial(groupID: String): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            sendCommitBundle(groupID, mlsClient.updateKeyingMaterial(groupID)).flatMap {
                    wrapStorageRequest {
                        conversationDAO.updateKeyingMaterial(groupID, Clock.System.now())
                    }
                }
            }

    private suspend fun sendCommitBundle(groupID: String, bundle: CommitBundle): Either<CoreFailure, Unit> {
        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapApiRequest {
                mlsMessageApi.sendMessage(MLSMessageApi.Message(bundle.commit))
            }.flatMap {
                mlsClient.commitAccepted(groupID)
                bundle.welcome?.let {
                    wrapApiRequest {
                        mlsMessageApi.sendWelcomeMessage(MLSMessageApi.WelcomeMessage(it))
                    }
                } ?: Either.Right(Unit)
            }
        }
    }

    override suspend fun commitPendingProposals(groupID: String): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient()
            .flatMap { mlsClient ->
                sendCommitBundle(groupID, mlsClient.commitPendingProposals(groupID)).flatMap {
                    wrapStorageRequest {
                        conversationDAO.clearProposalTimer(groupID)
                    }
                }
            }

    override suspend fun setProposalTimer(timer: ProposalTimer) {
        conversationDAO.setProposalTimer(conversationMapper.toDAOProposalTimer(timer))
    }

    override suspend fun observeProposalTimers(): Flow<List<ProposalTimer>> {
        return conversationDAO.getProposalTimers().map { it.map(conversationMapper::fromDaoModel) }
    }

    override suspend fun addMemberToMLSGroup(groupID: String, userIdList: List<UserId>): Either<CoreFailure, Unit> =
        // TODO: check for federated and non-federated members
        keyPackageRepository.claimKeyPackages(userIdList).flatMap { keyPackages ->
            mlsClientProvider.getMLSClient().flatMap { client ->
                val clientKeyPackageList = keyPackages
                    .map {
                        Pair(
                            CryptoQualifiedClientId(it.clientID, CryptoQualifiedID(it.userId, it.domain)),
                            it.keyPackage.decodeBase64Bytes()
                        )
                    }
                client.addMember(groupID, clientKeyPackageList)?.let { bundle ->
                    sendCommitBundle(groupID, bundle)
                        .flatMap {
                            wrapStorageRequest {
                                val list = userIdList.map {
                                    Member(idMapper.toDaoModel(it), Member.Role.Member)
                                }
                                conversationDAO.insertMembers(list, groupID)
                            }
                        }.flatMap {
                        Either.Right(Unit)
                    }
                } ?: run {
                    Either.Right(Unit)
                }
            }
        }

    override suspend fun removeMembersFromMLSGroup(
        groupID: String,
        userIdList: List<UserId>
    ): Either<CoreFailure, Unit> =
        wrapApiRequest { clientApi.listClientsOfUsers(userIdList.map { idMapper.toApiModel(it) }) }.map { userClientsList ->
            val usersCryptoQualifiedClientIDs = userClientsList.flatMap { userClients ->
                userClients.value.map { userClient ->
                    CryptoQualifiedClientId(userClient.id, idMapper.toCryptoQualifiedIDId(idMapper.fromApiModel(userClients.key)))
                }
            }
            mlsClientProvider.getMLSClient().flatMap { client ->
                client.removeMember(groupID, usersCryptoQualifiedClientIDs).let { bundle ->
                    sendCommitBundle(groupID, bundle).flatMap {
                        wrapStorageRequest {
                            conversationDAO.deleteMembersByQualifiedID(
                                userIdList.map { idMapper.toDaoModel(it) },
                                groupID
                            )
                        }
                    }
                }
            }
        }

    private suspend fun establishMLSGroup(groupID: String, members: List<UserId>): Either<CoreFailure, Unit> =
        keyPackageRepository.claimKeyPackages(members).flatMap { keyPackages ->
            mlsClientProvider.getMLSClient().flatMap { client ->
                val clientKeyPackageList = keyPackages
                    .map {
                        Pair(
                            CryptoQualifiedClientId(it.clientID, CryptoQualifiedID(it.userId, it.domain)),
                            it.keyPackage.decodeBase64Bytes()
                        )
                    }

                client.createConversation(groupID, clientKeyPackageList)?.let { bundle ->
                    sendCommitBundle(groupID, bundle).flatMap {
                        wrapStorageRequest {
                            conversationDAO.updateConversationGroupState(ConversationEntity.GroupState.ESTABLISHED, groupID)
                        }
                    }
                } ?: run {
                    Either.Right(Unit)
                }
            }
        }

    private suspend fun getConversationMembers(groupID: String): Either<StorageFailure, List<UserId>> = wrapStorageRequest {
        val conversationID =
            conversationDAO.getConversationByGroupID(groupID).first()?.id ?: return Either.Left(StorageFailure.DataNotFound)
        conversationDAO.getAllMembers(conversationID).first().map { idMapper.fromDaoModel(it.user) }
    }

}
