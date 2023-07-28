/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.CryptoQualifiedID
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.Event.Conversation.MLSWelcome
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysMapper
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.flatten
import com.wire.kalium.logic.functional.fold
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
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.DateTimeUtil
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlin.time.Duration

data class ApplicationMessage(
    val message: ByteArray,
    val senderClientID: ClientId
)

data class DecryptedMessageBundle(
    val groupID: GroupID,
    val applicationMessage: ApplicationMessage?,
    val commitDelay: Long?,
    val identity: E2EIdentity?
)

data class E2EIdentity(var clientId: String, var handle: String, var displayName: String, var domain: String)

@Suppress("TooManyFunctions", "LongParameterList")
interface MLSConversationRepository {
    suspend fun establishMLSGroup(groupID: GroupID, members: List<UserId>): Either<CoreFailure, Unit>
    suspend fun establishMLSGroupFromWelcome(welcomeEvent: MLSWelcome): Either<CoreFailure, Unit>
    suspend fun hasEstablishedMLSGroup(groupID: GroupID): Either<CoreFailure, Boolean>
    suspend fun addMemberToMLSGroup(groupID: GroupID, userIdList: List<UserId>): Either<CoreFailure, Unit>
    suspend fun removeMembersFromMLSGroup(groupID: GroupID, userIdList: List<UserId>): Either<CoreFailure, Unit>
    suspend fun removeClientsFromMLSGroup(groupID: GroupID, clientIdList: List<QualifiedClientID>): Either<CoreFailure, Unit>
    suspend fun leaveGroup(groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun requestToJoinGroup(groupID: GroupID, epoch: ULong): Either<CoreFailure, Unit>
    suspend fun joinGroupByExternalCommit(groupID: GroupID, groupInfo: ByteArray): Either<CoreFailure, Unit>
    suspend fun isGroupOutOfSync(groupID: GroupID, currentEpoch: ULong): Either<CoreFailure, Boolean>
    suspend fun clearJoinViaExternalCommit(groupID: GroupID)
    suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<GroupID>>
    suspend fun updateKeyingMaterial(groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun commitPendingProposals(groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun setProposalTimer(timer: ProposalTimer, inMemory: Boolean = false)
    suspend fun observeProposalTimers(): Flow<ProposalTimer>
    suspend fun observeEpochChanges(): Flow<GroupID>
    suspend fun getConversationVerificationStatus(groupID: GroupID): Either<CoreFailure, ConversationVerificationStatus>
}

private enum class CommitStrategy {
    KEEP_AND_RETRY,
    DISCARD_AND_RETRY,
    ABORT
}

private fun CoreFailure.getStrategy(retryOnClientMismatch: Boolean = true): CommitStrategy {
    return if (this is NetworkFailure.ServerMiscommunication && this.kaliumException is KaliumException.InvalidRequestError) {
        if (this.kaliumException.isMlsClientMismatch() && retryOnClientMismatch) {
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
internal class MLSConversationDataSource(
    private val keyPackageRepository: KeyPackageRepository,
    private val mlsClientProvider: MLSClientProvider,
    private val mlsMessageApi: MLSMessageApi,
    private val conversationDAO: ConversationDAO,
    private val clientApi: ClientApi,
    private val syncManager: SyncManager,
    private val mlsPublicKeysRepository: MLSPublicKeysRepository,
    private val commitBundleEventReceiver: CommitBundleEventReceiver,
    private val epochsFlow: MutableSharedFlow<GroupID>,
    private val proposalTimersFlow: MutableSharedFlow<ProposalTimer>,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(),
    private val mlsPublicKeysMapper: MLSPublicKeysMapper = MapperProvider.mlsPublicKeyMapper(),
    private val mlsCommitBundleMapper: MLSCommitBundleMapper = MapperProvider.mlsCommitBundleMapper()
) : MLSConversationRepository {

    override suspend fun establishMLSGroupFromWelcome(welcomeEvent: MLSWelcome): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient().flatMap { client ->
            wrapMLSRequest { client.processWelcomeMessage(welcomeEvent.message.decodeBase64Bytes()) }
                .flatMap { groupID ->
                    kaliumLogger.i("Created conversation from welcome message (groupID = $groupID)")

                    wrapStorageRequest {
                        if (conversationDAO.getConversationByGroupID(groupID).first() != null) {
                            // Welcome arrived after the conversation create event, updating existing conversation.
                            conversationDAO.updateConversationGroupState(
                                ConversationEntity.GroupState.ESTABLISHED,
                                groupID
                            )
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

    override suspend fun joinGroupByExternalCommit(groupID: GroupID, groupInfo: ByteArray): Either<CoreFailure, Unit> {
        kaliumLogger.d("Requesting to re-join MLS group $groupID via external commit")
        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.joinByExternalCommit(groupInfo)
            }.flatMap { commitBundle ->
                sendCommitBundleForExternalCommit(groupID, commitBundle)
            }.onSuccess {
                conversationDAO.updateConversationGroupState(
                    ConversationEntity.GroupState.ESTABLISHED,
                    idMapper.toCryptoModel(groupID)
                )
            }

        }
    }

    override suspend fun isGroupOutOfSync(groupID: GroupID, currentEpoch: ULong): Either<CoreFailure, Boolean> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.conversationEpoch(idMapper.toCryptoModel(groupID)) < currentEpoch
            }
        }

    override suspend fun clearJoinViaExternalCommit(groupID: GroupID) {
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.clearPendingGroupExternalCommit(idMapper.toCryptoModel(groupID))
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
                        conversationDAO.updateKeyingMaterial(
                            idMapper.toCryptoModel(groupID),
                            DateTimeUtil.currentInstant()
                        )
                    }
                }
            }
        }

    private suspend fun sendCommitBundle(groupID: GroupID, bundle: CommitBundle): Either<CoreFailure, Unit> {
        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapApiRequest {
                mlsMessageApi.sendCommitBundle(mlsCommitBundleMapper.toDTO(bundle))
            }.flatMap { response ->
                processCommitBundleEvents(response.events)
                wrapMLSRequest {
                    mlsClient.commitAccepted(idMapper.toCryptoModel(groupID))
                }
            }.onSuccess {
                epochsFlow.emit(groupID)
            }
        }
    }

    private suspend fun sendCommitBundleForExternalCommit(groupID: GroupID, bundle: CommitBundle): Either<CoreFailure, Unit> {
        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapApiRequest {
                mlsMessageApi.sendCommitBundle(mlsCommitBundleMapper.toDTO(bundle))
            }.fold({
                wrapMLSRequest {
                    mlsClient.clearPendingGroupExternalCommit(idMapper.toCryptoModel(groupID))
                }
            }, {
                wrapMLSRequest {
                    mlsClient.mergePendingGroupFromExternalCommit(idMapper.toCryptoModel(groupID))
                }
            }).onSuccess {
                epochsFlow.emit(groupID)
            }
        }
    }

    private suspend fun processCommitBundleEvents(events: List<EventContentDTO>) {
        events.forEach { eventContentDTO ->
            val event = MapperProvider.eventMapper().fromEventContentDTO("", eventContentDTO, true)
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

    override suspend fun setProposalTimer(timer: ProposalTimer, inMemory: Boolean) {
        if (inMemory) {
            proposalTimersFlow.emit(timer)
        } else {
            conversationDAO.setProposalTimer(conversationMapper.toDAOProposalTimer(timer))
        }
    }

    override suspend fun observeProposalTimers(): Flow<ProposalTimer> =
        merge(
            proposalTimersFlow,
            conversationDAO.getProposalTimers().map { it.map(conversationMapper::fromDaoModel) }.flatten()
        )

    override suspend fun observeEpochChanges(): Flow<GroupID> {
        return epochsFlow
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
                            if (userIdList.isEmpty()) {
                                // We are creating a group with only our self client which technically
                                // doesn't need be added with a commit, but our backend API requires one,
                                // so we create a commit by updating our key material.
                                mlsClient.updateKeyingMaterial(idMapper.toCryptoModel(groupID))
                            } else {
                                mlsClient.addMember(idMapper.toCryptoModel(groupID), clientKeyPackageList)
                            }
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
                wrapApiRequest { clientApi.listClientsOfUsers(userIdList.map { it.toApi() }) }.map { userClientsList ->
                    val usersCryptoQualifiedClientIDs = userClientsList.flatMap { userClients ->
                        userClients.value.map { userClient ->
                            CryptoQualifiedClientId(
                                userClient.id,
                                idMapper.toCryptoQualifiedIDId(userClients.key.toModel())
                            )
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

    override suspend fun removeClientsFromMLSGroup(groupID: GroupID, clientIdList: List<QualifiedClientID>): Either<CoreFailure, Unit> =
        commitPendingProposals(groupID).flatMap {
            retryOnCommitFailure(groupID, retryOnClientMismatch = false) {
                val qualifiedClientIDs = clientIdList.map { userClient ->
                    CryptoQualifiedClientId(
                        userClient.clientId.value,
                        userClient.userId.toCrypto()
                    )
                }
                return@retryOnCommitFailure mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                    wrapMLSRequest {
                        mlsClient.removeMember(groupID.toCrypto(), qualifiedClientIDs)
                    }.flatMap {
                        sendCommitBundle(groupID, it)
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

    override suspend fun getConversationVerificationStatus(groupID: GroupID): Either<CoreFailure, ConversationVerificationStatus> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest { mlsClient.isGroupVerified(idMapper.toCryptoModel(groupID)) }
        }.map {
            if (it) ConversationVerificationStatus.VERIFIED
            else ConversationVerificationStatus.NOT_VERIFIED
        }

    private suspend fun retryOnCommitFailure(
        groupID: GroupID,
        retryOnClientMismatch: Boolean = true,
        operation: suspend () -> Either<CoreFailure, Unit>
    ) =
        operation()
            .flatMapLeft {
                handleCommitFailure(it, groupID, retryOnClientMismatch, operation)
            }

    private suspend fun handleCommitFailure(
        failure: CoreFailure,
        groupID: GroupID,
        retryOnClientMismatch: Boolean,
        retryOperation: suspend () -> Either<CoreFailure, Unit>
    ): Either<CoreFailure, Unit> {
        return when (failure.getStrategy(retryOnClientMismatch)) {
            CommitStrategy.KEEP_AND_RETRY -> keepCommitAndRetry(groupID)
            CommitStrategy.DISCARD_AND_RETRY -> discardCommitAndRetry(groupID, retryOperation)
            CommitStrategy.ABORT -> return discardCommit(groupID).flatMap { Either.Left(failure) }
        }.flatMapLeft {
            handleCommitFailure(it, groupID, retryOnClientMismatch, retryOperation)
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
