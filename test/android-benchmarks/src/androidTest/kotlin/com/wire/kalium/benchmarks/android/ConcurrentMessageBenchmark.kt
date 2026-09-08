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
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wire.kalium.benchmarks.fixtures.MessageFixture
import com.wire.kalium.persistence.dao.message.MessageEntity
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.yield
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class ConcurrentMessageBenchmark {
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
    fun insertMessagesWhileReading() {
        benchmarkRule.measureRepeated {
            runBlocking {
                withTimeout(60_000) {
                    coroutineScope {
                        val readerStarted = CompletableDeferred<Unit>()
                        val reader = launch {
                            while (isActive) {
                                db.messageDAO.getMessagesByConversationAndVisibility(
                                    fixture.conversation.id, 50, 0, listOf(MessageEntity.Visibility.VISIBLE)
                                ).first()
                                readerStarted.complete(Unit)
                                yield()
                            }
                        }
                        try {
                            readerStarted.await()
                            fixture.batch.forEach { db.messageDAO.insertOrIgnoreMessage(it) }
                        } finally {
                            reader.cancelAndJoin()
                        }
                    }
                }
            }
            runWithMeasurementDisabled {
                runBlocking {
                    fixture.removeBatch(db)
                }
            }
        }
    }
}
