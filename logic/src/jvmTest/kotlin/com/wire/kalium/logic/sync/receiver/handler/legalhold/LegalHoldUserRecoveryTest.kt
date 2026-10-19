/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.Conversation
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.event.Event
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.data.sync.SyncState
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.legalhold.LegalHoldState
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.persistence.dao.client.ClientTypeEntity
import com.wire.kalium.persistence.dao.client.DeviceTypeEntity
import com.wire.kalium.persistence.dao.client.InsertClientParam
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.every
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.runBlocking
import java.io.File
import java.nio.file.Files
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class LegalHoldUserRecoveryTest {
    @Test
    fun interruptionAfterClientRefreshRetainsUserHistoryOnRetry() = runBlocking {
        exercise(PEER, true, "after-refresh", false, initialStatusMatches = true)
    }

    @Test
    fun selfAndPeerEnableDisableRecoverOriginalHistoryPlan() = runBlocking {
        for (subject in listOf(OWNER, PEER)) {
            for (enabled in listOf(false, true)) {
                for (stage in listOf("before-refresh", "after-refresh", "after-first-notice")) {
                    exercise(subject, enabled, stage, false, recoverAtStartup = true)
                }
                exercise(subject, enabled, "after-first-notice", true, recoverAtStartup = true)
            }
        }
    }

    private suspend fun exercise(subject: QualifiedID, enabled: Boolean, stage: String, profile: Boolean,
        recoverAtStartup: Boolean = false, initialStatusMatches: Boolean = false) {
        val directory = Files.createTempDirectory("legalhold-user-").toFile()
        fun open() = userDatabaseBuilder(PlatformDatabaseData(StorageData.FileBacked(File(directory, "account.db"))),
            OWNER.toDao(), null, Dispatchers.IO)
        suspend fun LegalHoldHandler.deliver() {
            if (profile) handleUserFetch(subject, enabled)
            else if (enabled) handleEnable(Event.User.LegalHoldEnabled("enable", subject))
            else handleDisable(Event.User.LegalHoldDisabled("disable", subject))
        }
        try {
            var db = open()
            try {
                for (id in listOf(OWNER, PEER)) db.userDAO.upsertUser(TestUser.ENTITY.copy(id = id.toDao(), team = null,
                    previewAssetId = null, completeAssetId = null))
                db.clientDAO.insertClientsAndRemoveRedundant(clients(subject, !enabled))
                for (id in CONVERSATIONS) {
                    db.conversationDAO.insertConversation(TestConversation.ENTITY.copy(id = id.toDao(), teamId = null,
                        protocolInfo = ConversationEntity.ProtocolInfo.Proteus))
                    val initialStatus = if (enabled == initialStatusMatches) ConversationEntity.LegalHoldStatus.ENABLED
                        else ConversationEntity.LegalHoldStatus.DISABLED
                    db.conversationDAO.updateLegalHoldStatus(id.toDao(), initialStatus)
                    assertEquals(initialStatus, db.conversationDAO.observeLegalHoldStatus(id.toDao()).first())
                }
                assertFailsWith<Interrupted> { handler(db, subject, enabled) { if (it == stage) throw Interrupted() }.deliver() }
                assertEquals(1, LegalHoldUserTransitionJournal(db.metadataDAO).pending().size)
            } finally { db.close() }
            db = open()
            try {
                if (stage != "before-refresh") assertEquals(enabled,
                    db.clientDAO.getClientsOfUserByQualifiedID(subject.toDao()).any { it.deviceType == DeviceTypeEntity.LegalHold })
                val fresh = handler(db, subject, if (stage == "after-first-notice") !enabled else enabled) {
                    if (stage == "after-first-notice" && it == "before-refresh") error("Recovery must reuse its persisted plan")
                }
                if (recoverAtStartup) fresh.recoverPendingTransitions()
                val replay = if (stage == "after-first-notice") handler(db, subject, enabled) else fresh
                replay.deliver()
                val first = CONVERSATIONS.associateWith { id -> db.messageDAO.getMessagesByConversationAndVisibility(id.toDao(), 10, 0).first() }
                assertEquals(List(2) { if (initialStatusMatches) 1 else 2 }, first.values.map { it.size })
                for (id in CONVERSATIONS) assertEquals(
                    if (enabled) ConversationEntity.LegalHoldStatus.ENABLED else ConversationEntity.LegalHoldStatus.DISABLED,
                    db.conversationDAO.observeLegalHoldStatus(id.toDao()).first())
                replay.recoverPendingTransitions()
                replay.deliver()
                for (id in CONVERSATIONS) assertEquals(first.getValue(id),
                    db.messageDAO.getMessagesByConversationAndVisibility(id.toDao(), 10, 0).first())
                assertEquals(emptyList(), LegalHoldUserTransitionJournal(db.metadataDAO).pending())
            } finally { db.close() }
        } finally { directory.deleteRecursively() }
    }

    private suspend fun handler(db: UserDatabaseBuilder, subject: QualifiedID, enabled: Boolean,
        checkpoint: suspend (String) -> Unit = {}): LegalHoldHandler {
        val conversations = mock<ConversationRepository>()
        val status = if (enabled) Conversation.LegalHoldStatus.ENABLED else Conversation.LegalHoldStatus.DISABLED
        everySuspend { conversations.getConversationsByUserId(any()) } returns Either.Right(CONVERSATIONS.map {
            TestConversation.CONVERSATION.copy(id = it, legalHoldStatus = status)
        })
        every { conversations.observeLegalHoldStatus(any()) } calls {
            db.conversationDAO.observeLegalHoldStatus((it.args[0] as QualifiedID).toDao()).map {
                Either.Right(Conversation.LegalHoldStatus.valueOf(it.name))
            }
        }
        everySuspend { conversations.updateLegalHoldStatus(any(), any()) } calls {
            Either.Right(db.conversationDAO.updateLegalHoldStatus((it.args[0] as QualifiedID).toDao(),
                ConversationEntity.LegalHoldStatus.valueOf((it.args[1] as Conversation.LegalHoldStatus).name)))
        }
        val members = mock<MembersHavingLegalHoldClientUseCase>()
        everySuspend { members.invoke(any()) } returns Either.Right(if (enabled) listOf(subject) else emptyList())
        var noticesWritten = 0
        val messages = mock<MessageRepository>()
        everySuspend { messages.getLastMessagesForConversationIds(any()) } returns Either.Right(emptyMap())
        val notices = LegalHoldSystemMessagesHandlerImpl(OWNER, PersistMessageUseCase { message ->
            db.messageDAO.insertOrIgnoreMessages(listOf((message as Message.System).toEntity()))
            if (++noticesWritten == 1) checkpoint("after-first-notice")
            Either.Right(Unit)
        }, conversations, messages)
        val clients = object : com.wire.kalium.logic.data.client.LegalHoldClients {
            override suspend fun isEnabled(userId: QualifiedID) = db.clientDAO.getClientsOfUserByQualifiedID(userId.toDao()).any {
                it.deviceType == DeviceTypeEntity.LegalHold
            }
            override suspend fun refresh(userId: QualifiedID) {
                checkpoint("before-refresh")
                db.clientDAO.insertClientsAndRemoveRedundant(clients(userId, enabled))
                checkpoint("after-refresh")
            }
        }
        everySuspend { conversations.getConversationById(any()) } returns Either.Right(TestConversation.CONVERSATION)
        val config = mock<UserConfigRepository>()
        everySuspend { config.deleteLegalHoldRequest() } returns Either.Right(Unit)
        everySuspend { config.setLegalHoldChangeNotified(any()) } returns Either.Right(Unit)
        return LegalHoldHandlerImpl(OWNER, mock<FetchUsersClientsFromRemoteUseCase>(), mock<ObserveLegalHoldStateForUserUseCase>(),
            members, config, conversations, notices, LegalHoldTransitionJournal(db.metadataDAO),
            LegalHoldUserTransitionJournal(db.metadataDAO), clients,
            object : ObserveSyncStateUseCase { override fun invoke() = flowOf(SyncState.GatheringPendingEvents) })
    }

    private fun clients(user: QualifiedID, enabled: Boolean) = listOf(InsertClientParam(user.toDao(), "phone",
        DeviceTypeEntity.Phone, ClientTypeEntity.Permanent, null, null, null, null, null, false, false)) +
        if (enabled) listOf(InsertClientParam(user.toDao(), "hold", DeviceTypeEntity.LegalHold,
            ClientTypeEntity.LegalHold, null, null, null, null, null, false, false)) else emptyList()

    private class Interrupted : Exception()
    companion object {
        private val OWNER = QualifiedID("owner", "test")
        private val PEER = QualifiedID("peer", "test")
        private val CONVERSATIONS = listOf(QualifiedID("conversation", "test"), QualifiedID("second", "test"))
    }
}
