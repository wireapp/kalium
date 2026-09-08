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

import android.content.Context
import androidx.benchmark.junit4.BenchmarkRule
import androidx.benchmark.junit4.measureRepeated
import androidx.paging.PagingConfig
import androidx.test.core.app.ApplicationProvider
import com.wire.kalium.benchmarks.fixtures.MessageFixture
import com.wire.kalium.benchmarks.fixtures.firstPage
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.junit.runners.Parameterized

@RunWith(Parameterized::class)
class MessagePagingBenchmark(private val offset: Int) {
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    private val context: Context = ApplicationProvider.getApplicationContext()
    private val fixture = MessageFixture()
    private lateinit var db: UserDatabaseBuilder

    @Before
    fun setUp() = runBlocking {
        BenchmarkDb.delete(context)
        db = BenchmarkDb.build(context)
        fixture.seed(db)
    }

    @After
    fun tearDown() {
        if (::db.isInitialized) db.nuke()
    }

    @Test
    fun loadInitialOrRestoredPage() {
        benchmarkRule.measureRepeated {
            val page = runBlocking {
                firstPage(db.messageDAO.platformExtensions.getPagerForConversation(
                    conversationId = fixture.conversation.id,
                    visibilities = listOf(MessageEntity.Visibility.VISIBLE),
                    pagingConfig = PagingConfig(pageSize = 50, initialLoadSize = 50, enablePlaceholders = false),
                    startingOffset = offset.toLong(),
                ).pagingDataFlow)
            }
            runWithMeasurementDisabled {
                check(page.map { it.id } == fixture.expectedPageIds(offset, 50))
                runBlocking {
                    // Detach the cancelled source's SQLDelight listener through invalidation.
                    db.messageDAO.insertOrIgnoreMessage(fixture.incoming)
                    db.messageDAO.deleteMessage(fixture.incoming.id, fixture.conversation.id)
                    fixture.restoreDates(db)
                }
            }
        }
    }

    companion object {
        @JvmStatic
        @Parameterized.Parameters(name = "offset={0}")
        fun offsets() = listOf(0, 5_000)
    }
}
