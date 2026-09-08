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
package com.wire.kalium.benchmarks.persistence

import androidx.paging.PagingConfig
import com.wire.kalium.benchmarks.fixtures.BenchmarkEntities
import com.wire.kalium.benchmarks.fixtures.firstPage
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationDetailsWithEventsEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions
import com.wire.kalium.persistence.dao.member.MemberEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import kotlinx.datetime.Instant
import org.openjdk.jmh.annotations.Benchmark
import org.openjdk.jmh.annotations.Level
import org.openjdk.jmh.annotations.Param
import org.openjdk.jmh.annotations.Scope
import org.openjdk.jmh.annotations.Setup
import org.openjdk.jmh.annotations.State
import org.openjdk.jmh.annotations.TearDown

@State(Scope.Thread)
class ConversationPagingBenchmark {
    @Param("0", "500")
    var offset: Int = 0
    private lateinit var directory: java.io.File
    private lateinit var db: UserDatabaseBuilder
    private var page: List<ConversationDetailsWithEventsEntity> = emptyList()
    private val conversations = List(2_000) { index ->
        BenchmarkEntities.conversation.copy(
            id = QualifiedIDEntity("conversation-$index", "wire.com"),
            lastModifiedDate = Instant.fromEpochMilliseconds((2_000 - index) * 100L),
        )
    }

    @Setup(Level.Trial)
    fun setUp() = runBlocking {
        directory = Files.createTempDirectory("conversation-benchmark").toFile()
        db = database(directory)
        db.userDAO.upsertUsers(BenchmarkEntities.users)
        db.conversationDAO.insertConversations(conversations)
        conversations.forEach { conversation ->
            db.memberDAO.insertMembersWithQualifiedId(
                BenchmarkEntities.users.map { MemberEntity(it.id, MemberEntity.Role.Member) }, conversation.id
            )
            val messages = BenchmarkEntities.messages(20).mapIndexed { index, message ->
                message.copy(conversationId = conversation.id, date = Instant.fromEpochMilliseconds(
                    conversation.lastModifiedDate.toEpochMilliseconds() - 19 + index
                ))
            }
            db.messageDAO.insertOrIgnoreMessages(messages)
        }
    }

    @Benchmark
    fun loadInitialOrRestoredPage() = runBlocking {
        page = firstPage(db.conversationDAO.platformExtensions.getPagerForConversationDetailsWithEventsSearch(
            queryConfig = ConversationExtensions.QueryConfig(newActivitiesOnTop = false),
            pagingConfig = PagingConfig(pageSize = 50, initialLoadSize = 50, enablePlaceholders = false),
            startingOffset = offset.toLong(),
        ).pagingDataFlow)
    }

    @TearDown(Level.Invocation)
    fun checkPageAndReleaseSource() = runBlocking {
        check(page.map { it.conversationViewEntity.id } == conversations.drop(offset).take(50).map { it.id })
        conversations.first().let { db.conversationDAO.updateConversationModifiedDate(it.id, it.lastModifiedDate) }
    }

    @TearDown(Level.Trial)
    fun tearDown() {
        if (::db.isInitialized) db.nuke()
        if (::directory.isInitialized) directory.deleteRecursively()
    }
}
