/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.sync.receiver.handler.legalhold

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.conversation.ConversationRepository
import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.data.message.toEntity
import com.wire.kalium.logic.feature.client.FetchSelfClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.client.FetchUsersClientsFromRemoteUseCase
import com.wire.kalium.logic.feature.legalhold.MembersHavingLegalHoldClientUseCase
import com.wire.kalium.logic.feature.legalhold.ObserveLegalHoldStateForUserUseCase
import com.wire.kalium.logic.framework.TestConversation
import com.wire.kalium.logic.framework.TestUser
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.db.PlatformDatabaseData
import com.wire.kalium.persistence.db.StorageData
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.db.userDatabaseBuilder
import dev.mokkery.answering.calls
import dev.mokkery.answering.returns
import dev.mokkery.everySuspend
import dev.mokkery.matcher.any
import dev.mokkery.mock
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.map
import com.wire.kalium.logic.sync.ObserveSyncStateUseCase
import com.wire.kalium.logic.data.sync.SyncState
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import java.io.File
import java.net.URLClassLoader
import java.nio.file.Files
import java.util.concurrent.TimeUnit
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

private val OWNER = QualifiedID("synthetic-legalhold-owner", "test")
private val CONVERSATION = QualifiedID("synthetic-conversation", "test")

class LegalHoldTransitionProcessTest {
    @Test
    fun startupRecoveryDiscardsHistoryForDeletedConversation() = runBlocking {
        val directory = Files.createTempDirectory("legalhold-deleted-").toFile()
        val db = legalHoldProcessDatabase(directory)
        try {
            val journal = LegalHoldTransitionJournal(db.metadataDAO)
            val key = LegalHoldTransitionJournal.conversationKey(CONVERSATION)
            journal.begin(key) {
                LegalHoldTransitionJournal.Transition(key, CONVERSATION, true, kotlin.time.Clock.System.now())
            }
            legalHoldProcessHandler(db).recoverPendingTransitions()
            assertTrue(journal.pending().isEmpty())
        } finally {
            db.close()
            directory.deleteRecursively()
        }
    }

    @Test
    fun killedHandlerRecoversStatusAndExactlyOneNoticeBeforeAcknowledgingIntent() = runBlocking {
        for (stage in listOf("before-notice", "after-notice")) {
            val directory = Files.createTempDirectory("legalhold-transition-kill-").toFile()
            try {
                val classpath = generateSequence(javaClass.classLoader) { it.parent }
                    .filterIsInstance<URLClassLoader>().flatMap { it.urLs.asSequence() }
                    .map { File(it.toURI()).path }.toList() + System.getProperty("java.class.path").split(File.pathSeparator)
                val arguments = File(directory, "java.args")
                arguments.writeText("-cp\n\"${classpath.distinct().joinToString(File.pathSeparator)}\"\n" +
                    "${LegalHoldTransitionProcessProbe::class.java.name}\n\"${directory.path}\"\n$stage\n")
                val process = ProcessBuilder(File(System.getProperty("java.home"), "bin/java").path, "@${arguments.path}")
                    .redirectErrorStream(true).redirectOutput(File(directory, "child.log")).start()
                try {
                    withTimeout(30_000) {
                        while (!File(directory, "ready").exists()) {
                            check(process.isAlive) { File(directory, "child.log").readText() }
                            delay(20)
                        }
                    }
                    process.destroyForcibly()
                    assertTrue(process.waitFor(10, TimeUnit.SECONDS))
                    val db = legalHoldProcessDatabase(directory)
                    try {
                        assertEquals(ConversationEntity.LegalHoldStatus.ENABLED,
                            db.conversationDAO.observeLegalHoldStatus(CONVERSATION.toDao()).first())
                        val before = db.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION.toDao(), 10, 0).first()
                        assertEquals(if (stage == "after-notice") 1 else 0, before.size)
                        legalHoldProcessHandler(db).handleConversationMembersChanged(CONVERSATION)
                        assertTrue(LegalHoldTransitionJournal(db.metadataDAO).pending().isEmpty())
                        val notices = db.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION.toDao(), 10, 0).first()
                        assertEquals(1, notices.size, "Retry after $stage must preserve exactly one legal hold notice")
                        legalHoldProcessHandler(db).handleConversationMembersChanged(CONVERSATION)
                        assertEquals(1, db.messageDAO.getMessagesByConversationAndVisibility(CONVERSATION.toDao(), 10, 0).first().size)
                    } finally { db.close() }
                } finally { if (process.isAlive) process.destroyForcibly().waitFor() }
            } finally { directory.deleteRecursively() }
        }
    }
}

private fun legalHoldProcessDatabase(directory: File) = userDatabaseBuilder(
    PlatformDatabaseData(StorageData.FileBacked(File(directory, "account.db"))), OWNER.toDao(), null, Dispatchers.IO,
)

private suspend fun legalHoldProcessHandler(db: UserDatabaseBuilder, barrier: suspend (String) -> Unit = {}): LegalHoldHandler {
    val conversations = mock<ConversationRepository>()
    everySuspend { conversations.getConversationById(any()) } calls {
        val entity = db.conversationDAO.getConversationById((it.args[0] as QualifiedID).toDao())
        if (entity == null) Either.Left(StorageFailure.DataNotFound)
        else Either.Right(TestConversation.CONVERSATION.copy(id = CONVERSATION))
    }
    everySuspend { conversations.updateLegalHoldStatus(any(), any()) } calls {
        val id = it.args[0] as QualifiedID
        val status = it.args[1] as com.wire.kalium.logic.data.conversation.Conversation.LegalHoldStatus
        Either.Right(db.conversationDAO.updateLegalHoldStatus(id.toDao(), ConversationEntity.LegalHoldStatus.valueOf(status.name)))
    }
    everySuspend { conversations.observeLegalHoldStatus(any()) } returns db.conversationDAO.observeLegalHoldStatus(CONVERSATION.toDao()).map {
        Either.Right(com.wire.kalium.logic.data.conversation.Conversation.LegalHoldStatus.valueOf(it.name))
    }
    everySuspend { conversations.observeIsUserMember(any(), any()) } returns flowOf(Either.Right(true))
    val members = mock<MembersHavingLegalHoldClientUseCase>()
    everySuspend { members.invoke(any()) } returns Either.Right(listOf(OWNER))
    val notices = LegalHoldSystemMessagesHandlerImpl(OWNER, PersistMessageUseCase { message ->
        barrier("before-notice")
        db.messageDAO.insertOrIgnoreMessages(listOf((message as Message.System).toEntity()))
        barrier("after-notice")
        Either.Right(Unit)
    }, conversations, mock<MessageRepository>())
    return LegalHoldHandlerImpl(OWNER, mock<FetchUsersClientsFromRemoteUseCase>(),
        mock<ObserveLegalHoldStateForUserUseCase>(), members, mock<UserConfigRepository>(), conversations, notices,
        LegalHoldTransitionJournal(db.metadataDAO), LegalHoldUserTransitionJournal(db.metadataDAO),
        mock<com.wire.kalium.logic.data.client.LegalHoldClients>(),
        object : ObserveSyncStateUseCase {
            override fun invoke() = flowOf(SyncState.GatheringPendingEvents)
        })
}

object LegalHoldTransitionProcessProbe {
    @JvmStatic
    fun main(args: Array<String>): Unit = runBlocking {
        val directory = File(args[0])
        val db = legalHoldProcessDatabase(directory)
        db.userDAO.upsertUser(TestUser.ENTITY.copy(id = OWNER.toDao(), team = null, previewAssetId = null, completeAssetId = null))
        db.conversationDAO.insertConversation(TestConversation.ENTITY.copy(id = CONVERSATION.toDao(), teamId = null,
            protocolInfo = ConversationEntity.ProtocolInfo.Proteus, legalHoldStatus = ConversationEntity.LegalHoldStatus.DISABLED))
        legalHoldProcessHandler(db) { checkpoint ->
            if (checkpoint == args[1]) {
                File(directory, "ready").writeText("ready")
                // Parent kills the actual handler with its SQLite connection and intent still open.
                awaitCancellation()
            }
        }.handleConversationMembersChanged(CONVERSATION)
    }
}
