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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.cryptography.CommitBundle
import com.wire.kalium.cryptography.CryptoCertificateStatus
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logger.obfuscateId
import com.wire.kalium.logic.CoreFailure
import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.MLSFailure
import com.wire.kalium.logic.NetworkFailure
import com.wire.kalium.logic.StorageFailure
import com.wire.kalium.logic.data.client.MLSClientProvider
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.event.EventDeliveryInfo
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.IdMapper
import com.wire.kalium.logic.data.id.QualifiedClientID
import com.wire.kalium.logic.data.id.toApi
import com.wire.kalium.logic.data.id.toCrypto
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.data.keypackage.KeyPackageLimitsProvider
import com.wire.kalium.logic.data.keypackage.KeyPackageRepository
import com.wire.kalium.logic.data.mls.CipherSuite
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.flatMap
import com.wire.kalium.logic.functional.flatMapLeft
import com.wire.kalium.logic.functional.flatten
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.functional.foldToEitherWhileRight
import com.wire.kalium.logic.functional.left
import com.wire.kalium.logic.functional.map
import com.wire.kalium.logic.functional.onFailure
import com.wire.kalium.logic.functional.onSuccess
import com.wire.kalium.logic.functional.right
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.incremental.EventSource
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
import com.wire.kalium.persistence.dao.message.LocalId
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.ktor.util.decodeBase64Bytes
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.withContext
import kotlin.time.Duration

data class ApplicationMessage(
    val message: ByteArray,
    val senderID: UserId,
    val senderClientID: ClientId
)

data class DecryptedMessageBundle(
    val groupID: GroupID,
    val applicationMessage: ApplicationMessage?,
    val commitDelay: Long?,
    val identity: E2EIdentity?
)

data class E2EIdentity(
    val clientId: CryptoQualifiedClientId,
    val handle: String,
    val displayName: String,
    val domain: String,
    val certificate: String,
    val status: CryptoCertificateStatus,
    val thumbprint: String
)

@Suppress("TooManyFunctions", "LongParameterList")
interface MLSConversationRepository {
    suspend fun decryptMessage(message: ByteArray, groupID: GroupID): Either<CoreFailure, List<DecryptedMessageBundle>>

    /**
     * Establishes an MLS (Messaging Layer Security) group with the specified group ID and members.
     *
     * Allows partial addition of members through the [allowSkippingUsersWithoutKeyPackages] parameter.
     * If this parameter is set to true, users without key packages will be ignored and the rest will be added to the group.
     *
     * @param groupID The ID of the group to be established. Must be of type [GroupID].
     * @param members The list of user IDs (of type [UserId]) to be added as members to the group.
     * @param allowSkippingUsersWithoutKeyPackages Flag indicating whether to allow a partial member list in case of some users
     * not having key packages available. Default value is false. If false, will return [Either.Left] containing
     * [CoreFailure.MissingKeyPackages] for the missing users.
     * @return An instance of [Either] indicating the result of the operation. It can be either [Either.Right] if the
     *         group was successfully established, or [Either.Left] if an error occurred. If successful, returns [Unit].
     *         Possible types of [Either.Left] are defined in the sealed interface [CoreFailure].
     */
    suspend fun establishMLSGroup(
        groupID: GroupID,
        members: List<UserId>,
        allowSkippingUsersWithoutKeyPackages: Boolean = false
    ): Either<CoreFailure, MLSAdditionResult>

    suspend fun establishMLSSubConversationGroup(groupID: GroupID, parentId: ConversationId): Either<CoreFailure, Unit>
    suspend fun hasEstablishedMLSGroup(groupID: GroupID): Either<CoreFailure, Boolean>
    suspend fun addMemberToMLSGroup(
        groupID: GroupID,
        userIdList: List<UserId>,
        cipherSuite: CipherSuite
    ): Either<CoreFailure, Unit>

    suspend fun removeMembersFromMLSGroup(groupID: GroupID, userIdList: List<UserId>): Either<CoreFailure, Unit>
    suspend fun removeClientsFromMLSGroup(groupID: GroupID, clientIdList: List<QualifiedClientID>): Either<CoreFailure, Unit>
    suspend fun leaveGroup(groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun requestToJoinGroup(groupID: GroupID, epoch: ULong): Either<CoreFailure, Unit>
    suspend fun joinGroupByExternalCommit(groupID: GroupID, groupInfo: ByteArray): Either<CoreFailure, Unit>
    suspend fun isGroupOutOfSync(groupID: GroupID, currentEpoch: ULong): Either<CoreFailure, Boolean>
    suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<GroupID>>
    suspend fun updateKeyingMaterial(groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun commitPendingProposals(groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun setProposalTimer(timer: ProposalTimer, inMemory: Boolean = false)
    suspend fun observeProposalTimers(): Flow<ProposalTimer>
    suspend fun rotateKeysAndMigrateConversations(
        clientId: ClientId,
        e2eiClient: E2EIClient,
        certificateChain: String,
        isNewClient: Boolean = false
    ): Either<E2EIFailure, Unit>

    suspend fun getClientIdentity(clientId: ClientId): Either<CoreFailure, WireIdentity?>
    suspend fun getUserIdentity(userId: UserId): Either<CoreFailure, List<WireIdentity>>

    suspend fun getMembersIdentities(
        conversationId: ConversationId,
        userIds: List<UserId>
    ): Either<CoreFailure, Map<UserId, List<WireIdentity>>>
}

private enum class CommitStrategy {
    KEEP_AND_RETRY,
    DISCARD_AND_RETRY,
    ABORT
}

private fun CoreFailure.getStrategy(
    remainingAttempts: Int,
    retryOnClientMismatch: Boolean = true,
    retryOnStaleMessage: Boolean = true
): CommitStrategy {
    return if (
        remainingAttempts > 0 &&
        this is NetworkFailure.ServerMiscommunication &&
        kaliumException is KaliumException.InvalidRequestError
    ) {
        if (this.kaliumException.isMlsClientMismatch() && retryOnClientMismatch) {
            CommitStrategy.DISCARD_AND_RETRY
        } else if (
            this.kaliumException.isMlsStaleMessage() && retryOnStaleMessage ||
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

// TODO: refactor this repository as it's doing too much.
// A Repository should be a dummy class that get and set some values
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class MLSConversationDataSource(
    private val selfUserId: UserId,
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
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val revocationListChecker: RevocationListChecker,
    private val certificateRevocationListRepository: CertificateRevocationListRepository,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
    private val mlsCommitBundleMapper: MLSCommitBundleMapper = MapperProvider.mlsCommitBundleMapper(),
    kaliumDispatcher: KaliumDispatcher = KaliumDispatcherImpl
) : MLSConversationRepository {

    /**
     * A dispatcher with limited parallelism of 1.
     * This means using this dispatcher only a single coroutine will be processed at a time.
     *
     * This used for operations where ordering is important. For example when sending commit to
     * add client to a group, this a two-step operation:
     *
     * 1. Create pending commit and send to distribution server
     * 2. Merge pending commit when accepted by distribution server
     *
     * Here's it's critical that no other operation like `decryptMessage` is performed
     * between step 1 and 2. We enforce this by dispatching all `decrypt` and `commit` operations
     * onto this serial dispatcher.
     */
    @OptIn(ExperimentalCoroutinesApi::class)
    private val serialDispatcher = kaliumDispatcher.default.limitedParallelism(1)

    override suspend fun decryptMessage(
        message: ByteArray,
        groupID: GroupID
    ): Either<CoreFailure, List<DecryptedMessageBundle>> = withContext(serialDispatcher) {
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.decryptMessage(
                    idMapper.toCryptoModel(groupID),
                    message
                ).let { messages ->
                    if (messages.any { it.hasEpochChanged }) {
                        kaliumLogger.d("Epoch changed for groupID = ${groupID.value.obfuscateId()}")
                        epochsFlow.emit(groupID)
                    }
                    messages.map {
                        it.crlNewDistributionPoints?.let { newDistributionPoints ->
                            checkRevocationList(newDistributionPoints)
                        }
                        it.toModel(groupID)
                    }
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
        kaliumLogger.d("Requesting to re-join MLS group ${groupID.toLogString()} with epoch $epoch")
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

    override suspend fun joinGroupByExternalCommit(
        groupID: GroupID,
        groupInfo: ByteArray
    ): Either<CoreFailure, Unit> = withContext(serialDispatcher) {
        kaliumLogger.d("Requesting to re-join MLS group ${groupID.toLogString()} via external commit")
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.joinByExternalCommit(groupInfo)
            }.flatMap { commitBundle ->
                commitBundle.crlNewDistributionPoints?.let {
                    checkRevocationList(it)
                }
                sendCommitBundleForExternalCommit(groupID, commitBundle)
            }.onSuccess {
                wrapStorageRequest {
                    conversationDAO.updateConversationGroupState(
                        ConversationEntity.GroupState.ESTABLISHED,
                        idMapper.toCryptoModel(groupID)
                    )
                }
            }
        }
    }

    override suspend fun isGroupOutOfSync(groupID: GroupID, currentEpoch: ULong): Either<CoreFailure, Boolean> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.conversationEpoch(idMapper.toCryptoModel(groupID)) < currentEpoch
            }
        }

    override suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<GroupID>> =
        wrapStorageRequest {
            conversationDAO.getConversationsByKeyingMaterialUpdate(threshold).map(idMapper::fromGroupIDEntity)
        }

    override suspend fun updateKeyingMaterial(groupID: GroupID): Either<CoreFailure, Unit> = withContext(serialDispatcher) {
        produceAndSendCommitWithRetry(groupID) {
            wrapMLSRequest {
                updateKeyingMaterial(idMapper.toCryptoModel(groupID))
            }
        }.flatMap {
            wrapStorageRequest {
                conversationDAO.updateKeyingMaterial(
                    idMapper.toCryptoModel(groupID),
                    DateTimeUtil.currentInstant()
                )
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

    private suspend fun sendCommitBundleForExternalCommit(
        groupID: GroupID,
        bundle: CommitBundle
    ): Either<CoreFailure, Unit> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapApiRequest {
                mlsMessageApi.sendCommitBundle(mlsCommitBundleMapper.toDTO(bundle))
            }.onFailure {
                wrapMLSRequest {
                    mlsClient.clearPendingGroupExternalCommit(idMapper.toCryptoModel(groupID))
                }
            }.flatMap {
                wrapMLSRequest {
                    mlsClient.mergePendingGroupFromExternalCommit(idMapper.toCryptoModel(groupID))
                }
            }
        }.onSuccess {
            epochsFlow.emit(groupID)
        }

    private suspend fun processCommitBundleEvents(events: List<EventContentDTO>) {
        events.forEach { eventContentDTO ->
            val event =
                MapperProvider.eventMapper(selfUserId).fromEventContentDTO(
                    LocalId.generate(),
                    eventContentDTO
                )
            if (event is Event.Conversation) {
                commitBundleEventReceiver.onEvent(event, EventDeliveryInfo(isTransient = true, source = EventSource.LIVE))
            }
        }
    }

    override suspend fun commitPendingProposals(
        groupID: GroupID
    ): Either<CoreFailure, Unit> = withContext(serialDispatcher) {
        produceAndSendCommitWithRetry(groupID) {
            getPendingCommitBundle(groupID)
        }.flatMap {
            wrapStorageRequest {
                conversationDAO.clearProposalTimer(idMapper.toCryptoModel(groupID))
            }
        }
    }

    private suspend fun commitPendingProposalsWithoutRetry(
        groupID: GroupID
    ): Either<CoreFailure, Unit> = withContext(serialDispatcher) {
        getPendingCommitBundle(groupID).flatMap {
            if (it != null) {
                sendCommitBundle(groupID, it)
            } else {
                Either.Right(Unit)
            }
        }.flatMap {
            wrapStorageRequest {
                conversationDAO.clearProposalTimer(idMapper.toCryptoModel(groupID))
            }
        }
    }

    private suspend fun getPendingCommitBundle(
        groupID: GroupID
    ): Either<CoreFailure, CommitBundle?> =
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.commitPendingProposals(idMapper.toCryptoModel(groupID))
            }.onSuccess { commitBundle ->
                commitBundle?.crlNewDistributionPoints?.let {
                    checkRevocationList(it)
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

    override suspend fun addMemberToMLSGroup(
        groupID: GroupID,
        userIdList: List<UserId>,
        cipherSuite: CipherSuite
    ): Either<CoreFailure, Unit> =
        internalAddMemberToMLSGroup(
            groupID = groupID,
            userIdList = userIdList,
            retryOnStaleMessage = true,
            allowPartialMemberList = false,
            cipherSuite = cipherSuite
        ).map { Unit }

    private suspend fun internalAddMemberToMLSGroup(
        groupID: GroupID,
        userIdList: List<UserId>,
        retryOnStaleMessage: Boolean,
        cipherSuite: CipherSuite,
        allowPartialMemberList: Boolean = false,
    ): Either<CoreFailure, MLSAdditionResult> = withContext(serialDispatcher) {
        commitPendingProposals(groupID).flatMap {
            produceAndSendCommitWithRetryAndResult(groupID, retryOnStaleMessage = retryOnStaleMessage) {
                keyPackageRepository.claimKeyPackages(userIdList, cipherSuite).flatMap { result ->
                    if (result.usersWithoutKeyPackagesAvailable.isNotEmpty() && !allowPartialMemberList) {
                        Either.Left(CoreFailure.MissingKeyPackages(result.usersWithoutKeyPackagesAvailable))
                    } else {
                        Either.Right(result)
                    }
                }.flatMap { result ->
                    val keyPackages = result.successfullyFetchedKeyPackages
                    val clientKeyPackageList = keyPackages.map { it.keyPackage.decodeBase64Bytes() }
                    wrapMLSRequest {
                        if (userIdList.isEmpty()) {
                            // We are creating a group with only our self client which technically
                            // doesn't need be added with a commit, but our backend API requires one,
                            // so we create a commit by updating our key material.
                            updateKeyingMaterial(idMapper.toCryptoModel(groupID))
                        } else {
                            addMember(idMapper.toCryptoModel(groupID), clientKeyPackageList)
                        }
                    }.onSuccess { commitBundle ->
                        commitBundle?.crlNewDistributionPoints?.let { revocationList ->
                            checkRevocationList(revocationList)
                        }
                    }.map {
                        val additionResult = MLSAdditionResult(
                            result.successfullyFetchedKeyPackages.map { user -> UserId(user.userId, user.domain) }.toSet(),
                            result.usersWithoutKeyPackagesAvailable.toSet()
                        )
                        CommitOperationResult(it, additionResult)
                    }
                }
            }
        }
    }

    override suspend fun removeMembersFromMLSGroup(
        groupID: GroupID,
        userIdList: List<UserId>
    ): Either<CoreFailure, Unit> = withContext(serialDispatcher) {
        commitPendingProposals(groupID).flatMap {
            produceAndSendCommitWithRetry(groupID) {
                wrapApiRequest { clientApi.listClientsOfUsers(userIdList.map { it.toApi() }) }.map { userClientsList ->
                    val usersCryptoQualifiedClientIDs = userClientsList.flatMap { userClients ->
                        userClients.value.map { userClient ->
                            CryptoQualifiedClientId(
                                userClient.id,
                                idMapper.toCryptoQualifiedIDId(userClients.key.toModel())
                            )
                        }
                    }
                    return@produceAndSendCommitWithRetry wrapMLSRequest {
                        removeMember(idMapper.toCryptoModel(groupID), usersCryptoQualifiedClientIDs)
                    }
                }
            }
        }
    }

    override suspend fun removeClientsFromMLSGroup(
        groupID: GroupID,
        clientIdList: List<QualifiedClientID>
    ): Either<CoreFailure, Unit> = withContext(serialDispatcher) {
        commitPendingProposals(groupID).flatMap {
            produceAndSendCommitWithRetry(groupID, retryOnClientMismatch = false) {
                val qualifiedClientIDs = clientIdList.map { userClient ->
                    CryptoQualifiedClientId(
                        userClient.clientId.value,
                        userClient.userId.toCrypto()
                    )
                }
                return@produceAndSendCommitWithRetry wrapMLSRequest {
                    removeMember(groupID.toCrypto(), qualifiedClientIDs)
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

    override suspend fun establishMLSGroup(
        groupID: GroupID,
        members: List<UserId>,
        allowSkippingUsersWithoutKeyPackages: Boolean,
    ): Either<CoreFailure, MLSAdditionResult> = withContext(serialDispatcher) {
        mlsClientProvider.getMLSClient().flatMap<MLSAdditionResult, CoreFailure, MLSClient> {
            mlsPublicKeysRepository.getKeyForCipherSuite(
                CipherSuite.fromTag(it.getDefaultCipherSuite())
            ).flatMap { key ->
                establishMLSGroup(
                    groupID = groupID,
                    members = members,
                    externalSenders = key,
                    allowPartialMemberList = allowSkippingUsersWithoutKeyPackages
                )
            }
        }
    }

    override suspend fun establishMLSSubConversationGroup(
        groupID: GroupID,
        parentId: ConversationId
    ): Either<CoreFailure, Unit> = withContext(serialDispatcher) {
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            conversationDAO.getMLSGroupIdByConversationId(parentId.toDao())?.let { parentGroupId ->
                val externalSenderKey = mlsClient.getExternalSenders(GroupID(parentGroupId).toCrypto())
                establishMLSGroup(
                    groupID = groupID,
                    members = emptyList(),
                    externalSenders = externalSenderKey.value,
                    allowPartialMemberList = false
                ).map { Unit }
            } ?: Either.Left(StorageFailure.DataNotFound)
        }
    }

    private suspend fun establishMLSGroup(
        groupID: GroupID,
        members: List<UserId>,
        externalSenders: ByteArray,
        allowPartialMemberList: Boolean = false,
    ): Either<CoreFailure, MLSAdditionResult> = withContext(serialDispatcher) {
        mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            wrapMLSRequest {
                mlsClient.createConversation(
                    idMapper.toCryptoModel(groupID),
                    externalSenders
                )
            }.flatMapLeft {
                if (it is MLSFailure.ConversationAlreadyExists) {
                    Either.Right(Unit)
                } else {
                    Either.Left(it)
                }
            }.flatMap {
                internalAddMemberToMLSGroup(
                    groupID = groupID,
                    userIdList = members,
                    retryOnStaleMessage = false,
                    allowPartialMemberList = allowPartialMemberList,
                    cipherSuite = CipherSuite.fromTag(mlsClient.getDefaultCipherSuite())
                ).onFailure {
                    wrapMLSRequest {
                        mlsClient.wipeConversation(groupID.toCrypto())
                    }
                }
            }.flatMap { additionResult ->
                wrapStorageRequest {
                    conversationDAO.updateConversationGroupState(
                        ConversationEntity.GroupState.ESTABLISHED,
                        idMapper.toGroupIDEntity(groupID)
                    )
                }.map { additionResult }
            }
        }
    }

    override suspend fun rotateKeysAndMigrateConversations(
        clientId: ClientId,
        e2eiClient: E2EIClient,
        certificateChain: String,
        isNewClient: Boolean
    ) = mlsClientProvider.getMLSClient(clientId).fold({
        E2EIFailure.MissingMLSClient(it).left()
    }, { mlsClient ->
        wrapMLSRequest {
            mlsClient.e2eiRotateAll(e2eiClient, certificateChain, keyPackageLimitsProvider.refillAmount().toUInt())
        }.fold({
            E2EIFailure.RotationAndMigration(it).left()
        }, { rotateBundle ->
            rotateBundle.crlNewDistributionPoints?.let {
                checkRevocationList(it)
            }
            if (!isNewClient) {
                kaliumLogger.w("enrollment for existing client: upload new keypackages and drop old ones")
                keyPackageRepository.replaceKeyPackages(clientId, rotateBundle.newKeyPackages).flatMapLeft {
                    return E2EIFailure.RotationAndMigration(it).left()
                }
            }
            kaliumLogger.w("send migration commits after key rotations")
            kaliumLogger.w("rotate bundles: ${rotateBundle.commits.size}")
            rotateBundle.commits.map {
                sendCommitBundle(GroupID(it.key), it.value)
            }.foldToEitherWhileRight(Unit) { value, _ -> value }.fold({ E2EIFailure.RotationAndMigration(it).left() }, {
                Unit.right()
            })
        })
    })

    override suspend fun getClientIdentity(clientId: ClientId) =
        wrapStorageRequest { conversationDAO.getE2EIConversationClientInfoByClientId(clientId.value) }.flatMap {
            mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                wrapMLSRequest {

                    mlsClient.getDeviceIdentities(
                        it.mlsGroupId,
                        listOf(CryptoQualifiedClientId(it.clientId, it.userId.toModel().toCrypto()))
                    ).firstOrNull()
                }
            }
        }

    override suspend fun getUserIdentity(userId: UserId) =
        wrapStorageRequest {
            if (userId == selfUserId) {
                conversationDAO.getEstablishedSelfMLSGroupId()
            } else {
                conversationDAO.getMLSGroupIdByUserId(userId.toDao())
            }
        }.flatMap { mlsGroupId ->
            mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                wrapMLSRequest {
                    mlsClient.getUserIdentities(
                        mlsGroupId,
                        listOf(userId.toCrypto())
                    )[userId.value] ?: emptyList()
                }
            }
        }

    override suspend fun getMembersIdentities(
        conversationId: ConversationId,
        userIds: List<UserId>
    ): Either<CoreFailure, Map<UserId, List<WireIdentity>>> =
        wrapStorageRequest {
            conversationDAO.getMLSGroupIdByConversationId(conversationId.toDao())
        }.flatMap { mlsGroupId ->
            mlsClientProvider.getMLSClient().flatMap { mlsClient ->
                wrapMLSRequest {
                    val userIdsAndIdentity = mutableMapOf<UserId, List<WireIdentity>>()

                    mlsClient.getUserIdentities(mlsGroupId, userIds.map { it.toCrypto() })
                        .forEach { (userIdValue, identities) ->
                            userIds.firstOrNull { it.value == userIdValue }?.also {
                                userIdsAndIdentity[it] = identities
                            }
                        }

                    userIdsAndIdentity
                }
            }
        }

    /**
     * Takes an operation that generates a commit, performs it and sends the commit to remote.
     * For convenience, it provides a MLSClient scope within the [operation].
     * In case of failure, will follow [CoreFailure.getStrategy], retrying the [operation], retrying the sending of the commit, or just
     * aborting.
     * If the [operation] produces a null commit, will skip the sending of the commit and just return success.
     *
     * @param groupID The ID of the group to send the commit for.
     * @param retryOnClientMismatch Whether to retry if a client mismatch occurs. Default is true.
     * @param retryOnStaleMessage Whether to retry if a stale message occurs. Default is true.
     * @param operation The operation to perform, which should return an [Either] containing a [CoreFailure] or [CommitBundle].
     * @return An [Either] containing a [CoreFailure] or [Unit], indicating whether the operation was successful.
     * @see produceAndSendCommitWithRetryAndResult
     */
    private suspend fun produceAndSendCommitWithRetry(
        groupID: GroupID,
        retryOnClientMismatch: Boolean = true,
        retryOnStaleMessage: Boolean = true,
        operation: suspend MLSClient.() -> Either<CoreFailure, CommitBundle?>
    ): Either<CoreFailure, Unit> = mlsClientProvider.getMLSClient().flatMap { mlsClient ->
        produceAndSendCommitWithRetryAndResult(
            groupID = groupID,
            retryOnClientMismatch = retryOnClientMismatch,
            retryOnStaleMessage = retryOnStaleMessage
        ) {
            mlsClient.operation().map { CommitOperationResult(it, Unit) }
        }
    }.map { Unit }

    /**
     * Takes an operation that generates a commit, performs it and sends the commit to remote.
     * This allows returning a value with the produced commit.
     * For convenience, it provides a MLSClient scope within the [operation].
     * In case of success, will return the latest result of the [operation], including the last result obtained during a retry.
     * In case of failure, will follow [CoreFailure.getStrategy], retrying the [operation], retrying the sending of the commit, or just
     * aborting.
     * If the [operation] produces a null commit, will skip the sending of the commit and just return success.
     *
     * @param groupID The ID of the group to send the commit for.
     * @param retryOnClientMismatch Whether to retry if a client mismatch occurs. Default is true.
     * @param retryOnStaleMessage Whether to retry if a stale message occurs. Default is true.
     * @param operation The operation to perform, which should return an [Either] containing a [CoreFailure] or [CommitOperationResult].
     * @return An [Either] containing a [CoreFailure] or [Unit], indicating whether the operation was successful.
     * @see produceAndSendCommitWithRetry
     */
    private suspend fun <T> produceAndSendCommitWithRetryAndResult(
        groupID: GroupID,
        retryOnClientMismatch: Boolean = true,
        retryOnStaleMessage: Boolean = true,
        operation: suspend MLSClient.() -> Either<CoreFailure, CommitOperationResult<T>>
    ): Either<CoreFailure, T> = mlsClientProvider.getMLSClient().flatMap { mlsClient ->
        mlsClient.operation().fold({
            kaliumLogger.w("Failure to produce commit. Aborting retry.")
            // Failure to generate commit. Nothing to retry
            Either.Left(it)
        }, { operationResult ->
            // Try sending the produced commit (or skip if null), and return the produced result
            val commitBundle = operationResult.commitBundle ?: return@fold Either.Right(operationResult.result)
            sendCommitBundle(groupID, commitBundle).map {
                operationResult
            }.flatMapLeft { failure ->
                handleCommitFailure(
                    failure = failure,
                    groupID = groupID,
                    currentOperationResult = operationResult,
                    remainingAttempts = 2,
                    retryOnClientMismatch = retryOnClientMismatch,
                    retryOnStaleMessage = retryOnStaleMessage,
                ) { mlsClient.operation() }
            }.map { it.result }
        })
    }

    private suspend fun <T> handleCommitFailure(
        failure: CoreFailure,
        groupID: GroupID,
        currentOperationResult: CommitOperationResult<T>,
        remainingAttempts: Int,
        retryOnClientMismatch: Boolean,
        retryOnStaleMessage: Boolean,
        retryOperation: suspend () -> Either<CoreFailure, CommitOperationResult<T>>
    ): Either<CoreFailure, CommitOperationResult<T>> = // Handle error in case the sending fails
        when (
            failure.getStrategy(
                remainingAttempts = remainingAttempts,
                retryOnClientMismatch = retryOnClientMismatch,
                retryOnStaleMessage = retryOnStaleMessage
            )
        ) {
            CommitStrategy.KEEP_AND_RETRY -> {
                // If we keep the commit, and resending it works, return the previous result
                keepCommitAndRetry(groupID).map { currentOperationResult }.flatMapLeft {
                    handleCommitFailure(
                        failure = it,
                        groupID = groupID,
                        currentOperationResult = currentOperationResult,
                        remainingAttempts = remainingAttempts - 1,
                        retryOnClientMismatch = retryOnClientMismatch,
                        retryOnStaleMessage = retryOnStaleMessage,
                        retryOperation = retryOperation
                    )
                }
            }

            CommitStrategy.DISCARD_AND_RETRY -> {
                // In case of DISCARD AND RETRY, discard pending commits and retry the operation, sending the new commit
                kaliumLogger.w("Discarding failed commit and retrying operation")
                discardCommitForRetrying(groupID).flatMap { retryOperation() }.flatMap { newResult ->
                    val commitBundle = newResult.commitBundle ?: return@flatMap Either.Right(newResult)
                    sendCommitBundle(groupID, commitBundle).map { newResult }.flatMapLeft {
                        handleCommitFailure(
                            failure = it,
                            groupID = groupID,
                            currentOperationResult = newResult,
                            remainingAttempts = remainingAttempts - 1,
                            retryOnClientMismatch = retryOnClientMismatch,
                            retryOnStaleMessage = retryOnStaleMessage,
                            retryOperation = retryOperation
                        )
                    }
                }
            }

            CommitStrategy.ABORT -> discardCommit(groupID).flatMap { Either.Left(failure) }
        }

    private suspend fun keepCommitAndRetry(groupID: GroupID): Either<CoreFailure, Unit> {
        kaliumLogger.w("Migrating failed commit to new epoch and re-trying.")

        return syncManager.waitUntilLiveOrFailure().flatMap {
            commitPendingProposalsWithoutRetry(groupID)
        }
    }

    private suspend fun discardCommitForRetrying(
        groupID: GroupID,
    ): Either<CoreFailure, Unit> = mlsClientProvider.getMLSClient().flatMap { mlsClient ->
        wrapMLSRequest {
            mlsClient.clearPendingCommit(idMapper.toCryptoModel(groupID))
        }.flatMap {
            syncManager.waitUntilLiveOrFailure()
        }
    }

    private suspend fun discardCommit(groupID: GroupID): Either<CoreFailure, Unit> {
        kaliumLogger.w("Discarding the failed commit.")

        return mlsClientProvider.getMLSClient().flatMap { mlsClient ->
            @Suppress("TooGenericExceptionCaught")
            try {
                mlsClient.clearPendingCommit(idMapper.toCryptoModel(groupID))
            } catch (error: Throwable) {
                kaliumLogger.e("Discarding pending commit failed: $error")
            }
            Either.Right(Unit)
        }
    }

    private suspend fun checkRevocationList(crlNewDistributionPoints: List<String>) {
        crlNewDistributionPoints.forEach { url ->
            revocationListChecker.check(url).map { newExpiration ->
                newExpiration?.let {
                    certificateRevocationListRepository.addOrUpdateCRL(url, it)
                }
            }
        }
    }

    private data class CommitOperationResult<T>(val commitBundle: CommitBundle?, val result: T)
}
