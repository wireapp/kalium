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
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.wire.kalium.benchmarks.fixtures.BenchmarkEntities
import com.wire.kalium.benchmarks.fixtures.MessageFixture
import com.wire.kalium.persistence.dao.asset.AssetTransferStatusEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AssetStatusBenchmark {
    @get:Rule
    val benchmarkRule = BenchmarkRule()
    @get:Rule
    val database = BenchmarkDbRule()
    private val db get() = database.db
    private val fixture = MessageFixture()

    @Before
    fun setUp() = runBlocking {
        fixture.seed(db)
        BenchmarkEntities.messages(fixture.messageCount)
            .filter { it.content is MessageEntityContent.Asset }
            .forEach { db.messageDAO.updateAssetTransferStatus(AssetTransferStatusEntity.UPLOADED, it.id, it.conversationId) }
    }

    @Test
    fun readAssetStatuses() {
        benchmarkRule.measureRepeated {
            val statuses = runBlocking { db.messageDAO.observeAssetStatuses(fixture.conversation.id).first() }
            runWithMeasurementDisabled { check(statuses.size == fixture.messageCount / 20) }
        }
    }
}
