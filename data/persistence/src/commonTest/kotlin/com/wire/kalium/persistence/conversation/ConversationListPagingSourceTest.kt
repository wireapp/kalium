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

package com.wire.kalium.persistence.conversation

import androidx.paging.PagingConfig
import androidx.paging.PagingSource
import androidx.paging.PagingState
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationFilterEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListItemEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListPagingSource
import com.wire.kalium.persistence.dao.conversation.ConversationListPositionEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListQueryEntity
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderEntity
import com.wire.kalium.persistence.dao.conversation.folder.ConversationFolderTypeEntity
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.dao.unread.UnreadEventTypeEntity
import com.wire.kalium.persistence.db.ReadDispatcher
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newDraftMessageEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlin.test.AfterTest
import kotlin.test.BeforeTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlinx.coroutines.test.runTest
import kotlinx.datetime.Instant

class ConversationListPagingSourceTest : BaseDatabaseTest() {
    private val self = QualifiedIDEntity("self", "wire.com")
    private lateinit var db: UserDatabaseBuilder
    private val sources = mutableListOf<ConversationListPagingSource>()

    @BeforeTest
    fun setUp() {
        deleteDatabase(self)
        db = createDatabase(self, encryptedDBSecret, true)
    }

    @AfterTest
    fun tearDown() {
        sources.forEach { it.invalidate() }
        sources.clear()
        deleteDatabase(self)
    }

    @Test
    fun appendAndPrependVisitEqualTimestampRowsExactlyOnce() = runTest(dispatcher) {
        val ids = listOf(QualifiedIDEntity("same", "a"), QualifiedIDEntity("same", "b")) +
            (0..8).map { QualifiedIDEntity("z$it", "wire.com") }
        ids.reversed().forEach { db.conversationDAO.insertConversation(group(it).copy(lastModifiedDate = time(10))) }
        val source = source()
        var page = source.refresh(size = 3)
        val visited = page.data.toMutableList()
        assertNull(page.prevKey)
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, page.itemsBefore)
        assertEquals(PagingSource.LoadResult.Page.COUNT_UNDEFINED, page.itemsAfter)
        while (page.nextKey != null) {
            page = source.load(PagingSource.LoadParams.Append(requireNotNull(page.nextKey), 3, false)).page()
            visited += page.data
        }
        assertEquals(ids, visited.map { it.position.conversationId })
        val backwards = page.data.toMutableList()
        while (page.prevKey != null) {
            page = source.load(PagingSource.LoadParams.Prepend(requireNotNull(page.prevKey), 3, false)).page()
            backwards.addAll(0, page.data)
        }
        assertEquals(ids, backwards.map { it.position.conversationId })
    }

    @Test
    fun centeredRefreshIncludesBothSidesAndKeepsOldPositionWhenAnchorMoves() = runTest(dispatcher) {
        seedGroups()
        val oldPosition = ConversationListPositionEntity(time(5), id(5))
        db.conversationDAO.updateConversationModifiedDate(id(5), time(50))
        val page = source().refresh(oldPosition, 4)
        assertEquals(listOf(id(7), id(6), id(4), id(3)), page.ids())
        assertNotNull(page.prevKey)
        assertNotNull(page.nextKey)
    }

    @Test
    fun centeredRefreshWorksWhenAnchorIsDeleted() = runTest(dispatcher) {
        seedGroups()
        db.conversationDAO.deleteConversationByQualifiedID(id(5))
        val page = source().refresh(ConversationListPositionEntity(time(5), id(5)), 4)
        assertEquals(listOf(id(7), id(6), id(4), id(3)), page.ids())
    }

    @Test
    fun centeredRefreshFillsTheWindowAtEitherEnd() = runTest(dispatcher) {
        seedGroups()
        val newest = source().refresh(ConversationListPositionEntity(time(100), id(100)), 4)
        assertEquals(listOf(id(10), id(9), id(8), id(7)), newest.ids())
        assertNull(newest.prevKey)
        val oldest = source().refresh(ConversationListPositionEntity(time(-1), id(-1)), 4)
        assertEquals(listOf(id(4), id(3), id(2), id(1)), oldest.ids())
        assertNull(oldest.nextKey)
    }

    @Test
    fun refreshKeyUsesPresentedItemPositionWithoutQueryingItsCurrentDate() = runTest(dispatcher) {
        seedGroups()
        val source = source()
        val page = source.refresh(size = 4)
        val expected = page.data[2].position
        db.conversationDAO.updateConversationModifiedDate(expected.conversationId, time(100))
        assertEquals(expected, source.getRefreshKey(PagingState(listOf(page), 2, PagingConfig(4, enablePlaceholders = false), 0)))
    }

    @Test
    fun emptyListHasNoKeys() = runTest(dispatcher) {
        val page = source().refresh(ConversationListPositionEntity(time(5), id(5)), 4)
        assertTrue(page.data.isEmpty())
        assertNull(page.prevKey)
        assertNull(page.nextKey)
    }

    @Test
    fun sourceAndSearchFiltersApplyBeforePageLimit() = runTest(dispatcher) {
        seedGroups()
        db.conversationDAO.insertConversation(group(id(20)).copy(type = ConversationEntity.Type.CHANNEL, name = "Needle"))
        db.conversationDAO.insertConversation(group(id(30)).copy(type = ConversationEntity.Type.MEETING, name = "Needle"))
        db.conversationDAO.insertConversation(group(id(40)).copy(type = ConversationEntity.Type.SELF, name = "Needle"))
        val channels = source(ConversationListQueryEntity(source = ConversationFilterEntity.CHANNELS)).refresh(size = 1)
        assertEquals(listOf(id(20)), channels.ids())
        val search = source(ConversationListQueryEntity(searchQuery = "eed")).refresh(size = 1)
        assertEquals(listOf(id(20)), search.ids())
        assertNull(search.nextKey)
        assertEquals(11, source().refresh(size = 50).data.size)
    }

    @Test
    fun oneOnOneMembershipMatchesLegacyIncludingDeletedUsers() = runTest(dispatcher) {
        db.userDAO.upsertUser(newUserEntity(self))
        for (n in 1..4) {
            val other = QualifiedIDEntity("other$n", "wire.com")
            db.conversationDAO.insertConversation(group(id(n)).copy(type = ConversationEntity.Type.ONE_ON_ONE))
            db.userDAO.upsertUser(newUserEntity(other).copy(
                name = if (n == 2 || n == 3) null else "Other $n",
                deleted = false,
                activeOneOnOneConversationId = if (n == 4) null else id(n),
            ))
            db.memberDAO.insertMembersWithQualifiedId(listOf(MemberEntity(other, MemberEntity.Role.Member)), id(n))
            if (n == 3) db.userDAO.markAsDeleted(listOf(other))
        }
        val query = ConversationListQueryEntity(source = ConversationFilterEntity.ONE_ON_ONE)
        val page = source(query).refresh(size = 10)
        assertEquals(listOf(id(3), id(1)), page.ids())
        val legacy = db.conversationDAO.platformExtensions.getPagerForConversationDetailsWithEventsSearch(
            com.wire.kalium.persistence.dao.conversation.ConversationExtensions.QueryConfig(
                conversationFilter = ConversationFilterEntity.ONE_ON_ONE,
            ), PagingConfig(10, enablePlaceholders = false),
        ).pagingSource
        val legacyPage = assertIs<PagingSource.LoadResult.Page<Int, com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity>>(
            legacy.load(PagingSource.LoadParams.Refresh(null, 10, false)),
        )
        assertEquals(legacyPage.data.map { it.conversationViewEntity.id }.toSet(), page.ids().toSet())
        legacy.invalidate()
        assertEquals(listOf(id(1)), source(query.copy(onlyInteractionEnabled = true)).refresh().ids())
    }

    @Test
    fun pendingConnectionUsesConversationDateAndSupportsMissingName() = runTest(dispatcher) {
        db.conversationDAO.insertConversation(group(id(10)))
        db.conversationDAO.insertConversation(group(id(1)).copy(type = ConversationEntity.Type.CONNECTION_PENDING))
        val other = newUserEntity("pending").copy(name = null, connectionStatus = ConnectionEntity.State.PENDING)
        db.userDAO.upsertUser(other)
        db.connectionDAO.insertConnection(ConnectionEntity(
            "c1", self.value, time(100), id(1), other.id, ConnectionEntity.State.PENDING, other.id.value,
        ))
        assertEquals(listOf(id(10), id(1)), source().refresh().ids())
        assertNull(source().refresh().data.last().otherUser?.name)
        assertEquals(emptyList(), source(ConversationListQueryEntity(source = ConversationFilterEntity.ONE_ON_ONE)).refresh().ids())
    }

    @Test
    fun interactionFilterRequiresSelfMembershipForGroups() = runTest(dispatcher) {
        seedGroups()
        db.userDAO.upsertUser(newUserEntity(self))
        db.memberDAO.insertMembersWithQualifiedId(listOf(MemberEntity(self, MemberEntity.Role.Member)), id(2))
        val page = source(ConversationListQueryEntity(onlyInteractionEnabled = true)).refresh()
        assertEquals(listOf(id(2)), page.ids())
        assertTrue(page.data.single().isSelfUserMember)
    }

    @Test
    fun archiveSuppressesDraftAndMessagePreview() = runTest(dispatcher) {
        db.userDAO.upsertUser(newUserEntity(self))
        db.conversationDAO.insertConversation(group(id(1)).copy(archived = true))
        db.messageDAO.insertOrIgnoreMessage(newRegularMessageEntity(conversationId = id(1), senderUserId = self))
        db.messageDraftDAO.upsertMessageDraft(newDraftMessageEntity(id(1)))
        assertTrue(source().refresh().data.isEmpty())
        val row = source(ConversationListQueryEntity(fromArchive = true)).refresh().data.single()
        assertNull(row.draftText)
        assertNull(row.lastMessage)
    }

    @Test
    fun draftAndFolderWritesDoNotInvalidateButNewReadsSeeDraft() = runTest(dispatcher) {
        db.conversationDAO.insertConversation(group(id(1)))
        val source = source()
        source.refresh()
        db.messageDraftDAO.upsertMessageDraft(newDraftMessageEntity(id(1)))
        db.conversationFolderDAO.addFolder(ConversationFolderEntity("f", "Folder", ConversationFolderTypeEntity.USER))
        db.conversationFolderDAO.addConversationToFolder(id(1), "f")
        assertFalse(source.invalid)
        assertEquals("draft text", source().refresh().data.single().draftText)
    }

    @Test
    fun membershipAndOrderWritesInvalidateBeforeFirstLoadAndAfterLoads() = runTest(dispatcher) {
        val before = source()
        seedGroups()
        assertTrue(before.invalid)
        assertIs<PagingSource.LoadResult.Invalid<ConversationListPositionEntity, ConversationListItemEntity>>(
            before.load(PagingSource.LoadParams.Refresh(null, 4, false)),
        )
        val after = source()
        after.refresh()
        db.conversationDAO.updateConversationModifiedDate(id(1), time(100))
        assertTrue(after.invalid)
    }

    @Test
    fun unreadCountersRemainSeparateAndUnreadWritesInvalidate() = runTest(dispatcher) {
        db.userDAO.upsertUser(newUserEntity(self))
        db.conversationDAO.insertConversation(group(id(1)))
        val types = listOf(UnreadEventTypeEntity.MESSAGE, UnreadEventTypeEntity.MENTION, UnreadEventTypeEntity.REPLY,
            UnreadEventTypeEntity.MISSED_CALL, UnreadEventTypeEntity.KNOCK)
        types.forEachIndexed { n, type ->
            val message = newRegularMessageEntity(id = "m$n", conversationId = id(1), senderUserId = self)
            db.messageDAO.insertOrIgnoreMessage(message)
            db.database.unreadEventsQueries.deleteUnreadEvent(message.id, id(1))
            db.database.unreadEventsQueries.insertEvent(message.id, type, id(1), message.date)
        }
        val source = source()
        val row = source.refresh().data.single()
        assertEquals(listOf(1, 1, 1, 1, 1), listOf(row.unreadMessages, row.unreadMentions, row.unreadReplies,
            row.unreadMissedCalls, row.unreadKnocks))
        assertNotNull(row.lastMessage)
        db.database.unreadEventsQueries.deleteUnreadEvent("m0", id(1))
        assertTrue(source.invalid)
    }

    @Test
    fun strictMlsFilterAcceptsEstablishedAndResetConversationsOnly() = runTest(dispatcher) {
        ConversationEntity.GroupState.entries.forEachIndexed { n, state ->
            db.conversationDAO.insertConversation(group(id(n)).copy(protocolInfo = ConversationEntity.ProtocolInfo.MLS(
                "group$n", state, 1u, time(0), ConversationEntity.CipherSuite.MLS_128_DHKEMX25519_AES128GCM_SHA256_Ed25519,
            )))
        }
        val expected = ConversationEntity.GroupState.entries.mapIndexedNotNull { n, state ->
            id(n).takeIf { state == ConversationEntity.GroupState.ESTABLISHED || state == ConversationEntity.GroupState.PENDING_AFTER_RESET }
        }.reversed()
        assertEquals(expected, source().refresh(size = 10).ids())
        assertEquals(ConversationEntity.GroupState.entries.size,
            source(ConversationListQueryEntity(strictMlsFilter = false)).refresh(size = 10).data.size)
    }

    @Test
    fun messageAndUserChangesInvalidateLoadedRows() = runTest(dispatcher) {
        db.userDAO.upsertUser(newUserEntity(self))
        db.conversationDAO.insertConversation(group(id(1)))
        val messageSource = source()
        messageSource.refresh()
        db.messageDAO.insertOrIgnoreMessage(newRegularMessageEntity(conversationId = id(1), senderUserId = self))
        assertTrue(messageSource.invalid)
        val userSource = source()
        userSource.refresh()
        db.userDAO.updateUserDisplayName(self, "New sender name")
        assertTrue(userSource.invalid)
    }

    private fun source(query: ConversationListQueryEntity = ConversationListQueryEntity()) =
        ConversationListPagingSource(db.database.conversationListQueries, query, ReadDispatcher(dispatcher)).also { sources += it }

    private suspend fun seedGroups() {
        (1..10).forEach { db.conversationDAO.insertConversation(group(id(it))) }
    }

    private fun id(n: Int) = QualifiedIDEntity("c$n", "wire.com")
    private fun time(n: Int) = Instant.fromEpochSeconds(n.toLong())
    private fun group(id: QualifiedIDEntity) = newConversationEntity(id).copy(
        type = ConversationEntity.Type.GROUP,
        lastModifiedDate = time(id.value.removePrefix("c").toIntOrNull() ?: 10),
    )

    private suspend fun ConversationListPagingSource.refresh(position: ConversationListPositionEntity? = null, size: Int = 4) =
        load(PagingSource.LoadParams.Refresh(position, size, false)).page()

    private fun PagingSource.LoadResult<ConversationListPositionEntity, ConversationListItemEntity>.page() =
        assertIs<PagingSource.LoadResult.Page<ConversationListPositionEntity, ConversationListItemEntity>>(this)

    private fun PagingSource.LoadResult.Page<ConversationListPositionEntity, ConversationListItemEntity>.ids() =
        data.map { it.position.conversationId }
}
