/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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

package com.wire.kalium.cells

import com.wire.kalium.logic.data.id.QualifiedID
import com.wire.kalium.persistence.dao.cellfile.CellFileDao
import dev.mokkery.every
import dev.mokkery.mock
import dev.mokkery.answering.returns
import io.ktor.client.HttpClient
import io.ktor.client.engine.mock.MockEngine
import io.ktor.client.engine.mock.respondOk
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.job
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class CellsOwnerBindingIdentityTest {
    @Test
    fun generatedBindingsCacheUseCasesPerOwnerWithoutTouchingNetworkOrDownloadPorts() = runTest {
        val cellFiles = mock<CellFileDao>()
        every { cellFiles.observeOfflineFiles() } returns flowOf(emptyList())
        val borrowed = HttpClient(MockEngine { error("Offline queries must not make HTTP requests") })
        val dependencies = LegacyCellsDependencies(
            cellsClient = borrowed,
            dao = daos(cellFiles), sessionManager = mock(), accessTokenApi = mock(),
            userId = QualifiedID("self", "example.com"), networkStateObserver = mock(),
        )
        val ports = object : CellsOwnerPorts {
            override val coroutineScope: CoroutineScope = backgroundScope
            override val downloadHttpClient: HttpClient get() = error("Offline queries must not request downloads")
            override suspend fun getNodeServiceApi(credentials: () -> CellsCredentialsProvider) =
                error("Offline queries must not open the node API")
        }
        try {
            val first = createLegacyCellsGraph(dependencies, ports)
            val second = createLegacyCellsGraph(dependencies, ports)
            assertSame(first.cellsObserveOfflineFiles, first.cellsObserveOfflineFiles)
            assertSame(first.cellsCellFileRepository, first.cellsCellFileRepository)
            assertNotSame(first.cellsObserveOfflineFiles, second.cellsObserveOfflineFiles)
            assertNotSame(first.cellsCellFileRepository, second.cellsCellFileRepository)
            assertEquals(emptyList(), first.cellsObserveOfflineFiles().first())
            assertEquals(emptyList(), second.cellsObserveOfflineFiles().first())
            assertSame(first.cellsMessageAttachmentsDraftRepository, first.cellsMessageAttachmentsDraftRepository)
            assertNotSame(first.cellsMessageAttachmentsDraftRepository, second.cellsMessageAttachmentsDraftRepository)
        } finally {
            borrowed.close()
        }
    }

    @Test
    fun publicSixArgumentConstructorKeepsIndependentJobsAndCachedMembers() = runTest {
        val borrowed = HttpClient(MockEngine { respondOk() })
        val first = CellsScope(borrowed, daos(), mock(), mock(), QualifiedID("first", "example.com"), mock())
        val second = CellsScope(borrowed, daos(), mock(), mock(), QualifiedID("second", "example.com"), mock())
        try {
            assertSame(first.removeAttachments, first.removeAttachments)
            assertNotSame(first.removeAttachments, second.removeAttachments)
            assertSame(first.messageAttachmentsDraftRepository, first.messageAttachmentsDraftRepository)
            assertNotSame(first.messageAttachmentsDraftRepository, second.messageAttachmentsDraftRepository)
            assertNotSame(first.coroutineContext.job, second.coroutineContext.job)
            assertNotSame(first.downloadHttpClient, second.downloadHttpClient)
            first.cancel()
            first.coroutineContext.job.join()
            assertFalse(first.coroutineContext.job.isActive)
            assertFalse(second.coroutineContext.job.isCancelled)
            assertFalse(borrowed.coroutineContext.job.isCancelled)
        } finally {
            first.cancel()
            second.cancel()
            first.downloadHttpClient.close()
            second.downloadHttpClient.close()
            borrowed.close()
        }
    }

    private fun daos(cellFileDao: CellFileDao = mock()): CellsScope.CellScopeDao = CellsScope.CellScopeDao(
        attachmentDraftDao = mock(), conversationsDao = mock(), attachmentsDao = mock(), cellFileDao = cellFileDao,
        userDao = mock(), memberDao = mock(), publicLinkDao = mock(), userConfigDAO = mock(),
    )
}
