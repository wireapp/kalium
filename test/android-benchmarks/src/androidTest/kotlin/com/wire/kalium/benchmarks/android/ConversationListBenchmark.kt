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

package com.wire.kalium.benchmarks.android

import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.paging.PagingConfig
import com.wire.kalium.benchmarks.fixtures.firstPage
import com.wire.kalium.persistence.dao.QualifiedIDEntity
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import com.wire.kalium.persistence.dao.conversation.ConversationExtensions
import com.wire.kalium.persistence.dao.conversation.ConversationFilterEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListPositionEntity
import com.wire.kalium.persistence.dao.conversation.ConversationListQueryEntity
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

/** Fresh Pager-to-presenter loads against an already-open SQLCipher database. See this module's README. */
@RunWith(Parameterized::class)
class ConversationListBenchmark(
    private val implementation: Implementation,
    private val scenario: Scenario,
    private val depth: Depth,
) {
    enum class Implementation { LEGACY, V2 }
    enum class Scenario { ALL, GROUPS, CHANNELS, ONE_ON_ONE, ARCHIVE, SEARCH }
    enum class Depth { FIRST, MIDDLE, DEEP }

    @get:Rule
    val benchmarkRule = BenchmarkRule()
    @get:Rule
    val database = BenchmarkDbRule()
    private val db get() = database.db
    private val fixture = ConversationListFixture()
    private lateinit var expectedIds: List<QualifiedIDEntity>
    private var offset = 0
    private var position: ConversationListPositionEntity? = null
    private val config = PagingConfig(pageSize = PAGE_SIZE, initialLoadSize = PAGE_SIZE, prefetchDistance = 1, enablePlaceholders = false)

    private val query: ConversationListQueryEntity
        get() = ConversationListQueryEntity(
            source = when (scenario) {
                Scenario.GROUPS -> ConversationFilterEntity.GROUPS
                Scenario.CHANNELS -> ConversationFilterEntity.CHANNELS
                Scenario.ONE_ON_ONE -> ConversationFilterEntity.ONE_ON_ONE
                else -> ConversationFilterEntity.ALL
            },
            searchQuery = if (scenario == Scenario.SEARCH) "Needle" else "",
            fromArchive = scenario == Scenario.ARCHIVE,
        )

    @Before
    fun setUp() = runBlocking {
        fixture.seed(db, database.context)
        val eligible = fixture.conversations.filter { conversation ->
            conversation.archived == query.fromArchive && when (scenario) {
                Scenario.GROUPS -> conversation.type == ConversationEntity.Type.GROUP
                Scenario.CHANNELS -> conversation.type == ConversationEntity.Type.CHANNEL
                Scenario.ONE_ON_ONE -> conversation.type == ConversationEntity.Type.ONE_ON_ONE
                Scenario.SEARCH -> conversation.name.orEmpty().contains(query.searchQuery)
                else -> true
            }
        }
        require(eligible.size >= PAGE_SIZE * 2) { "Increase conversationListCount for this filtered source" }
        offset = when (depth) {
            Depth.FIRST -> 0
            Depth.MIDDLE -> (eligible.size - PAGE_SIZE) / 2
            Depth.DEEP -> (eligible.size - PAGE_SIZE) * 9 / 10
        }
        expectedIds = eligible.drop(offset).take(PAGE_SIZE).map { it.id }
        position = if (offset == 0) null else eligible[offset + PAGE_SIZE / 2].let {
            ConversationListPositionEntity(it.lastModifiedDate, it.id)
        }
    }

    @Test
    fun loadInitialOrRestoredPage() {
        benchmarkRule.measureRepeated {
            val ids = runBlocking { loadPageIds() }
            runWithMeasurementDisabled {
                check(ids == expectedIds) { "Wrong page for $implementation/$scenario/$depth: $ids" }
                // The update invalidates cancelled sources and detaches their SQLDelight listeners.
                runBlocking { fixture.restoreConversationDate(db) }
            }
        }
    }

    private suspend fun loadPageIds(): List<QualifiedIDEntity> = when (implementation) {
        Implementation.LEGACY -> firstPage(db.conversationDAO.platformExtensions.getPagerForConversationDetailsWithEventsSearch(
            queryConfig = ConversationExtensions.QueryConfig(
                searchQuery = query.searchQuery, fromArchive = query.fromArchive, conversationFilter = query.source,
                newActivitiesOnTop = false,
            ),
            pagingConfig = config,
            startingOffset = offset.toLong(),
        ).pagingDataFlow).map { it.conversationViewEntity.id }
        Implementation.V2 -> firstPage(db.conversationDAO.observeConversationListItems(config, query, position))
            .map { it.position.conversationId }
    }

    companion object {
        private const val PAGE_SIZE = 20

        @JvmStatic
        @Parameterized.Parameters(name = "{0}_{1}_{2}")
        fun cases(): List<Array<Any>> = Implementation.entries.flatMap { implementation ->
            Scenario.entries.flatMap { scenario -> Depth.entries.map { depth -> arrayOf(implementation, scenario, depth) } }
        }
    }
}
