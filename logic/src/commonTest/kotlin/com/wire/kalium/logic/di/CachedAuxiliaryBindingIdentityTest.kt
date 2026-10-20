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

package com.wire.kalium.logic.di

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.common.functional.right
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logger.KaliumLogger
import com.wire.kalium.logic.cache.SelfConversationIdProvider
import com.wire.kalium.logic.data.asset.AssetRepository
import com.wire.kalium.logic.data.client.ClientRepository
import com.wire.kalium.logic.data.client.CryptoTransactionProvider
import com.wire.kalium.logic.data.client.remote.ClientRemoteRepository
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.conversation.FetchConversationUseCase
import com.wire.kalium.logic.data.conversation.JoinExistingMLSConversationUseCase
import com.wire.kalium.logic.data.conversation.MLSConversationRepository
import com.wire.kalium.logic.data.conversation.ResetMLSConversationUseCase
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.id.CurrentClientIdProvider
import com.wire.kalium.logic.data.meeting.MeetingRepository
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.mls.MLSMissingUsersMessageRejectionHandler
import com.wire.kalium.logic.data.prekey.PreKeyRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.SelfUserObservationProvider
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.feature.channels.ChannelsScope
import com.wire.kalium.logic.feature.channels.LegacyChannelsBindings
import com.wire.kalium.logic.feature.channels.LegacyChannelsDependencies
import com.wire.kalium.logic.feature.conversation.channel.UpdateChannelAddPermissionUseCaseImpl
import com.wire.kalium.logic.feature.debug.DebugScope
import com.wire.kalium.logic.feature.debug.LegacyDebugBindings
import com.wire.kalium.logic.feature.debug.LegacyDebugDependencies
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesResult
import com.wire.kalium.logic.feature.keypackage.RefillKeyPackagesUseCase
import com.wire.kalium.logic.feature.meeting.LegacyMeetingBindings
import com.wire.kalium.logic.feature.meeting.LegacyMeetingDependencies
import com.wire.kalium.logic.feature.meeting.MaintainMeetingOccurrencesUseCase
import com.wire.kalium.logic.feature.meeting.MeetingScope
import com.wire.kalium.logic.feature.message.LegacyMessageBindings
import com.wire.kalium.logic.feature.message.LegacyMessageDependencies
import com.wire.kalium.logic.feature.message.MessageSendingScheduler
import com.wire.kalium.logic.feature.message.StaleEpochVerifier
import com.wire.kalium.logic.feature.search.FederatedSearchParser
import com.wire.kalium.logic.feature.search.LegacySearchBindings
import com.wire.kalium.logic.feature.search.LegacySearchDependencies
import com.wire.kalium.logic.feature.search.SearchScope
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.SyncManager
import com.wire.kalium.logic.sync.receiver.handler.legalhold.LegalHoldHandler
import com.wire.kalium.logic.util.arrangement.provider.CryptoTransactionProviderArrangementImpl
import com.wire.kalium.persistence.dao.MetadataDAO
import com.wire.kalium.util.DebugKaliumApi
import com.wire.kalium.util.InternalKaliumApi
import com.wire.kalium.util.KaliumDispatcherImpl
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import dev.zacsweers.metro.DependencyGraph
import dev.zacsweers.metro.Provides
import dev.zacsweers.metro.createGraphFactory
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame
import kotlin.time.Instant
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.runTest

/**
 * Generated test roots use the production binding interfaces, independently of the evolving root
 * factory. Unconfigured dependency getters throw: resolving one member must not read the old list.
 * External repository effects are mocked; these tests do not establish runtime startup or ABI parity.
 */
@OptIn(DebugKaliumApi::class, InternalKaliumApi::class)
class CachedAuxiliaryBindingIdentityTest {
    @Test
    fun searchParserRetainsItsOwnCacheWhileGettersAndUserRepositoryCapturesRemainIndependent() = runTest {
        var captures = 0
        var federationReads = 0
        val dependencies = object : LegacySearchDependencies by mock<LegacySearchDependencies>() {
            override val selfUserId = TestUser.USER_ID
            override val sessionRepository: SessionRepository
                get() = mock<SessionRepository>().also { repository ->
                    captures++
                    everySuspend { repository.isFederated(TestUser.USER_ID) } calls {
                        federationReads++
                        true.right()
                    }
                }
        }
        val first = graph(search = dependencies)
        val second = graph(search = dependencies)
        val facade = SearchScope(first)
        assertEquals(0, captures)
        val parser = facade.federatedSearchParser
        val anotherParser = facade.federatedSearchParser
        assertNotSame(parser, anotherParser)
        assertEquals(1, captures)
        val expected = FederatedSearchParser.Result("alice", "elsewhere.example")
        assertEquals(expected, parser("alice@elsewhere.example", true))
        assertEquals(expected, parser("alice@elsewhere.example", true))
        assertEquals(1, federationReads)
        assertEquals(expected, anotherParser("alice@elsewhere.example", true))
        assertEquals(2, federationReads)
        assertNotSame(first.searchSessionRepository, second.searchSessionRepository)
        assertEquals(2, captures)
    }

    @Test
    fun channelsCaptureRawFunctionOncePerUserWithoutCachingItsReturnedRepositories() {
        var captures = 0
        var invocations = 0
        val dependencies = object : LegacyChannelsDependencies by mock<LegacyChannelsDependencies>() {
            override val conversationRepository: () -> ConversationRepository
                get() {
                    captures++
                    return { mock<ConversationRepository>().also { invocations++ } }
                }
        }
        val first = graph(channels = dependencies)
        val second = graph(channels = dependencies)
        val facade = ChannelsScope(first)
        assertEquals(0, captures)
        val capturedFunction = facade.conversationRepository
        assertSame(capturedFunction, facade.conversationRepository)
        assertEquals(1, captures)
        assertEquals(0, invocations)
        val update = facade.updateChannelAddPermission as UpdateChannelAddPermissionUseCaseImpl
        val nextUpdate = facade.updateChannelAddPermission as UpdateChannelAddPermissionUseCaseImpl
        assertNotSame(update, nextUpdate)
        assertNotSame(update.conversationRepository, nextUpdate.conversationRepository)
        assertEquals(1, captures)
        assertEquals(2, invocations)
        assertNotSame(capturedFunction, ChannelsScope(second).conversationRepository)
        assertEquals(2, captures)
        assertEquals(2, invocations)
    }

    @Test
    fun channelConfigurationGettersKeepInvokingMetadataAndObservationPorts() {
        var metadataReads = 0
        var observationReads = 0
        val dependencies = object : LegacyChannelsDependencies by mock<LegacyChannelsDependencies>() {
            override val metadataDaoProvider: () -> MetadataDAO = {
                mock<MetadataDAO>().also { metadataReads++ }
            }
            override val selfUserObservationProvider: () -> SelfUserObservationProvider = {
                mock<SelfUserObservationProvider>().also { observationReads++ }
            }
        }
        val facade = ChannelsScope(graph(channels = dependencies))
        assertEquals(0, metadataReads)
        assertNotSame(facade.channelsConfigStorage, facade.channelsConfigStorage)
        assertNotSame(facade.channelsFeatureConfigHandler, facade.channelsFeatureConfigHandler)
        assertEquals(4, metadataReads)
        assertEquals(0, observationReads)
        assertNotSame(facade.observeChannelsCreationPermissionUseCase, facade.observeChannelsCreationPermissionUseCase)
        assertEquals(6, metadataReads)
        assertEquals(2, observationReads)
    }

    @Test
    fun meetingFactoryUseCasesShareOnlyTheirFacadeCaptureAndSeparateUsersGetFreshCaptures() = runTest {
        var captures = 0
        var reminderReads = 0
        var maintenanceCalls = 0
        val now = Instant.fromEpochSeconds(0)
        val dependencies = object : LegacyMeetingDependencies by mock<LegacyMeetingDependencies>() {
            override val dispatcher = KaliumDispatcherImpl
            override val meetingRepository: MeetingRepository
                get() = mock<MeetingRepository>().also { repository ->
                    captures++
                    everySuspend { repository.getNextMeetingReminder(now) } calls {
                        reminderReads++
                        null
                    }
                    everySuspend { repository.syncMeetingOccurrences(any(), any()) } calls {
                        maintenanceCalls++
                        Unit.right()
                    }
                }
        }
        val first = graph(meeting = dependencies)
        val second = graph(meeting = dependencies)
        val facade = MeetingScope(first)
        assertEquals(0, captures)
        val reminders = facade.meetingReminders
        val nextReminders = facade.meetingReminders
        assertNotSame(reminders, nextReminders)
        assertEquals(null, reminders.next(now))
        assertEquals(null, nextReminders.next(now))
        assertEquals(MaintainMeetingOccurrencesUseCase.Result.Success, facade.maintainMeetingOccurrences())
        assertEquals(2, reminderReads)
        assertEquals(1, maintenanceCalls)
        assertEquals(1, captures)
        assertSame(first.meetingMeetingRepository, first.meetingMeetingRepository)
        assertNotSame(first.meetingMeetingRepository, second.meetingMeetingRepository)
        assertEquals(2, captures)
    }

    @Test
    fun debugReportDoesNotResolveRepairCryptoOrMessageCaptures() {
        var dispatcherReads = 0
        val dependencies = object : LegacyDebugDependencies by mock<LegacyDebugDependencies>() {
            override val dispatcher
                get() = KaliumDispatcherImpl.also { dispatcherReads++ }
        }
        val facade = DebugScope(graph(debug = dependencies))
        assertEquals(0, dispatcherReads)
        assertNotSame(facade.getCryptoServiceReport, facade.getCryptoServiceReport)
        assertEquals(1, dispatcherReads)
    }

    @Test
    fun debugRepairIsRetainedWithAnIndependentConversationCapturePerUser() {
        var conversationCaptures = 0
        val dependencies = object : LegacyDebugDependencies by mock<LegacyDebugDependencies>() {
            override val conversationRepository: ConversationRepository
                get() = mock<ConversationRepository>().also { conversationCaptures++ }
            override val userId = TestUser.USER_ID
            override val resetMLSConversationUseCase = mock<ResetMLSConversationUseCase>()
            override val transactionProvider = mock<CryptoTransactionProvider>()
        }
        val first = graph(debug = dependencies)
        val second = graph(debug = dependencies)
        val facade = DebugScope(first)
        assertEquals(0, conversationCaptures)
        assertSame(facade.repairFaultyRemovalKeysUseCase, first.debugRepairFaultyRemovalKeysUseCase)
        assertSame(facade.repairFaultyRemovalKeysUseCase, facade.repairFaultyRemovalKeysUseCase)
        assertEquals(1, conversationCaptures)
        assertNotSame(first.debugRepairFaultyRemovalKeysUseCase, second.debugRepairFaultyRemovalKeysUseCase)
        assertEquals(2, conversationCaptures)
        assertNotSame(facade.getConversationCryptoStats, facade.getConversationCryptoStats)
        assertEquals(2, conversationCaptures)
    }

    @Test
    fun debugAndMessageEncodersAndRepositoryCapturesRemainDistinctInOneGeneratedGraph() {
        var repositoryCaptures = 0
        val debug = object : LegacyDebugDependencies by mock<LegacyDebugDependencies>() {
            override val messageRepository: MessageRepository
                get() = mock<MessageRepository>().also { repositoryCaptures++ }
        }
        val messages = object : LegacyMessageDependencies by mock<LegacyMessageDependencies>() {
            override val messageRepository: MessageRepository
                get() = mock<MessageRepository>().also { repositoryCaptures++ }
        }
        val factory = createGraphFactory<AuxiliaryDebugMessageTestGraph.Factory>()
        val first = factory.create(debug, messages)
        val second = factory.create(debug, messages)
        assertSame(first.debugMessageContentEncoder, first.debugMessageContentEncoder)
        assertSame(first.messageMessageContentEncoder, first.messageMessageContentEncoder)
        assertNotSame(first.debugMessageContentEncoder, first.messageMessageContentEncoder)
        assertNotSame(first.debugMessageContentEncoder, second.debugMessageContentEncoder)
        assertEquals(0, repositoryCaptures)
        assertSame(first.debugMessageRepository, first.debugMessageRepository)
        assertNotSame(first.debugMessageRepository, first.messageMessageRepository)
        assertEquals(2, repositoryCaptures)
    }

    @Test
    fun generateEventsChecksTheLiveClientProviderOnEveryCallWithoutInitializingMessageState() = runTest {
        var clientReads = 0
        val dependencies = object : LegacyDebugDependencies by mock<LegacyDebugDependencies>() {
            override val currentClientIdProvider = CurrentClientIdProvider {
                clientReads++
                Either.Left(CoreFailure.MissingClientRegistration)
            }
            override val userId = TestUser.USER_ID
            override val transactionProvider = mock<CryptoTransactionProvider>()
        }
        val facade = DebugScope(graph(debug = dependencies))
        repeat(2) {
            val failure = assertFailsWith<RuntimeException> {
                facade.generateEvents(
                    targetUserId = TestUser.USER_ID,
                    targetClientId = ClientId("target"),
                    conversationId = ConversationId("conversation", TestUser.USER_ID.domain),
                    limit = 1,
                )
            }
            assertEquals("No current client ID available", failure.message)
        }
        assertEquals(2, clientReads)
    }

    @Test
    fun debugSelfDeletionRetainsItsReceiverSenderWhileOtherSenderRequestsRemainFactories() = runTest {
        val ports = DebugSendingPorts(backgroundScope)
        val first = graph(debug = ports)
        val second = graph(debug = ports)
        assertNotSame(first.debugMessageSender, first.debugMessageSender)
        assertEquals(2, ports.rejectionHandlers)
        assertEquals(1, ports.rejectionPortCaptures)
        assertEquals(1, ports.messageCaptures)
        assertEquals(0, ports.loggerReads)
        val deletion = first.debugEphemeralMessageDeletionHandler
        assertSame(deletion, first.debugEphemeralMessageDeletionHandler)
        assertEquals(1, ports.loggerReads)
        // The retained receiver creates one more factory sender; the deferred reverse edge must not recurse.
        assertEquals(3, ports.rejectionHandlers)
        assertNotSame(deletion, second.debugEphemeralMessageDeletionHandler)
        assertEquals(2, ports.loggerReads)
        assertEquals(2, ports.messageCaptures)
        assertEquals(2, ports.rejectionPortCaptures)
        assertEquals(4, ports.rejectionHandlers)
        assertEquals(0, ports.joinRequests)
    }

    @Test
    fun refillKeyPackagesKeepsTheCapturedUseCaseButRunsTheOperationOnEveryCall() = runTest {
        val crypto = CryptoTransactionProviderArrangementImpl()
        crypto.withTransactionReturning(Unit.right())
        val refill = mock<RefillKeyPackagesUseCase>()
        val failure = CoreFailure.MissingClientRegistration
        everySuspend { refill(crypto.mlsContext) } returns RefillKeyPackagesResult.Success
        var captures = 0
        val dependencies = object : LegacyDebugDependencies by mock<LegacyDebugDependencies>() {
            override val transactionProvider = crypto.cryptoTransactionProvider
            override val refillKeyPackagesUseCase: RefillKeyPackagesUseCase
                get() = refill.also { captures++ }
        }
        val facade = DebugScope(graph(debug = dependencies))
        assertEquals(0, captures)
        assertEquals(Unit.right(), facade.refillKeyPackages())
        everySuspend { refill(crypto.mlsContext) } returns RefillKeyPackagesResult.Failure(failure)
        assertEquals(Either.Left(failure), facade.refillKeyPackages())
        assertEquals(1, captures)
    }

    private fun graph(
        search: LegacySearchDependencies = mock(),
        channels: LegacyChannelsDependencies = mock(),
        meeting: LegacyMeetingDependencies = mock(),
        debug: LegacyDebugDependencies = mock(),
    ): AuxiliaryCachedBindingTestGraph =
        createGraphFactory<AuxiliaryCachedBindingTestGraph.Factory>().create(search, channels, meeting, debug)
}

@DependencyGraph(UserLifetime::class)
internal interface AuxiliaryCachedBindingTestGraph :
    LegacySearchBindings, LegacyChannelsBindings, LegacyMeetingBindings, LegacyDebugBindings {
    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides search: LegacySearchDependencies,
            @Provides channels: LegacyChannelsDependencies,
            @Provides meeting: LegacyMeetingDependencies,
            @Provides debug: LegacyDebugDependencies,
        ): AuxiliaryCachedBindingTestGraph
    }
}

@DependencyGraph(UserLifetime::class)
internal interface AuxiliaryDebugMessageTestGraph : LegacyDebugBindings, LegacyMessageBindings {
    @DependencyGraph.Factory
    interface Factory {
        fun create(
            @Provides debug: LegacyDebugDependencies,
            @Provides messages: LegacyMessageDependencies,
        ): AuxiliaryDebugMessageTestGraph
    }
}

/** Only the sending-path inputs are available; report, event, feature and storage captures must stay unread. */
private class DebugSendingPorts(override val scope: CoroutineScope) :
    LegacyDebugDependencies by mock<LegacyDebugDependencies>() {
    var messageCaptures = 0
    var rejectionPortCaptures = 0
    var rejectionHandlers = 0
    var loggerReads = 0
    var joinRequests = 0

    override val messageRepository: MessageRepository
        get() = mock<MessageRepository>().also { messageCaptures++ }
    override val conversationRepository = mock<ConversationRepository>()
    override val mlsConversationRepository = mock<MLSConversationRepository>()
    override val clientRepository = mock<ClientRepository>()
    override val clientRemoteRepository = mock<ClientRemoteRepository>()
    override val currentClientIdProvider = mock<CurrentClientIdProvider>()
    override val preKeyRepository = mock<PreKeyRepository>()
    override val userRepository = mock<UserRepository>()
    override val userId = TestUser.USER_ID
    override val assetRepository = mock<AssetRepository>()
    override val syncManager = mock<SyncManager>()
    override val messageSendingScheduler = mock<MessageSendingScheduler>()
    override val selfConversationIdProvider = mock<SelfConversationIdProvider>()
    override val staleEpochVerifier = mock<StaleEpochVerifier>()
    override val legalHoldHandler = mock<LegalHoldHandler>()
    override val fetchConversationUseCase = mock<FetchConversationUseCase>()
    override val transactionProvider = mock<CryptoTransactionProvider>()
    override val joinExistingMLSConversationUseCaseProvider: () -> JoinExistingMLSConversationUseCase = {
        joinRequests++
        mock<JoinExistingMLSConversationUseCase>()
    }
    override val mlsMissingUsersMessageRejectionHandlerProvider: () -> MLSMissingUsersMessageRejectionHandler
        get() {
            rejectionPortCaptures++
            return { mock<MLSMissingUsersMessageRejectionHandler>().also { rejectionHandlers++ } }
        }
    override val logger: KaliumLogger
        get() = kaliumLogger.also { loggerReads++ }
}
