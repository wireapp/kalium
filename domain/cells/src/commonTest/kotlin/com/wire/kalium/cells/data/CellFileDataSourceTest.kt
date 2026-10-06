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
package com.wire.kalium.cells.data

import com.wire.kalium.cells.domain.usecase.offline.GetOfflineFileUseCaseImpl
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesByConversationUseCaseImpl
import com.wire.kalium.cells.domain.usecase.offline.ObserveOfflineFilesUseCaseImpl
import com.wire.kalium.cells.domain.usecase.offline.OfflineFileInfo
import com.wire.kalium.cells.domain.usecase.offline.SaveOfflineFileUseCaseImpl
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.persistence.dao.cellfile.CellFileDao
import com.wire.kalium.persistence.dao.cellfile.CellFileEntity
import dev.mokkery.MockMode
import dev.mokkery.answering.returns
import dev.mokkery.answering.throws
import dev.mokkery.every
import dev.mokkery.everySuspend
import dev.mokkery.mock
import dev.mokkery.verifySuspend
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNull
import kotlin.test.assertSame

class CellFileDataSourceTest {
    private val dao = mock<CellFileDao>(MockMode.autoUnit)
    private val repository = CellFileDataSource(dao)
    private val conversationId = ConversationId("conv", "domain")

    @Test
    fun givenNullableOrPopulatedPath_whenSavingOffline_thenAllFieldsReachTheDao() = runTest {
        for (path in listOf(null, "conv/reports/été #1.pdf")) {
            val info = offlineInfo(path)
            SaveOfflineFileUseCaseImpl(repository)(info)
            verifySuspend { dao.upsert(entity(path)) }
        }
    }

    @Test
    fun givenNullableOrPopulatedPath_whenReadingOfflineFile_thenAllFieldsReachTheCaller() = runTest {
        for (path in listOf(null, "conv/reports/file.pdf")) {
            everySuspend { dao.getById("file-id") } returns entity(path)
            assertEquals(offlineInfo(path), GetOfflineFileUseCaseImpl(repository)("file-id"))
        }
    }

    @Test
    fun givenOfflineFiles_whenObservingAllOrByConversation_thenBothPathsAreMapped() = runTest {
        val files = listOf(entity(null), entity("conv/reports/file.pdf").copy(uuid = "other-id"))
        val expected = listOf(offlineInfo(null), offlineInfo("conv/reports/file.pdf").copy(id = "other-id"))
        every { dao.observeOfflineFiles() } returns flowOf(files)
        every { dao.observeOfflineFilesByConversationId(conversationId.toString()) } returns flowOf(files)

        assertEquals(expected, ObserveOfflineFilesUseCaseImpl(repository)().first())
        assertEquals(expected, ObserveOfflineFilesByConversationUseCaseImpl(repository)(conversationId).first())
    }

    @Test
    fun givenStandaloneFile_whenSavingAndReading_thenMissingConversationStaysSupported() = runTest {
        val info = offlineInfo("personal/reports/file.pdf").copy(conversationId = null)
        val file = entity(info.remotePath).copy(conversationId = "")
        SaveOfflineFileUseCaseImpl(repository)(info)
        verifySuspend { dao.upsert(file) }
        everySuspend { dao.getById("file-id") } returns file
        assertEquals(info, GetOfflineFileUseCaseImpl(repository)("file-id"))
        everySuspend { dao.getById("missing") } returns null
        assertNull(GetOfflineFileUseCaseImpl(repository)("missing"))
    }

    @Test
    fun givenCancelledSave_whenSavingOffline_thenCancellationReachesTheCaller() = runTest {
        val cancellation = CancellationException("cancel offline save")
        everySuspend { dao.upsert(entity("conv/file.pdf")) } throws cancellation

        assertSame(cancellation, assertFailsWith<CancellationException> {
            SaveOfflineFileUseCaseImpl(repository)(offlineInfo("conv/file.pdf"))
        })
    }

    private fun offlineInfo(remotePath: String?) = OfflineFileInfo(
        id = "file-id",
        conversationId = conversationId.toString(),
        name = "file.pdf",
        mimeType = "application/pdf",
        owner = "owner",
        localPath = "/data/file.pdf",
        size = 1024,
        downloadedAt = 1000L,
        modifiedAt = 500L,
        remotePath = remotePath,
    )

    private fun entity(remotePath: String?) = CellFileEntity(
        uuid = "file-id",
        conversationId = conversationId.toString(),
        name = "file.pdf",
        mimeType = "application/pdf",
        owner = "owner",
        localPath = "/data/file.pdf",
        size = 1024,
        downloadedAt = 1000L,
        modifiedAt = 500L,
        isOffline = true,
        remotePath = remotePath,
    )
}
