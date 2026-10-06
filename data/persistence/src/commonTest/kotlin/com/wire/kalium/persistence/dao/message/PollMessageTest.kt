/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
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
package com.wire.kalium.persistence.dao.message

import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.UserDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDAO
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.time.Instant
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class PollMessageTest : BaseDatabaseTest() {

    private lateinit var messageDAO: MessageDAO
    private lateinit var conversationDAO: ConversationDAO
    private lateinit var userDAO: UserDAO
    private lateinit var pollMessageDAO: PollMessageDAO

    private val conversation = newConversationEntity("poll-conversation")
    private val author = newUserEntity("author")
    private val voter = newUserEntity("voter")
    private val selfUserId = UserIDEntity("selfValue", "selfDomain")

    @BeforeTest
    fun setUp() {
        deleteDatabase(selfUserId)
        val db = createDatabase(selfUserId, encryptedDBSecret, true)
        messageDAO = db.messageDAO
        conversationDAO = db.conversationDAO
        userDAO = db.userDAO
        pollMessageDAO = db.pollMessageDAO
    }

    @Test
    fun givenOptionTextThatNeedsJsonEscaping_whenReadingPoll_thenTextIsUnchanged() = runTest {
        val options = listOf(
            PollOptionEntity("a", """He said "yes" \o/"""),
            PollOptionEntity("b", "Line one\nline two"),
        )
        val poll = insertPoll(options = options)

        val content = messageDAO.getMessageById(poll.id, poll.conversationId)?.content as MessageEntityContent.Poll

        assertEquals("Lunch?", content.question)
        assertEquals(options, content.options)
        assertEquals(emptyList(), content.votes)
    }

    @Test
    fun givenVotes_whenOlderVoteArrivesLast_thenNewestVoteIsKept() = runTest {
        val poll = insertPoll()
        val newest = PollVoteEntity(voter.id, listOf("b"), Instant.fromEpochMilliseconds(2_000))
        val older = PollVoteEntity(voter.id, listOf("a"), Instant.fromEpochMilliseconds(1_000))

        pollMessageDAO.recordVote(poll.conversationId, poll.id, newest)
        pollMessageDAO.recordVote(poll.conversationId, poll.id, older)

        assertEquals(listOf(newest), pollVotes(poll))
    }

    @Test
    fun givenVoteForUnknownPoll_whenRecording_thenItIsRejected() = runTest {
        insertPoll()

        val isValid = pollMessageDAO.recordVote(conversation.id, "unknown", vote(listOf("a")))

        assertFalse(isValid)
    }

    @Test
    fun givenSingleAnswerPoll_whenRecordingTwoOptions_thenItIsRejected() = runTest {
        val poll = insertPoll()

        assertFalse(pollMessageDAO.recordVote(poll.conversationId, poll.id, vote(listOf("a", "b"))))
        assertNull(pollMessageDAO.getVote(poll.conversationId, poll.id, voter.id))
    }

    @Test
    fun givenUnknownOption_whenRecording_thenItIsRejected() = runTest {
        val poll = insertPoll(allowMultipleAnswers = true)

        assertFalse(pollMessageDAO.recordVote(poll.conversationId, poll.id, vote(listOf("a", "unknown"))))
    }

    @Test
    fun givenMultipleAnswerPoll_whenRecordingRepeatedOptions_thenDistinctOptionsAreStored() = runTest {
        val poll = insertPoll(allowMultipleAnswers = true)

        assertTrue(pollMessageDAO.recordVote(poll.conversationId, poll.id, vote(listOf("b", "a", "b"))))
        assertEquals(listOf("b", "a"), pollMessageDAO.getVote(poll.conversationId, poll.id, voter.id)?.selectedOptionIds)
    }

    @Test
    fun givenEmptySelection_whenRecording_thenTheRetractionIsStored() = runTest {
        val poll = insertPoll()

        assertTrue(pollMessageDAO.recordVote(poll.conversationId, poll.id, vote(emptyList())))
        assertEquals(emptyList(), pollMessageDAO.getVote(poll.conversationId, poll.id, voter.id)?.selectedOptionIds)
    }

    @Test
    fun givenFailedVote_whenReverting_thenPreviousVoteIsRestored() = runTest {
        val poll = insertPoll()
        val previous = PollVoteEntity(voter.id, listOf("a"), Instant.fromEpochMilliseconds(1_000))
        val failed = PollVoteEntity(voter.id, listOf("b"), Instant.fromEpochMilliseconds(2_000))
        pollMessageDAO.recordVote(poll.conversationId, poll.id, previous)
        pollMessageDAO.recordVote(poll.conversationId, poll.id, failed)

        pollMessageDAO.revertVote(poll.conversationId, poll.id, failed, previous)

        assertEquals(previous, pollMessageDAO.getVote(poll.conversationId, poll.id, voter.id))
    }

    @Test
    fun givenFailedFirstVote_whenReverting_thenVoteIsRemoved() = runTest {
        val poll = insertPoll()
        val failed = PollVoteEntity(voter.id, listOf("b"), Instant.fromEpochMilliseconds(2_000))
        pollMessageDAO.recordVote(poll.conversationId, poll.id, failed)

        pollMessageDAO.revertVote(poll.conversationId, poll.id, failed, previousVote = null)

        assertNull(pollMessageDAO.getVote(poll.conversationId, poll.id, voter.id))
    }

    @Test
    fun givenNewerVoteAfterFailedVote_whenReverting_thenNewerVoteIsKept() = runTest {
        val poll = insertPoll()
        val failed = PollVoteEntity(voter.id, listOf("a"), Instant.fromEpochMilliseconds(1_000))
        val newer = PollVoteEntity(voter.id, listOf("b"), Instant.fromEpochMilliseconds(2_000))
        pollMessageDAO.recordVote(poll.conversationId, poll.id, failed)
        pollMessageDAO.recordVote(poll.conversationId, poll.id, newer)

        pollMessageDAO.revertVote(poll.conversationId, poll.id, failed, previousVote = null)

        assertEquals(newer, pollMessageDAO.getVote(poll.conversationId, poll.id, voter.id))
    }

    @Test
    fun givenPollWithVotes_whenDeletedForEveryone_thenPollDataIsRemoved() = runTest {
        val poll = insertPoll()
        pollMessageDAO.recordVote(poll.conversationId, poll.id, PollVoteEntity(voter.id, listOf("a"), Instant.fromEpochMilliseconds(1)))

        messageDAO.markMessageAsDeleted(poll.id, poll.conversationId)

        assertNull(pollMessageDAO.getVote(poll.conversationId, poll.id, voter.id))
        assertFalse(pollMessageDAO.recordVote(poll.conversationId, poll.id, vote(listOf("a"))))
    }

    @Test
    fun givenPoll_whenItIsTheNewestMessage_thenItIsTheLastMessageWithItsQuestion() = runTest {
        val poll = insertPoll()

        val lastMessage = messageDAO.observeLastMessages().first().single { it.conversationId == poll.conversationId }

        assertEquals(MessagePreviewEntityContent.Poll(senderName = author.name, question = "Lunch?"), lastMessage.content)
    }

    private suspend fun insertPoll(
        options: List<PollOptionEntity> = listOf(PollOptionEntity("a", "Pizza"), PollOptionEntity("b", "Sushi")),
        allowMultipleAnswers: Boolean = false,
    ): MessageEntity.Regular {
        conversationDAO.insertConversation(conversation)
        userDAO.upsertUser(author)
        userDAO.upsertUser(voter)
        val poll = newRegularMessageEntity().copy(
            senderUserId = author.id,
            conversationId = conversation.id,
            content = MessageEntityContent.Poll(question = "Lunch?", options = options, allowMultipleAnswers = allowMultipleAnswers)
        )
        messageDAO.insertOrIgnoreMessages(listOf(poll))
        return poll
    }

    private fun vote(optionIds: List<String>) = PollVoteEntity(voter.id, optionIds, Instant.fromEpochMilliseconds(1_000))

    private suspend fun pollVotes(poll: MessageEntity.Regular): List<PollVoteEntity> =
        (messageDAO.getMessageById(poll.id, poll.conversationId)?.content as MessageEntityContent.Poll).votes
}
