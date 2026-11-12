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
import com.wire.kalium.logic.data.conversation.MLSJoinMode
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
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertIs
import kotlin.test.assertTrue
import kotlin.time.Instant

/**
 * Synthetic reproduction of build 113's persisted-action/canonical-ID redirect.
 * The original is a team-backed fake 1:1; the canonical replacement is teamless.
 * Uses a file-backed SQL database, the real action repository, drain and join helper.
 * Only network and native join effects are deterministic boundaries; neither real account
 * identifiers nor exported device data belong in this fixture.
 */
class PersistedMLSCanonicalRedirectRegressionTest {
    @Test
    fun legacyPersistedJoinCanJoinReplacementWithoutAcknowledgingOriginal() = runBlocking {
        withFixture { f ->
            f.persistLegacyJoin()
            f.exactFetchFails = true
            f.restart()

            f.drain()

            assertEquals(listOf(REPLACEMENT), f.joined)
            assertEquals(1, f.canonicalLookups)
            assertEquals(listOf(ORIGINAL), f.exactFetches)
            f.assertOriginalUnrecovered()
            assertEquals(listOf(ORIGINAL), f.actions(PendingActionType.JOIN_MLS_GROUP_CONVERSATION))
            assertEquals(listOf(ORIGINAL), f.actions(PendingActionType.RECOVER_MLS_GROUP_CONVERSATION))
            f.restart()
            assertEquals(listOf(ORIGINAL), f.actions(PendingActionType.JOIN_MLS_GROUP_CONVERSATION))
        }
    }

    @Test
    fun persistedExactRecoveryJoinsOriginalAndNeverConsultsCanonicalLookup() = runBlocking {
        withFixture { f ->
            f.pending.requestMLSGroupJoin(ORIGINAL)
            val canonicalBefore = f.row(REPLACEMENT)
            f.restart()

            f.drain()

            assertEquals(listOf(ORIGINAL), f.joined)
            assertEquals(listOf(ORIGINAL), f.exactFetches)
            assertEquals(0, f.canonicalLookups)
            f.assertOriginalRecovered()
            assertEquals(canonicalBefore, f.row(REPLACEMENT))
            assertFalse(f.database.pendingActionDAO.hasMLSGroupRecovery(ORIGINAL.toDao()))
            assertTrue(f.actions(PendingActionType.RECOVER_MLS_GROUP_CONVERSATION).isEmpty())
        }
    }

    @Test
    fun restartedForegroundDrainsRetainLegacyActionUntilOriginalReallyRecovers() = runBlocking {
        withFixture { f ->
            f.persistLegacyJoin()
            f.exactFetchFails = true
            f.restart()
            f.drain()
            f.assertOriginalUnrecovered()
            assertEquals(listOf(ORIGINAL), f.actions(PendingActionType.JOIN_MLS_GROUP_CONVERSATION))

            f.exactFetchFails = false
            f.restart()
            f.drain()
            f.assertOriginalRecovered()
            // If the legacy attempt preceded exact recovery, the next foreground acknowledges
            // it. If exact recovery came first, this drain can already acknowledge it safely.
            f.restart()
            f.drain()
            assertTrue(f.actions(PendingActionType.JOIN_MLS_GROUP_CONVERSATION).isEmpty())
            assertTrue(f.actions(PendingActionType.RECOVER_MLS_GROUP_CONVERSATION).isEmpty())
            f.assertOriginalRecovered()
        }
    }

    @Test
    fun falselySuccessfulRedirectedExactJoinRetainsDurableRecoveryUntilNewEvidence() = runBlocking {
        withFixture { f ->
            f.pending.requestMLSGroupJoin(ORIGINAL)
            f.redirectExactJoin = true
            f.restart()
            f.drain()

            assertEquals(listOf(REPLACEMENT), f.joined)
            f.assertOriginalUnrecovered()
            assertTrue(f.database.pendingActionDAO.hasMLSGroupRecovery(ORIGINAL.toDao()))
            assertEquals(listOf(ORIGINAL), f.actions(PendingActionType.PAUSE_MLS_GROUP_JOIN))
            f.restart()
            f.drain()
            assertEquals(listOf(REPLACEMENT), f.joined)
            f.assertOriginalUnrecovered()
            assertTrue(f.database.pendingActionDAO.hasMLSGroupRecovery(ORIGINAL.toDao()))

            f.redirectExactJoin = false
            f.pending.requestMLSGroupJoin(ORIGINAL) // A new message/welcome resumes this retained request.
            f.drain()
            assertEquals(listOf(REPLACEMENT, ORIGINAL), f.joined)
            f.assertOriginalRecovered()
            assertFalse(f.database.pendingActionDAO.hasMLSGroupRecovery(ORIGINAL.toDao()))
        }
    }

    @Test
    fun verifiedSelfRemovalIsTerminalButReplacementExistenceAloneIsNot() = runBlocking {
        withFixture { f ->
            f.pending.requestMLSGroupJoin(ORIGINAL)
            f.database.memberDAO.deleteMemberByQualifiedID(SELF.toDao(), ORIGINAL.toDao())
            f.restart()
            f.drain()

            assertTrue(f.joined.isEmpty())
            assertFalse(f.database.pendingActionDAO.hasMLSGroupRecovery(ORIGINAL.toDao()))
            assertEquals(f.row(ORIGINAL), f.database.conversationDAO.getNonDeletedConversationById(ORIGINAL.toDao()))
            assertFalse(ORIGINAL_GROUP in f.nativeGroups)
            f.restart()
            f.drain()
            assertTrue(f.joined.isEmpty())
            assertTrue(f.actions(PendingActionType.RECOVER_MLS_GROUP_CONVERSATION).isEmpty())
        }
    }

    @Test
    fun canonicalOneOnOneResolutionStillSelectsAndJoinsReplacement() = runBlocking {
        withFixture { f ->
            f.persistLegacyJoin()
            f.nativeGroups.remove(REPLACEMENT_GROUP)
            val resolver = MLSOneOnOneConversationResolverImpl(f.conversations, f.joinHelper, f.fetchCanonical, f.mlsRepository)

            assertEquals(REPLACEMENT, assertIs<Either.Right<ConversationId>>(resolver(f.transactionContext, PEER)).value)

            assertEquals(1, f.canonicalLookups)
            assertEquals(listOf(REPLACEMENT), f.joined)
            f.assertOriginalUnrecovered()
            assertEquals(listOf(ORIGINAL), f.actions(PendingActionType.JOIN_MLS_GROUP_CONVERSATION))
        }
    }

    @Test
    fun teamBackedOriginalRemainsIneligibleForCanonicalResolutionAfterExactRecovery() = runBlocking {
        withFixture { f ->
            f.pending.requestMLSGroupJoin(ORIGINAL)
            f.drain()
            f.assertOriginalRecovered()
            assertTrue(REPLACEMENT_GROUP in f.nativeGroups)
            assertEquals(ORIGINAL_TEAM, f.row(ORIGINAL).teamId)
            assertNull(f.row(REPLACEMENT).teamId)
            assertEquals(REPLACEMENT.toDao(),
                f.database.userDAO.getUserDetailsByQualifiedID(PEER.toDao())?.activeOneOnOneConversationId)
            val resolver = MLSOneOnOneConversationResolverImpl(f.conversations, f.joinHelper, f.fetchCanonical, f.mlsRepository)

            assertEquals(REPLACEMENT, assertIs<Either.Right<ConversationId>>(resolver(f.transactionContext, PEER)).value,
                "Exact recovery must preserve the existing teamless-only canonical eligibility rule")
            assertEquals(listOf(REPLACEMENT_GROUP), f.establishedChecks,
                "The team-backed original must be excluded before checking canonical native candidates")
            assertEquals(0, f.canonicalLookups)
            assertEquals(listOf(ORIGINAL), f.joined)
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
        val exactFetches = mutableListOf<ConversationId>()
        val establishedChecks = mutableListOf<String>()
        var canonicalLookups = 0
        var exactFetchFails = false
        var redirectExactJoin = false
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

        suspend fun persistLegacyJoin() = database.pendingActionDAO.upsert(
            PendingActionType.JOIN_MLS_GROUP_CONVERSATION, ORIGINAL.toDao(), 1L
        )

        suspend fun restart() {
            database.close()
            database = open()
            wire() // Fresh repository/helper/drain; the action exists only in the reopened SQL database.
        }

        suspend fun row(id: ConversationId) = checkNotNull(database.conversationDAO.getConversationById(id.toDao()))
        private suspend fun model(id: ConversationId) = MapperProvider.conversationMapper(SELF).fromDaoModel(row(id))
        suspend fun actions(type: PendingActionType) = database.pendingActionDAO.getByActionType(type).map { it.qualifiedId.toModel() }

        suspend fun assertOriginalUnrecovered() {
            assertEquals(ORIGINAL_TEAM, row(ORIGINAL).teamId)
            assertNull(row(REPLACEMENT).teamId)
            assertEquals(ConversationEntity.GroupState.PENDING_WELCOME_MESSAGE,
                (row(ORIGINAL).protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupState)
            assertFalse(ORIGINAL_GROUP in nativeGroups)
            assertEquals(row(ORIGINAL), database.conversationDAO.getNonDeletedConversationById(ORIGINAL.toDao()))
            assertEquals(REPLACEMENT.toDao(), database.userDAO.getUserDetailsByQualifiedID(PEER.toDao())?.activeOneOnOneConversationId)
        }

        suspend fun assertOriginalRecovered() {
            assertEquals(ORIGINAL_TEAM, row(ORIGINAL).teamId)
            assertNull(row(REPLACEMENT).teamId)
            assertEquals(ConversationEntity.GroupState.ESTABLISHED,
                (row(ORIGINAL).protocolInfo as ConversationEntity.ProtocolInfo.MLS).groupState)
            assertTrue(ORIGINAL_GROUP in nativeGroups)
            assertEquals(row(ORIGINAL), database.conversationDAO.getNonDeletedConversationById(ORIGINAL.toDao()))
        }

        suspend fun wire() {
            withTransactionReturning(Either.Right(Unit))
            everySuspend { mlsContext.conversationExists(any()) } calls { it.args[0] as String in nativeGroups }
            conversations = mock<ConversationRepository>(MockMode.autoUnit)
            everySuspend { conversations.getNonDeletedConversationById(any()) } calls {
                val row = database.conversationDAO.getNonDeletedConversationById((it.args[0] as ConversationId).toDao())
                if (row == null) Either.Left(StorageFailure.DataNotFound)
                else Either.Right(MapperProvider.conversationMapper(SELF).fromDaoModel(row))
            }
            everySuspend { conversations.setConversationDeletedLocally(any(), any()) } calls {
                database.conversationDAO.setConversationDeletedLocally((it.args[0] as ConversationId).toDao(), it.args[1] as Boolean)
                Either.Right(Unit)
            }
            everySuspend { conversations.getConversationMembers(any()) } calls {
                Either.Right(database.memberDAO.getConversationMembers((it.args[0] as ConversationId).toDao()).map { user -> user.toModel() })
            }
            everySuspend { conversations.getConversationsByGroupState(any()) } calls { call ->
                val state = call.args[0] as Conversation.ProtocolInfo.MLSCapable.GroupState
                Either.Right(database.conversationDAO.getConversationsByGroupState(ConversationEntity.GroupState.valueOf(state.name)).map {
                    MapperProvider.conversationMapper(SELF).fromDaoModel(it)
                })
            }
            everySuspend { conversations.getConversationsByUserId(PEER) } calls {
                Either.Right(database.conversationDAO.getConversationsByUserId(PEER.toDao()).map {
                    MapperProvider.conversationMapper(SELF).fromDaoModel(it)
                })
            }
            val feature = mock<FeatureSupport>()
            every { feature.isMLSSupported } returns true
            val clients = mock<ClientRepository>()
            everySuspend { clients.hasRegisteredMLSClient() } returns Either.Right(true)
            sync = mock<SyncStateObserver>()
            everySuspend { sync.waitUntilLiveOrFailure() } returns Either.Right(Unit)
            fetchCanonical = mock<FetchMLSOneToOneConversationUseCase>()
            everySuspend { fetchCanonical(any(), PEER) } calls {
                canonicalLookups++
                Either.Right(model(REPLACEMENT))
            }
            val fetchExact = mock<FetchConversationUseCase>()
            everySuspend { fetchExact(any(), any(), any()) } calls {
                val id = it.args[1] as ConversationId
                exactFetches += id
                assertEquals(ORIGINAL, id)
                if (exactFetchFails) Either.Left(NetworkFailure.NoNetworkConnection(null)) else Either.Right(Unit)
            }
            val api = mock<ConversationApi>()
            everySuspend { api.fetchGroupInfo(any()) } calls {
                val id = (it.args[0] as com.wire.kalium.network.api.model.QualifiedID).toModel()
                NetworkResponse.Success(id.toString().encodeToByteArray(), emptyMap(), 200)
            }
            mlsRepository = mock<MLSConversationRepository>()
            everySuspend { mlsRepository.hasEstablishedMLSGroup(any(), any()) } calls {
                val group = (it.args[1] as GroupID).value
                establishedChecks += group
                Either.Right(group in nativeGroups)
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

        suspend fun drain() {
            val helper = if (!redirectExactJoin) joinHelper else mock<JoinExistingMLSConversationUseCase>().also { broken ->
                everySuspend { broken(any(), any(), any(), any(), any()) } calls {
                    // Deliberate old-helper regression: report success after canonicalizing a recovery ID.
                    joinHelper(transactionContext, it.args[1] as ConversationId, mode = MLSJoinMode.JOIN_OR_ESTABLISH)
                }
            }
            RecoverPendingMLSGroupJoinsUseCaseImpl(pending, sync, cryptoTransactionProvider, helper, conversations, SELF)()
        }
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
