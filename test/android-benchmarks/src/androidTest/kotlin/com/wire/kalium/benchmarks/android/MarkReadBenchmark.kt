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
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MarkReadBenchmark {
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
    fun markRead() {
        val message = fixture.incoming
        benchmarkRule.measureRepeated {
            runWithMeasurementDisabled {
                runBlocking { db.messageDAO.insertOrIgnoreMessage(message) }
            }
            val hasUnread = runBlocking {
                db.conversationDAO.updateReadDateAndGetHasUnreadEvents(message.conversationId, message.date)
            }
            runWithMeasurementDisabled {
                check(!hasUnread)
                runBlocking {
                    db.messageDAO.deleteMessage(message.id, message.conversationId)
                    fixture.restoreDates(db)
                }
            }
        }
    }
}
