/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature.conversation.mls

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.error.NetworkFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.FetchMLSOneToOneConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCaseImpl
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.mls.PersistentPendingActionsRepository
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.GroupID
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.id.toModel
import com.wire.kalium.logic.di.MapperProvider
import com.wire.kalium.logic.featureFlags.FeatureSupport
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncStateObserver
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangement
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.network.api.base.authenticated.conversation.ConversationApi
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.persistence.dao.TeamEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.pendingaction.PendingActionType
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import dev.mokkery.MockMode
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Persisted-action/canonical-ID redirect with a real file-backed database, action repository, drain and join helper.
 * The original is a team-backed fake 1:1 whose native group is missing; the healthy canonical replacement is teamless.
 */
class PersistedMLSCanonicalRedirectRegressionTest {
    @Test
    fun replacementJoinNeverAcknowledgesOriginalUntilExactRecoveryJoinsIt() = runBlocking {
        withFixture { f ->
            f.database.pendingActionDAO.upsert(PendingActionType.JOIN_MLS_GROUP_CONVERSATION, ORIGINAL.toDao(), 1L)
            f.exactFetchFails = true
            f.restart()
            f.drain()

            assertEquals(listOf(REPLACEMENT), f.joined)
            f.assertOriginal(recovered = false)
            f.restart()
            assertEquals(listOf(ORIGINAL), f.actions(PendingActionType.JOIN_MLS_GROUP_CONVERSATION))
            assertEquals(listOf(ORIGINAL), f.actions(PendingActionType.RECOVER_MLS_GROUP_CONVERSATION))

            f.exactFetchFails = false
            f.drain()
            f.assertOriginal(recovered = true)
            f.restart()
            f.drain()
            assertTrue(f.actions(PendingActionType.JOIN_MLS_GROUP_CONVERSATION).isEmpty())
            assertTrue(f.actions(PendingActionType.RECOVER_MLS_GROUP_CONVERSATION).isEmpty())

            // Both are established now; only the teamless replacement remains eligible as the canonical 1:1.
            val resolver = MLSOneOnOneConversationResolverImpl(f.conversations, f.joinHelper, f.fetchCanonical, f.mlsRepository)
            assertEquals(REPLACEMENT, assertIs<Either.Right<ConversationId>>(resolver(f.transactionContext, PEER)).value)
        }
    }

    private suspend fun withFixture(block: suspend (Fixture) -> Unit) {
        val directory = Files.createTempDirectory("mls-canonical-redirect-").toFile()
        val fixture = Fixture(directory)
        try {
            fixture.seed()
            fixture.wire()
            block(fixture)
        } finally {
            fixture.database.close()
            directory.deleteRecursively()
        }
    }

    private class Fixture(private val directory: File) :
        CryptoTransactionProviderArrangement by CryptoTransactionProviderArrangementImpl() {
        var database = open()
        val pending get() = PersistentPendingActionsRepository(database.pendingActionDAO)
        val nativeGroups = mutableSetOf(REPLACEMENT_GROUP)
        val joined = mutableListOf<ConversationId>()
        var exactFetchFails = false
        lateinit var conversations: ConversationRepository
        lateinit var mlsRepository: MLSConversationRepository
        lateinit var fetchCanonical: FetchMLSOneToOneConversationUseCase
        lateinit var joinHelper: JoinExistingMLSConversationUseCase
        private lateinit var sync: SyncStateObserver

        private fun open(): UserDatabaseBuilder = userDatabaseBuilder(
            PlatformDatabaseData(StorageData.FileBacked(File(directory, "account.db"))), SELF.toDao(), null, Dispatchers.IO
        )

        suspend fun seed() {
            database.teamDAO.insertTeam(TeamEntity(ORIGINAL_TEAM, "Original team", "default"))
            for (user in listOf(SELF, PEER)) database.userDAO.upsertUser(TestUser.ENTITY.copy(
                id = user.toDao(), team = ORIGINAL_TEAM, previewAssetId = null, completeAssetId = null, activeOneOnOneConversationId = null
            ))
            for ((id, group, state) in listOf(
                Triple(ORIGINAL, ORIGINAL_GROUP, ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE),
                Triple(REPLACEMENT, REPLACEMENT_GROUP, ConversationEntity.GroupState.ESTABLISHED)
            )) {
                database.conversationDAO.insertConversation(TestConversation.ENTITY.copy(
                    id = id.toDao(), teamId = if (id == ORIGINAL) ORIGINAL_TEAM else null,
                    creatorId = SELF.value, type = ConversationEntity.Type.ONE_ON_ONE,
                    protocolInfo = ConversationEntity.ProtocolInfo.MLS(group, state, 3UL, Instant.fromEpochSeconds(0),
                        ConversationEntity.CipherSuite.MLS_128_DHKEMP256_AES128GCM_SHA256_P256)
                ))
                database.memberDAO.insertMembersWithQualifiedId(listOf(SELF, PEER).map {
                    MemberEntity(it.toDao(), MemberEntity.Role.Member)
                }, id.toDao())
            }
            database.userDAO.updateActiveOneOnOneConversation(PEER.toDao(), REPLACEMENT.toDao())
        }

        suspend fun restart() {
            database.close()
            database = open()
            wire() // Fresh repository/helper/drain; the action exists only in the reopened SQL database.
        }

        private suspend fun row(id: ConversationId) = checkNotNull(database.conversationDAO.getNonDeletedConversationById(id.toDao()))
        private fun ConversationEntity.model() = MapperProvider.conversationMapper(SELF).fromDaoModel(this)
        suspend fun actions(type: PendingActionType) = database.pendingActionDAO.getByActionType(type).map { it.qualifiedId.toModel() }

        suspend fun assertOriginal(recovered: Boolean) {
            val expected = if (recovered) GroupStateEntity.ESTABLISHED else GroupStateEntity.PENDING_WELCOME_MESSAGE
            assertEquals(expected, (row(ORIGINAL).protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupState)
            assertEquals(recovered, ORIGINAL_GROUP in nativeGroups)
            assertEquals(REPLACEMENT.toDao(), database.userDAO.getUserDetailsByQualifiedID(PEER.toDao())?.activeOneOnOneConversationId)
        }

        suspend fun wire() {
            withTransactionReturning(Either.Right(Unit))
            everySuspend { mlsContext.conversationExists(any()) } calls { it.args[0] as String in nativeGroups }
            conversations = mock<ConversationRepository>(MockMode.autoUnit)
            everySuspend { conversations.getNonDeletedConversationById(any()) } calls {
                val row = database.conversationDAO.getNonDeletedConversationById((it.args[0] as ConversationId).toDao())
                row?.let { found -> Either.Right(found.model()) } ?: Either.Left(StorageFailure.DataNotFound)
            }
            everySuspend { conversations.getConversationMembers(any()) } calls {
                val id = (it.args[0] as ConversationId).toDao()
                Either.Right(database.memberDAO.getConversationMembers(id).map { member -> member.toModel() })
            }
            everySuspend { conversations.getConversationsByGroupState(any()) } calls { call ->
                val state = call.args[0] as Conversation.ProtocolInfo.MLSCapable.GroupState
                Either.Right(database.conversationDAO.getConversationsByGroupState(ConversationEntity.GroupState.valueOf(state.name))
                    .map { it.model() })
            }
            everySuspend { conversations.getConversationsByUserId(PEER) } calls {
                Either.Right(database.conversationDAO.getConversationsByUserId(PEER.toDao()).map { it.model() })
            }
            val feature = mock<FeatureSupport>()
            every { feature.isMLSSupported } returns true
            val clients = mock<ClientRepository>()
            everySuspend { clients.hasRegisteredMLSClient() } returns Either.Right(true)
            sync = mock<SyncStateObserver>()
            everySuspend { sync.waitUntilLiveOrFailure() } returns Either.Right(Unit)
            fetchCanonical = mock<FetchMLSOneToOneConversationUseCase>()
            everySuspend { fetchCanonical(any(), PEER) } calls { Either.Right(row(REPLACEMENT).model()) }
            val fetchExact = mock<FetchConversationUseCase>()
            everySuspend { fetchExact(any(), any(), any()) } calls {
                assertEquals(ORIGINAL, it.args[1] as ConversationId)
                if (exactFetchFails) Either.Left(NetworkFailure.NoNetworkConnection(null)) else Either.Right(Unit)
            }
            val api = mock<ConversationApi>()
            everySuspend { api.fetchGroupInfo(any()) } calls {
                val id = (it.args[0] as com.wire.kalium.network.api.model.QualifiedID).toModel()
                NetworkResponse.Success(id.toString().encodeToByteArray(), emptyMap(), 200)
            }
            mlsRepository = mock<MLSConversationRepository>()
            everySuspend { mlsRepository.hasEstablishedMLSGroup(any(), any()) } calls {
                Either.Right((it.args[1] as GroupID).value in nativeGroups)
            }
            everySuspend { mlsRepository.joinGroupByExternalCommit(any(), any(), any()) } calls {
                val group = (it.args[1] as GroupID).value
                val id = when (group) { ORIGINAL_GROUP -> ORIGINAL; REPLACEMENT_GROUP -> REPLACEMENT; else -> error("Unexpected group") }
                assertContentEquals(id.toString().encodeToByteArray(), it.args[2] as ByteArray)
                joined += id
                nativeGroups += group
                database.conversationDAO.updateConversationGroupState(ConversationEntity.GroupState.ESTABLISHED, group)
                Either.Right(Unit)
            }
            joinHelper = JoinExistingMLSConversationUseCaseImpl(feature, api, clients, conversations, mlsRepository,
                fetchCanonical, fetchExact, mock<ResetMLSConversationUseCase>(), SELF)
        }

        suspend fun drain() =
            RecoverPendingMLSGroupJoinsUseCaseImpl(pending, sync, cryptoTransactionProvider, joinHelper, conversations, SELF)()
    }

    private companion object {
        val SELF = QualifiedID("self", "example.test")
        val PEER = QualifiedID("peer", "example.test")
        val ORIGINAL = ConversationId("0-original-one-to-one", "example.test")
        val REPLACEMENT = ConversationId("5-canonical-replacement", "example.test")
        const val ORIGINAL_TEAM = "original-team"
        const val ORIGINAL_GROUP = "original-group"
        const val REPLACEMENT_GROUP = "replacement-group"
    }
}

private typealias GroupStateEntity = ConversationEntity.GroupState
