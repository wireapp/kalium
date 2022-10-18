package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.Event.Conversation.MLSWelcome
import com.wire.kalium.logic.data.event.Event.Conversation.NewMLSMessage
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysMapper
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.wrapApiRequest
import com.wire.kalium.logic.wrapMLSRequest
import com.wire.kalium.logic.wrapStorageRequest
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.network.api.base.authenticated.message.MLSMessageApi
import com.wire.kalium.network.api.base.authenticated.notification.EventContentDTO
import com.wire.kalium.network.exceptions.KaliumException
import com.wire.kalium.network.exceptions.isMlsClientMismatch
import com.wire.kalium.network.exceptions.isMlsCommitMissingReferences
import com.wire.kalium.network.exceptions.isMlsStaleMessage
import com.wire.kalium.persistence.dao.ConversationDAO
import com.wire.kalium.persistence.dao.ConversationEntity
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.datetime.Clock
import kotlin.time.Duration

data class ApplicationMessage(
    val message: ByteArray,
    val senderClientID: ClientId
)
data class DecryptedMessageBundle(
    val groupID: GroupID,
    val applicationMessage: ApplicationMessage?,
    val commitDelay: Long?
)

interface MLSConversationRepository {
    suspend fun establishMLSGroup(groupID: GroupID, members: List<UserId>): Either<CoreFailure, Unit>
    suspend fun establishMLSGroupFromWelcome(welcomeEvent: MLSWelcome): Either<CoreFailure, Unit>
    suspend fun hasEstablishedMLSGroup(groupID: GroupID): Either<CoreFailure, Boolean>
    suspend fun messageFromMLSMessage(messageEvent: NewMLSMessage): Either<CoreFailure, DecryptedMessageBundle?>
    suspend fun addMemberToMLSGroup(groupID: GroupID, userIdList: List<UserId>): Either<CoreFailure, Unit>
    suspend fun removeMembersFromMLSGroup(groupID: GroupID, userIdList: List<UserId>): Either<CoreFailure, Unit>
    suspend fun leaveGroup(groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun requestToJoinGroup(groupID: GroupID, epoch: ULong): Either<CoreFailure, Unit>
    suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<GroupID>>
    suspend fun updateKeyingMaterial(groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun commitPendingProposals(groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun setProposalTimer(timer: ProposalTimer)
    suspend fun observeProposalTimers(): Flow<List<ProposalTimer>>
}

private enum class CommitStrategy {
    KEEP_AND_RETRY,
    DISCARD_AND_RETRY,
    ABORT
}

private fun CoreFailure.getStrategy(): CommitStrategy {
    return if (this is NetworkFailure.ServerMiscommunication && this.kaliumException is KaliumException.InvalidRequestError) {
        if (this.kaliumException.isMlsClientMismatch()) {
            CommitStrategy.DISCARD_AND_RETRY
        } else if (
            this.kaliumException.isMlsStaleMessage() ||
            this.kaliumException.isMlsCommitMissingReferences()
        ) {
            CommitStrategy.KEEP_AND_RETRY
        } else {
            CommitStrategy.ABORT
        }
    } else {
        CommitStrategy.ABORT
    }
}

@Suppress("TooManyFunctions", "LongParameterList")
class MLSConversationDataSource(
    private val keyPackageRepository: KeyPackageRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val mlsMessageApi: MLSMessageApi,
    private val conversationDAO: ConversationDAO,
    private val clientApi: ClientApi,
    private val syncManager: SyncManager,
    private val mlsPublicKeysRepository: MLSPublicKeysRepository,
    private val commitBundleEventReceiver: CommitBundleEventReceiver,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(),
    private val mlsPublicKeysMapper: MLSPublicKeysMapper = MapperProvider.mlsPublicKeyMapper()
) : MLSConversationRepository {

    override suspend fun messageFromMLSMessage(
        messageEvent: NewMLSMessage
    ): Either<CoreFailure, DecryptedMessageBundle?> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapStorageRequest {
                conversationDAO.observeGetConversationByQualifiedID(
                    idMapper.toDaoModel(messageEvent.conversationId)
                ).first()
            }.flatMap { conversation ->
                if (conversation.protocolInfo is ConversationEntity.ProtocolInfo.MLS) {
                    val groupID = idMapper.fromGroupIDEntity(
                        (conversation.protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupId
                    )
                    wrapMLSRequest {
                        mlsClient.decryptMessage(
                            idMapper.toCryptoModel(groupID),
                            messageEvent.content.decodeBase64Bytes()
                        ).let {
                            DecryptedMessageBundle(
                                groupID,
                                it.message?.let { message ->
                                    // We will always have senderClientId together with an application message
                                    // but CoreCrypto API doesn't express this
                                    val senderClientId = it.senderClientId?.let { senderClientId ->
                                        idMapper.fromCryptoQualifiedClientId(senderClientId)
                                    } ?: ClientId("")

                                    ApplicationMessage(
                                        message,
                                        senderClientId
                                    )
                                },
                                it.commitDelay
                            )
                        }
                    }
                } else {
                    Either.Right(null)
                }
            }
        }

    override suspend fun establishMLSGroupFromWelcome(welcomeEvent: MLSWelcome): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient().flatMap { client ->
            wrapMLSRequest { client.processWelcomeMessage(welcomeEvent.message.decodeBase64Bytes()) }
                .flatMap { groupID ->
                    kaliumLogger.i("Created conversation from welcome message (groupID = $groupID)")

                    wrapStorageRequest {
                        if (conversationDAO.getConversationByGroupID(groupID).first() != null) {
                            // Welcome arrived after the conversation create event, updating existing conversation.
                            conversationDAO.updateConversationGroupState(ConversationEntity.GroupState.ESTABLISHED, groupID)
                            kaliumLogger.i("Updated conversation from welcome message (groupID = $groupID)")
                        }
                    }
                }
        }

    override suspend fun hasEstablishedMLSGroup(groupID: GroupID): Either<CoreFailure, Boolean> =
        mlsClientProvider.getMLSClient()
            .flatMap {
                wrapMLSRequest {
                    it.conversationExists(idMapper.toCryptoModel(groupID))
                }
            }

    override suspend fun requestToJoinGroup(groupID: GroupID, epoch: ULong): Either<CoreFailure, Unit> {
        kaliumLogger.d("Requesting to re-join MLS group $groupID with epoch $epoch")
        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.joinConversation(idMapper.toCryptoModel(groupID), epoch)
            }.flatMap { message ->
                wrapApiRequest {
                    mlsMessageApi.sendMessage(
                        MLSMessageApi.Message(message)
                    )
                }.flatMap { Either.Right(Unit) }
            }.onSuccess {
                conversationDAO.updateConversationGroupState(
                    ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE,
                    idMapper.toCryptoModel(groupID)
                )
            }
        }
    }

    override suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<GroupID>> =
        wrapStorageRequest {
            conversationDAO.getConversationsByKeyingMaterialUpdate(threshold).map(idMapper::fromGroupIDEntity)
        }

    override suspend fun updateKeyingMaterial(groupID: GroupID): Either<CoreFailure, Unit> =
        retryOnCommitFailure(groupID) {
            mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                wrapMLSRequest {
                    mlsClient.updateKeyingMaterial(idMapper.toCryptoModel(groupID))
                }.flatMap { commitBundle ->
                    sendCommitBundle(groupID, commitBundle)
                }.flatMap {
                    wrapStorageRequest {
                        conversationDAO.updateKeyingMaterial(idMapper.toCryptoModel(groupID), Clock.System.now())
                    }
                }
            }
        }

    private suspend fun sendCommitBundle(groupID: GroupID, bundle: CommitBundle): Either<CoreFailure, Unit> {
        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapApiRequest {
                mlsMessageApi.sendMessage(MLSMessageApi.Message(bundle.commit))
            }.flatMap { response ->
                processCommitBundleEvents(response.events)
                wrapMLSRequest {
                    mlsClient.commitAccepted(idMapper.toCryptoModel(groupID))
                }
            }.flatMap {
                bundle.welcome?.let {
                    wrapApiRequest {
                        mlsMessageApi.sendWelcomeMessage(MLSMessageApi.WelcomeMessage(it))
                    }
                } ?: Either.Right(Unit)
            }
        }
    }

    private suspend fun processCommitBundleEvents(events: List<EventContentDTO>) {
        events.forEach { eventContentDTO ->
            val event = MapperProvider.eventMapper().fromEventContentDTO("", eventContentDTO)
            if (event is Event.Conversation) {
                commitBundleEventReceiver.onEvent(event)
            }
        }
    }

    override suspend fun commitPendingProposals(groupID: GroupID): Either<CoreFailure, Unit> =
        retryOnCommitFailure(groupID) {
            internalCommitPendingProposals(groupID)
        }

    private suspend fun internalCommitPendingProposals(groupID: GroupID): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient()
            .flatMap { mlsClient ->
                wrapMLSRequest {
                    mlsClient.commitPendingProposals(idMapper.toCryptoModel(groupID))
                }.flatMap { commitBundle ->
                    commitBundle?.let { sendCommitBundle(groupID, it) } ?: Either.Right(Unit)
                }.flatMap {
                    wrapStorageRequest {
                        conversationDAO.clearProposalTimer(idMapper.toCryptoModel(groupID))
                    }
                }
            }

    override suspend fun setProposalTimer(timer: ProposalTimer) {
        conversationDAO.setProposalTimer(conversationMapper.toDAOProposalTimer(timer))
    }

    override suspend fun observeProposalTimers(): Flow<List<ProposalTimer>> {
        return conversationDAO.getProposalTimers().map { it.map(conversationMapper::fromDaoModel) }
    }

    override suspend fun addMemberToMLSGroup(groupID: GroupID, userIdList: List<UserId>): Either<CoreFailure, Unit> =
        commitPendingProposals(groupID).flatMap {
            retryOnCommitFailure(groupID) {
                keyPackageRepository.claimKeyPackages(userIdList).flatMap { keyPackages ->
                    mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                        val clientKeyPackageList = keyPackages
                            .map {
                                Pair(
                                    CryptoQualifiedClientId(it.clientID, CryptoQualifiedID(it.userId, it.domain)),
                                    it.keyPackage.decodeBase64Bytes()
                                )
                            }

                        wrapMLSRequest {
                            mlsClient.addMember(idMapper.toCryptoModel(groupID), clientKeyPackageList)
                        }.flatMap { commitBundle ->
                            commitBundle?.let {
                                sendCommitBundle(groupID, it)
                            } ?: Either.Right(Unit)
                        }
                    }
                }
            }
        }

    override suspend fun removeMembersFromMLSGroup(groupID: GroupID, userIdList: List<UserId>): Either<CoreFailure, Unit> =
        commitPendingProposals(groupID).flatMap {
            retryOnCommitFailure(groupID) {
                wrapApiRequest { clientApi.listClientsOfUsers(userIdList.map { idMapper.toApiModel(it) }) }.map { userClientsList ->
                    val usersCryptoQualifiedClientIDs = userClientsList.flatMap { userClients ->
                        userClients.value.map { userClient ->
                            CryptoQualifiedClientId(userClient.id, idMapper.toCryptoQualifiedIDId(idMapper.fromApiModel(userClients.key)))
                        }
                    }
                    return@retryOnCommitFailure mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                        wrapMLSRequest {
                            mlsClient.removeMember(idMapper.toCryptoModel(groupID), usersCryptoQualifiedClientIDs)
                        }.flatMap {
                            sendCommitBundle(groupID, it)
                        }
                    }
                }
            }
        }

    override suspend fun leaveGroup(groupID: GroupID): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient().map { mlsClient ->
            wrapMLSRequest {
                mlsClient.wipeConversation(idMapper.toCryptoModel(groupID))
            }
        }

    override suspend fun establishMLSGroup(groupID: GroupID, members: List<UserId>): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            mlsPublicKeysRepository.getKeys().flatMap { publicKeys ->
                wrapMLSRequest {
                    mlsClient.createConversation(
                        idMapper.toCryptoModel(groupID),
                        publicKeys.map { mlsPublicKeysMapper.toCrypto(it) }
                    )
                }
            }.flatMap {
                addMemberToMLSGroup(groupID, members)
            }.flatMap {
                wrapStorageRequest {
                    conversationDAO.updateConversationGroupState(
                        ConversationEntity.GroupState.ESTABLISHED,
                        idMapper.toGroupIDEntity(groupID)
                    )
                }
            }
        }

    private suspend fun retryOnCommitFailure(groupID: GroupID, operation: suspend () -> Either<CoreFailure, Unit>) =
        operation()
            .flatMapLeft {
                handleCommitFailure(it, groupID, operation)
            }

    private suspend fun handleCommitFailure(
        failure: CoreFailure,
        groupID: GroupID,
        retryOperation: suspend () -> Either<CoreFailure, Unit>
    ): Either<CoreFailure, Unit> {
        return when (failure.getStrategy()) {
            CommitStrategy.KEEP_AND_RETRY -> keepCommitAndRetry(groupID)
            CommitStrategy.DISCARD_AND_RETRY -> discardCommitAndRetry(groupID, retryOperation)
            CommitStrategy.ABORT -> return discardCommit(groupID).flatMap { Either.Left(failure) }
        }.flatMapLeft {
            handleCommitFailure(it, groupID, retryOperation)
        }
    }

    private suspend fun keepCommitAndRetry(groupID: GroupID): Either<CoreFailure, Unit> {
        kaliumLogger.w("Migrating failed commit to new epoch and re-trying.")

        return syncManager.waitUntilLiveOrFailure().flatMap {
            internalCommitPendingProposals(groupID)
        }
    }

    private suspend fun discardCommitAndRetry(
        groupID: GroupID,
        operation: suspend () -> Either<CoreFailure, Unit>
    ): Either<CoreFailure, Unit> {
        kaliumLogger.w("Discarding failed commit and retry by re-generating the commit.")

        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.clearPendingCommit(idMapper.toCryptoModel(groupID))
            }.flatMap {
                syncManager.waitUntilLiveOrFailure().flatMap {
                    operation()
                }
            }
        }
    }

    private suspend fun discardCommit(groupID: GroupID): Either<CoreFailure, Unit> {
        kaliumLogger.w("Discarding the failed commit.")

        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.clearPendingCommit(idMapper.toCryptoModel(groupID))
            }
        }
    }
}
