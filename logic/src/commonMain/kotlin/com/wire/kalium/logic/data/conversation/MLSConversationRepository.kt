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

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.error.E2EIFailure
import com.wire.kalium.common.error.MLSFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.wrapApiRequest
import com.wire.kalium.common.error.wrapMLSRequest
import com.wire.kalium.common.error.wrapStorageRequest
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.Either.Left
import com.wire.kalium.common.functional.Either.Right
import com.wire.kalium.common.functional.flatMap
import com.wire.kalium.common.functional.flatMapLeft
import com.wire.kalium.common.functional.flatten
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.functional.left
import com.wire.kalium.common.functional.map
import com.wire.kalium.common.functional.onFailure
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.cryptography.CryptoQualifiedClientId
import com.wire.kalium.cryptography.E2EIClient
import com.wire.kalium.cryptography.MLSClient
import com.wire.kalium.cryptography.MlsCoreCryptoContext
import com.wire.kalium.cryptography.WireIdentity
import com.wire.kalium.logic.data.client.toDao
import com.wire.kalium.logic.data.client.toModel
import com.wire.kalium.logic.data.conversation.mls.MLSAdditionResult
import com.wire.kalium.logic.data.e2ei.CertificateRevocationListRepository
import com.wire.kalium.logic.data.e2ei.RevocationListChecker
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
import com.wire.kalium.logic.data.mls.MLSMemberAdder
import com.wire.kalium.logic.data.mls.MLSPublicKeys
import com.wire.kalium.logic.data.mlspublickeys.MLSPublicKeysRepository
import com.wire.kalium.logic.data.mlspublickeys.getRemovalKey
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.network.api.base.authenticated.client.ClientApi
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.util.DateTimeUtil
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import io.mockative.Mockable
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.datetime.Clock
import kotlin.io.encoding.Base64
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

internal data class ApplicationMessage(
    val message: ByteArray,
    val senderID: UserId,
    val senderClientID: ClientId
) {
    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other == null || this::class != other::class) return false

        other as ApplicationMessage

        if (!message.contentEquals(other.message)) return false
        if (senderID != other.senderID) return false
        if (senderClientID != other.senderClientID) return false

        return true
    }

    override fun hashCode(): Int {
        var result = message.contentHashCode()
        result = 31 * result + senderID.hashCode()
        result = 31 * result + senderClientID.hashCode()
        return result
    }
}

internal data class DecryptedMessageBundle(
    val groupID: GroupID,
    val applicationMessage: ApplicationMessage?,
    val commitDelay: Long?,
    val identity: WireIdentity?
)

@Suppress("TooManyFunctions", "LongParameterList")
@Mockable
internal interface MLSConversationRepository : MLSMemberAdder {
    suspend fun decryptMessage(
        mlsContext: MlsCoreCryptoContext,
        message: ByteArray,
        groupID: GroupID
    ): Either<CoreFailure, List<DecryptedMessageBundle>>

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
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        members: List<UserId>,
        publicKeys: MLSPublicKeys? = null,
        allowSkippingUsersWithoutKeyPackages: Boolean = false
    ): Either<CoreFailure, MLSAdditionResult>

    suspend fun establishMLSSubConversationGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        parentId: ConversationId
    ): Either<CoreFailure, Unit>

    suspend fun hasEstablishedMLSGroup(mlsContext: MlsCoreCryptoContext, groupID: GroupID): Either<CoreFailure, Boolean>

    suspend fun removeMembersFromMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        userIdList: List<UserId>
    ): Either<CoreFailure, Unit>

    suspend fun removeClientsFromMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        clientIdList: List<QualifiedClientID>
    ): Either<CoreFailure, Unit>

    suspend fun leaveGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID
    ): Either<CoreFailure, Unit>

    suspend fun joinGroupByExternalCommit(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        groupInfo: ByteArray
    ): Either<CoreFailure, Unit>

    /**
     * Determines whether the known group epoch is stale (outdated), when compared to the provided epoch.
     *
     * @param mlsContext The MLS (Messaging Layer Security) core crypto context.
     * @param groupID The ID of the group being evaluated.
     * @param currentRemoteEpoch The current epoch of the group to compare against.
     * @return An instance of [Either]. Returns [Either.Right] containing `true` if the provided epoch
     *         is outdated, or `false` otherwise. Returns [Either.Left] containing [CoreFailure] if any error occurs.
     */
    suspend fun isLocalGroupEpochStale(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        currentRemoteEpoch: ULong
    ): Either<CoreFailure, Boolean>

    suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<GroupID>>
    suspend fun updateKeyingMaterial(mlsContext: MlsCoreCryptoContext, groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun commitPendingProposals(mlsContext: MlsCoreCryptoContext, groupID: GroupID): Either<CoreFailure, Unit>
    suspend fun setProposalTimer(timer: ProposalTimer, inMemory: Boolean = false)
    suspend fun observeProposalTimers(): Flow<ProposalTimer>
    suspend fun rotateKeysAndMigrateConversations(
        mlsContext: MlsCoreCryptoContext,
        clientId: ClientId,
        e2eiClient: E2EIClient,
        certificateChain: String,
        groupIdList: List<GroupID>,
        isNewClient: Boolean = false
    ): Either<E2EIFailure, Unit>

    suspend fun getClientIdentity(
        mlsContext: MlsCoreCryptoContext,
        clientId: ClientId
    ): Either<CoreFailure, WireIdentity?>

    suspend fun getUserIdentity(
        mlsContext: MlsCoreCryptoContext,
        userId: UserId
    ): Either<CoreFailure, List<WireIdentity>>

    suspend fun getMembersIdentities(
        mlsClient: MLSClient,
        conversationId: ConversationId,
        userIds: List<UserId>
    ): Either<CoreFailure, Map<UserId, List<WireIdentity>>>

    suspend fun updateGroupIdAndState(
        conversationId: ConversationId,
        newGroupId: GroupID,
        newEpoch: Long,
        groupState: ConversationEntity.GroupState = ConversationEntity.GroupState.PENDING_JOIN
    ): Either<CoreFailure, Unit>
}

private sealed interface CommitStrategy {
    data object Retry : CommitStrategy
    data object Abort : CommitStrategy
    data class AddMissingUsers(val users: List<UserId>) : CommitStrategy
}

private fun CoreFailure.getStrategy(
    remainingAttempts: Int,
    retryOnClientMismatch: Boolean = true,
    retryOnStaleMessage: Boolean = true,
    retryOnGroupOutOfSync: Boolean = true,
): CommitStrategy {
    return if (this is MLSFailure.MessageRejected && remainingAttempts > 0) {
        when (val cause = this.cause) {
            NetworkFailure.MlsMessageRejectedFailure.ClientMismatch -> if (retryOnClientMismatch) {
                CommitStrategy.Retry
            } else {
                CommitStrategy.Abort
            }

            NetworkFailure.MlsMessageRejectedFailure.CommitMissingReferences -> CommitStrategy.Retry
            NetworkFailure.MlsMessageRejectedFailure.StaleMessage -> if (retryOnStaleMessage) {
                CommitStrategy.Retry
            } else {
                CommitStrategy.Abort
            }

            NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeIndex,
            NetworkFailure.MlsMessageRejectedFailure.InvalidLeafNodeSignature -> CommitStrategy.Abort

            is NetworkFailure.MlsMessageRejectedFailure.GroupOutOfSync -> if (retryOnGroupOutOfSync) {
                CommitStrategy.AddMissingUsers(cause.missingUsers)
            } else { CommitStrategy.Abort }
            NetworkFailure.MlsMessageRejectedFailure.MissingGroupInfo,
            is NetworkFailure.MlsMessageRejectedFailure.Other -> CommitStrategy.Abort
        }
    } else {
        CommitStrategy.Abort
    }
}

// TODO: refactor this repository as it's doing too much.
// A Repository should be a dummy class that get and set some values
@Suppress("TooManyFunctions", "LongParameterList", "LargeClass")
internal class MLSConversationDataSource(
    private val selfUserId: UserId,
    private val keyPackageRepository: KeyPackageRepository,
    private val conversationDAO: ConversationDAO,
    private val clientApi: ClientApi,
    private val mlsPublicKeysRepository: MLSPublicKeysRepository,
    private val proposalTimersFlow: MutableSharedFlow<ProposalTimer>,
    private val keyPackageLimitsProvider: KeyPackageLimitsProvider,
    private val revocationListChecker: RevocationListChecker,
    private val certificateRevocationListRepository: CertificateRevocationListRepository,
    private val mutex: Mutex,
    private val idMapper: IdMapper = MapperProvider.idMapper(),
    private val conversationMapper: ConversationMapper = MapperProvider.conversationMapper(selfUserId),
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : MLSConversationRepository {

    private val logger = kaliumLogger.withTextTag("MLSConversationDataSource")

    /**
     * Performs a work using the Mutex lock returning its result, exactly like the regular withLock function.
     * However, this will monitor the work and log warnings using the provided [workIdentifier]
     * every 10 seconds, while the work isn't completed.
     */
    @Suppress("MagicNumber")
    private suspend fun <T> Mutex.withLock(workIdentifier: String, block: suspend () -> T): T = coroutineScope {
        val asyncLockWork = async {
            withLock {
                block()
            }
        }
        val startInstant = Clock.System.now()
        val waitJob = launch(dispatchers.default) {
            while (asyncLockWork.isActive) {
                delay(10.seconds)
                if (asyncLockWork.isActive) {
                    val currentInstant = Clock.System.now()
                    val elapsedTime = currentInstant.minus(startInstant)
                    logger.w("Waiting for Mutex work '$workIdentifier' to complete for a long time! Elapsed time: $elapsedTime.")
                }
            }
        }
        asyncLockWork.invokeOnCompletion {
            waitJob.cancel()
        }
        asyncLockWork.await()
    }

    override suspend fun decryptMessage(
        mlsContext: MlsCoreCryptoContext,
        message: ByteArray,
        groupID: GroupID
    ): Either<CoreFailure, List<DecryptedMessageBundle>> {
        logger.d("Decrypting message for group ${groupID.toLogString()}")
        return wrapMLSRequest {
            mlsContext.decryptMessage(
                idMapper.toCryptoModel(groupID),
                message
            )
                .let { messages ->
                    messages.map {
                        it.crlNewDistributionPoints?.let { newDistributionPoints ->
                            checkRevocationList(mlsContext, newDistributionPoints)
                        }
                        it.toModel(groupID)
                    }
                }
        }
    }

    override suspend fun hasEstablishedMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID
    ): Either<CoreFailure, Boolean> = wrapMLSRequest {
        mlsContext.conversationExists(idMapper.toCryptoModel(groupID))
    }

    override suspend fun joinGroupByExternalCommit(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        groupInfo: ByteArray
    ): Either<CoreFailure, Unit> = wrapMLSRequest {
        logger.d("Joining group ${groupID.toLogString()} by external commit")
        mlsContext.joinByExternalCommit(groupInfo)
    }.onSuccess { welcomeBundle ->
        welcomeBundle.crlNewDistributionPoints?.let {
            checkRevocationList(mlsContext, it)
        }
    }.onSuccess {
        wrapStorageRequest {
            conversationDAO.updateConversationGroupState(
                ConversationEntity.GroupState.ESTABLISHED,
                idMapper.toCryptoModel(groupID)
            )
        }
    }.map { }

    override suspend fun isLocalGroupEpochStale(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        currentRemoteEpoch: ULong
    ): Either<CoreFailure, Boolean> = wrapMLSRequest {
        mlsContext.conversationEpoch(idMapper.toCryptoModel(groupID)) < currentRemoteEpoch
    }

    override suspend fun getMLSGroupsRequiringKeyingMaterialUpdate(threshold: Duration): Either<CoreFailure, List<GroupID>> =
        wrapStorageRequest {
            conversationDAO.getConversationsByKeyingMaterialUpdate(threshold).map(idMapper::fromGroupIDEntity)
        }

    override suspend fun updateKeyingMaterial(mlsContext: MlsCoreCryptoContext, groupID: GroupID): Either<CoreFailure, Unit> {
        logger.d("Updating keying material for group ${groupID.toLogString()}")
        return mutex.withLock {
            produceAndSendCommitWithRetryAndResult(mlsContext, groupID) {
                wrapMLSRequest {
                    mlsContext.updateKeyingMaterial(idMapper.toCryptoModel(groupID))
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
    }

    override suspend fun commitPendingProposals(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID
    ): Either<CoreFailure, Unit> {
        logger.d("Committing pending proposals for group ${groupID.toLogString()}")
        return wrapMLSRequest {
            mlsContext.commitPendingProposals(idMapper.toCryptoModel(groupID))
        }
            .flatMap {
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

    override suspend fun addMemberToMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        userIdList: List<UserId>,
        cipherSuite: CipherSuite
    ): Either<CoreFailure, Unit> =
        internalAddMemberToMLSGroup(
            mlsContext = mlsContext,
            groupID = groupID,
            userIdList = userIdList,
            retryOnStaleMessage = true,
            retryOnMissingUsers = true,
            allowPartialMemberList = false,
            cipherSuite = cipherSuite
        )
            .map { }

    private suspend fun internalAddMemberToMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        userIdList: List<UserId>,
        retryOnStaleMessage: Boolean,
        retryOnMissingUsers: Boolean,
        cipherSuite: CipherSuite,
        allowPartialMemberList: Boolean = false,
    ): Either<CoreFailure, MLSAdditionResult> = commitPendingProposals(mlsContext, groupID).flatMap {
        logger.d("adding ${userIdList.count()} users to MLS group ${groupID.toLogString()}")
        produceAndSendCommitWithRetryAndResult(
            mlsContext = mlsContext,
            groupID = groupID,
            retryOnStaleMessage = retryOnStaleMessage,
            retryOnMissingUsers = retryOnMissingUsers
        ) {
            keyPackageRepository.claimKeyPackages(userIdList, cipherSuite).flatMap { result ->
                if (result.usersWithoutKeyPackagesAvailable.isNotEmpty() && !allowPartialMemberList) {
                    logger.d(
                        "add members to MLS Group: failed " +
                                "${result.usersWithoutKeyPackagesAvailable.count()} user(s) missing KeyPackages"
                    )
                    Either.Left(CoreFailure.MissingKeyPackages(result.usersWithoutKeyPackagesAvailable))
                } else {
                    logger.d("add members to MLS Group: claiming KeyPackages succeed")
                    Either.Right(result)
                }
            }.flatMap { result ->
                val keyPackages = result.successfullyFetchedKeyPackages
                val clientKeyPackageList = keyPackages.map { Base64.decode(it.keyPackage) }
                wrapMLSRequest {
                    if (clientKeyPackageList.isEmpty()) {
                        // We are creating a group with only our self client which technically
                        // doesn't need be added with a commit, but our backend API requires one,
                        // so we create a commit by updating our key material.
                        logger.d("add members to MLS Group: updating keying material for self client")
                        mlsContext.updateKeyingMaterial(idMapper.toCryptoModel(groupID))
                        null
                    } else {
                        logger.d("add members to MLS Group: executing for groupID ${groupID.toLogString()}")
                        mlsContext.addMember(idMapper.toCryptoModel(groupID), clientKeyPackageList)
                    }
                }.onSuccess { crlNewDistributionPoints ->
                    crlNewDistributionPoints?.let { revocationList ->
                        logger.d("add members to MLS Group: checking revocation list")
                        checkRevocationList(mlsContext, revocationList)
                    }
                }.map {
                    val additionResult = MLSAdditionResult(
                        result.successfullyFetchedKeyPackages.map { user -> UserId(user.userId, user.domain) }.toSet(),
                        result.usersWithoutKeyPackagesAvailable.toSet()
                    )
                    additionResult
                }
            }
        }
    }

    override suspend fun removeMembersFromMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        userIdList: List<UserId>
    ): Either<CoreFailure, Unit> {
        logger.d("Removing ${userIdList.count()} users from MLS group ${groupID.toLogString()}")
        return mutex.withLock {
            commitPendingProposals(mlsContext, groupID).flatMap {
                produceAndSendCommitWithRetryAndResult(mlsContext, groupID) {
                    wrapApiRequest { clientApi.listClientsOfUsers(userIdList.map { it.toApi() }) }.map { userClientsList ->
                        val usersCryptoQualifiedClientIDs = userClientsList.flatMap { userClients ->
                            userClients.value.map { userClient ->
                                CryptoQualifiedClientId(
                                    userClient.id,
                                    idMapper.toCryptoQualifiedIDId(userClients.key.toModel())
                                )
                            }
                        }
                        return@produceAndSendCommitWithRetryAndResult wrapMLSRequest {
                            removeMember(idMapper.toCryptoModel(groupID), usersCryptoQualifiedClientIDs)
                        }
                    }
                }
            }
        }
    }

    override suspend fun removeClientsFromMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        clientIdList: List<QualifiedClientID>
    ): Either<CoreFailure, Unit> {
        logger.d("Attempt to remove ${clientIdList.count()} clients from MLS group ${groupID.toLogString()}")
        return mutex.withLock {
            commitPendingProposals(mlsContext, groupID).flatMap {
                produceAndSendCommitWithRetryAndResult(mlsContext, groupID, retryOnClientMismatch = false) {
                    val qualifiedClientIDs = clientIdList.map { userClient ->
                        CryptoQualifiedClientId(
                            userClient.clientId.value,
                            userClient.userId.toCrypto()
                        )
                    }
                    wrapMLSRequest { members(groupID.toCrypto()) }
                        .flatMap { memberList ->
                            val clientsToRemove = qualifiedClientIDs.filter { client ->
                                memberList.any {
                                    it == client
                                }
                            }
                            if (clientsToRemove.isEmpty()) {
                                logger.d("Clients were already removed from MLS group ${groupID.toLogString()}")
                                Either.Right(Unit)
                            } else {
                                logger.d("Removing ${clientsToRemove.count()} clients from MLS group ${groupID.toLogString()}")
                                wrapMLSRequest {
                                    removeMember(groupID.toCrypto(), clientsToRemove)
                                }
                            }
                        }
                }
            }
        }
    }

    override suspend fun leaveGroup(mlsContext: MlsCoreCryptoContext, groupID: GroupID): Either<CoreFailure, Unit> =
        wrapMLSRequest {
            mlsContext.wipeConversation(idMapper.toCryptoModel(groupID))
        }

    override suspend fun establishMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        members: List<UserId>,
        publicKeys: MLSPublicKeys?,
        allowSkippingUsersWithoutKeyPackages: Boolean
    ): Either<CoreFailure, MLSAdditionResult> {
        logger.d("Establishing MLS group ${groupID.toLogString()}")
        return mutex.withLock {
            val cipherSuite = mlsContext.getDefaultCipherSuite().toModel()
            val keys = publicKeys?.getRemovalKey(cipherSuite) ?: mlsPublicKeysRepository.getKeyForCipherSuite(cipherSuite)

            keys.flatMap { externalSenders ->
                establishMLSGroup(
                    mlsContext = mlsContext,
                    groupID = groupID,
                    members = members,
                    externalSenders = externalSenders,
                    allowPartialMemberList = allowSkippingUsersWithoutKeyPackages
                )
            }
        }
    }

    override suspend fun establishMLSSubConversationGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        parentId: ConversationId
    ): Either<CoreFailure, Unit> {
        logger.d("Establishing MLS sub-conversation group ${groupID.toLogString()} with parent $parentId")
        return mutex.withLock {
            conversationDAO.getMLSGroupIdByConversationId(parentId.toDao())?.let { parentGroupId ->
                val externalSenderKey = mlsContext.getExternalSenders(GroupID(parentGroupId).toCrypto())
                establishMLSGroup(
                    mlsContext = mlsContext,
                    groupID = groupID,
                    members = emptyList(),
                    externalSenders = externalSenderKey.value,
                    allowPartialMemberList = false
                ).map { Unit }
            } ?: Either.Left(StorageFailure.DataNotFound)
        }
    }

    private suspend fun establishMLSGroup(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        members: List<UserId>,
        externalSenders: ByteArray,
        allowPartialMemberList: Boolean = false,
    ): Either<CoreFailure, MLSAdditionResult> {
        logger.d("establish MLS group: ${groupID.toLogString()}")
        return wrapMLSRequest {
            mlsContext.createConversation(
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
                mlsContext = mlsContext,
                groupID = groupID,
                userIdList = members,
                retryOnStaleMessage = false,
                retryOnMissingUsers = true,
                allowPartialMemberList = allowPartialMemberList,
                cipherSuite = mlsContext.getDefaultCipherSuite().toModel()
            ).onFailure {
                wrapMLSRequest {
                    mlsContext.wipeConversation(groupID.toCrypto())
                }
            }
        }.flatMap { additionResult ->
            wrapStorageRequest {
                conversationDAO.updateMlsGroupStateAndCipherSuite(
                    ConversationEntity.GroupState.ESTABLISHED,
                    mlsContext.getDefaultCipherSuite().toDao(),
                    idMapper.toGroupIDEntity(groupID)
                )
            }.map { additionResult }
        }
    }

    override suspend fun rotateKeysAndMigrateConversations(
        mlsContext: MlsCoreCryptoContext,
        clientId: ClientId,
        e2eiClient: E2EIClient,
        certificateChain: String,
        groupIdList: List<GroupID>,
        isNewClient: Boolean
    ): Either<E2EIFailure, Unit> = wrapMLSRequest { mlsContext.saveX509Credential(e2eiClient, certificateChain) }
        .flatMap { crlNewDistributionPoints ->
            val existingGroupList =
                groupIdList.filter { hasEstablishedMLSGroup(mlsContext, it).fold({ false }, { hasEstablished -> hasEstablished }) }
            wrapMLSRequest { mlsContext.e2eiRotateGroups(existingGroupList.map { it.toCrypto() }) }
                .flatMap { wrapMLSRequest { mlsContext.generateKeyPackages(keyPackageLimitsProvider.refillAmount()) } }
                .flatMap { newKeyPackages ->
                    crlNewDistributionPoints?.let { checkRevocationList(mlsContext, it) }
                    if (!isNewClient) {
                        logger.w("enrollment for existing client: upload new keypackages and drop old ones")
                        keyPackageRepository
                            .replaceKeyPackages(clientId, newKeyPackages, mlsContext.getDefaultCipherSuite().toModel())
                            .flatMap {
                                logger.w("removing stale key packages")
                                wrapMLSRequest {
                                    mlsContext.removeStaleKeyPackages()
                                }
                            }
                            .fold({ failure ->
                                E2EIFailure.RotationAndMigration(failure).left()
                            }, {
                                Either.Right(Unit)
                            })
                    } else {
                        Either.Right(Unit)
                    }
                }
        }.fold({ failure ->
            if (failure is E2EIFailure.RotationAndMigration) {
                return failure.left()
            } else {
                E2EIFailure.RotationAndMigration(failure).left()
            }
        }, {
            Either.Right(Unit)
        })

    override suspend fun getClientIdentity(mlsContext: MlsCoreCryptoContext, clientId: ClientId) =
        wrapStorageRequest { conversationDAO.getE2EIConversationClientInfoByClientId(clientId.value) }
            .flatMap { conversationClientInfo ->
                wrapMLSRequest {
                    mlsContext.getDeviceIdentities(
                        conversationClientInfo.mlsGroupId,
                        listOf(
                            CryptoQualifiedClientId(
                                conversationClientInfo.clientId,
                                conversationClientInfo.userId.toModel().toCrypto()
                            )
                        )
                    ).firstOrNull()
                }
            }

    override suspend fun getUserIdentity(mlsContext: MlsCoreCryptoContext, userId: UserId) =
        wrapStorageRequest {
            if (userId == selfUserId) {
                conversationDAO.getEstablishedSelfMLSGroupId()
            } else {
                conversationDAO.getMLSGroupIdByUserId(userId.toDao())
            }
        }.flatMap { mlsGroupId ->
            wrapMLSRequest {
                mlsContext.getUserIdentities(
                    mlsGroupId,
                    listOf(userId.toCrypto())
                )[userId.value] ?: emptyList()
            }
        }

    override suspend fun getMembersIdentities(
        mlsClient: MLSClient,
        conversationId: ConversationId,
        userIds: List<UserId>
    ): Either<CoreFailure, Map<UserId, List<WireIdentity>>> =
        wrapStorageRequest {
            conversationDAO.getMLSGroupIdByConversationId(conversationId.toDao())
        }.flatMap { mlsGroupId ->
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
     * @param operation The operation to perform, which should return an [Either] containing a [CoreFailure] or [T].
     * @return An [Either] containing a [CoreFailure] or [Unit], indicating whether the operation was successful.
     *
     */
    private suspend fun <T> produceAndSendCommitWithRetryAndResult(
        mlsContext: MlsCoreCryptoContext,
        groupID: GroupID,
        retryOnClientMismatch: Boolean = true,
        retryOnStaleMessage: Boolean = true,
        retryOnMissingUsers: Boolean = true,
        operation: suspend MlsCoreCryptoContext.() -> Either<CoreFailure, T>
    ): Either<CoreFailure, T> = mlsContext.operation().flatMapLeft { failure ->
        handleCommitFailure(
            mlsContext = mlsContext,
            failure = failure,
            groupID = groupID,
            remainingAttempts = 2,
            retryOnClientMismatch = retryOnClientMismatch,
            retryOnStaleMessage = retryOnStaleMessage,
            retryOnMissingUsers = retryOnMissingUsers,
        ) { mlsContext.operation() }
    }

    private suspend fun <T> handleCommitFailure(
        mlsContext: MlsCoreCryptoContext,
        failure: CoreFailure,
        groupID: GroupID,
        remainingAttempts: Int,
        retryOnClientMismatch: Boolean,
        retryOnStaleMessage: Boolean,
        retryOnMissingUsers: Boolean,
        retryOperation: suspend () -> Either<CoreFailure, T>
    ): Either<CoreFailure, T> = // Handle error in case the sending fails
        when (
            val strategy = failure.getStrategy(
                remainingAttempts = remainingAttempts,
                retryOnClientMismatch = retryOnClientMismatch,
                retryOnStaleMessage = retryOnStaleMessage,
                retryOnGroupOutOfSync = retryOnMissingUsers,
            )
        ) {
            CommitStrategy.Retry -> {
                // In case of RETRY, retry the operation, sending the new commit
                logger.w("Failed commit, retrying operation")
                retryOperation()
                    .flatMapLeft {
                        handleCommitFailure(
                            mlsContext = mlsContext,
                            failure = it,
                            groupID = groupID,
                            remainingAttempts = remainingAttempts - 1,
                            retryOnClientMismatch = retryOnClientMismatch,
                            retryOnStaleMessage = retryOnStaleMessage,
                            retryOnMissingUsers = retryOnMissingUsers,
                            retryOperation = retryOperation
                        )
                    }
            }

            is CommitStrategy.AddMissingUsers -> {
                wrapStorageRequest { conversationDAO.getConversationByGroupID(groupID.value) }.flatMap {
                    val protocolInfo = it.protocolInfo
                    if (protocolInfo is ConversationEntity.ProtocolInfo.MLSCapable) {
                        Right(CipherSuite.fromTag(protocolInfo.cipherSuite.cipherSuiteTag))
                    } else {
                        Left(MLSFailure.ConversationDoesNotSupportMLS)
                    }
                }.flatMap { cipherSuite ->
                    internalAddMemberToMLSGroup(mlsContext, groupID, strategy.users, retryOnStaleMessage, false, cipherSuite)
                }.flatMap {
                    retryOperation()
                }.flatMapLeft {
                    handleCommitFailure(
                        mlsContext = mlsContext,
                        failure = it,
                        groupID = groupID,
                        remainingAttempts = remainingAttempts - 1,
                        retryOnClientMismatch = retryOnClientMismatch,
                        retryOnStaleMessage = retryOnStaleMessage,
                        retryOnMissingUsers = retryOnMissingUsers,
                        retryOperation = retryOperation
                    )
                }
            }

            CommitStrategy.Abort -> Left(failure)
        }

    private suspend fun checkRevocationList(mlsContext: MlsCoreCryptoContext, crlNewDistributionPoints: List<String>) {
        crlNewDistributionPoints.forEach { url ->
            revocationListChecker.check(mlsContext, url).map { newExpiration ->
                newExpiration?.let {
                    certificateRevocationListRepository.addOrUpdateCRL(url, it)
                }
            }
        }
    }

    override suspend fun updateGroupIdAndState(
        conversationId: ConversationId,
        newGroupId: GroupID,
        newEpoch: Long,
        groupState: ConversationEntity.GroupState
    ): Either<CoreFailure, Unit> =
        wrapStorageRequest {
            conversationDAO.updateMLSGroupIdAndState(
                conversationId.toDao(),
                idMapper.toCryptoModel(newGroupId),
                newEpoch,
                groupState
            )
        }
}
