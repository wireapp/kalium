/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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
package com.wire.kalium.cells.domain.usecase

import com.wire.kalium.cells.domain.CellAttachmentsRepository
import com.wire.kalium.cells.domain.CellConversationRepository
import com.wire.kalium.cells.domain.CellUsersRepository
import com.wire.kalium.cells.domain.CellsRepository
import com.wire.kalium.cells.domain.model.CellNode
import com.wire.kalium.cells.domain.model.Node
import com.wire.kalium.cells.domain.model.PaginatedList
import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.isRight
import com.wire.kalium.common.functional.right
import com.wire.kalium.logic.data.asset.AssetTransferStatus
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.CellAssetContent
import io.mockative.Mock
import io.mockative.any
import io.mockative.coEvery
import io.mockative.mock
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class GetPaginatedNodesUseCaseTest {

    @Test
    fun givenSuccessResponse_whenUseCaseInvoked_thenDraftsAreFiltered() = runTest {
        val (_, useCase) = Arrangement().arrange()

        val result = useCase(
            conversationId = null,
            query = "",
            limit = 100,
            offset = 0
        )

        assertTrue(result.isRight())
        assertEquals(2, result.getOrNull()?.data?.size)
    }

    @Test
    fun givenSuccessResponse_whenUseCaseInvoked_thenLocalPathIsUsed() = runTest {
        val (_, useCase) = Arrangement().arrange()

        val result = useCase(
            conversationId = null,
            query = "",
            limit = 100,
            offset = 0
        )

        assertTrue(result.isRight())
        assertTrue(result.getOrNull()?.data?.all { (it as Node.File).localPath == "local_path" } == true)
    }

    @Test
    fun givenSuccessResponse_whenUseCaseInvoked_thenMetadataIsUsed() = runTest {
        val (_, useCase) = Arrangement().arrange()

        val result = useCase(
            conversationId = null,
            query = "",
            limit = 100,
            offset = 0
        )

        assertTrue(result.isRight())
        assertTrue(result.getOrNull()?.data?.all { (it as Node.File).metadata is AssetContent.AssetMetadata.Image } == true)
    }

    @Test
    fun givenSuccessResponse_whenUseCaseInvoked_thenUserNameIsUsed() = runTest {
        val (_, useCase) = Arrangement().arrange()

        val result = useCase(
            conversationId = null,
            query = "",
            limit = 100,
            offset = 0
        )

        assertTrue(result.isRight())
        assertTrue(result.getOrNull()?.data?.all { it.userName == "user_name" } == true)
    }

    @Test
    fun givenSuccessResponse_whenUseCaseInvoked_thenConversationNameIsUsed() = runTest {
        val (_, useCase) = Arrangement().arrange()

        val result = useCase(
            conversationId = null,
            query = "",
            limit = 100,
            offset = 0
        )

        assertTrue(result.isRight())
        assertTrue(result.getOrNull()?.data?.all { it.conversationName == "conversation_name" } == true)
    }

    private inner class Arrangement {

        @Mock
        val cellsRepository = mock(CellsRepository::class)

        @Mock
        val conversationRepository = mock(CellConversationRepository::class)

        @Mock
        val attachmentsRepository = mock(CellAttachmentsRepository::class)

        @Mock
        val usersRepository = mock(CellUsersRepository::class)

        suspend fun arrange(): Pair<Arrangement, GetPaginatedNodesUseCase> {

            coEvery { cellsRepository.getPaginatedNodes(any(), any(), any(), any(), any()) }.returns(
                PaginatedList(
                    data = testNodes,
                    pagination = null,
                ).right()
            )

            coEvery { usersRepository.getUserNames() }.returns(testUserNames.right())

            coEvery { conversationRepository.getConversationNames() }.returns(testConversationNames.right())

            coEvery { attachmentsRepository.getAttachments() }.returns(testAttachments.right())

            coEvery { attachmentsRepository.getStandaloneAssetPaths() }.returns(testAssetPaths.right())

            return this to GetPaginatedNodesUseCaseImpl(
                cellsRepository = cellsRepository,
                conversationRepository = conversationRepository,
                attachmentsRepository = attachmentsRepository,
                usersRepository = usersRepository
            )
        }
    }

    private companion object {

        val testNodes = listOf(
            CellNode(
                uuid = "uuid_1",
                versionId = "version_id",
                path = "path",
                isDraft = false,
                ownerUserId = "user_id",
                conversationId = "conversation_id",
            ),
            CellNode(
                uuid = "uuid_2",
                versionId = "version_id_2",
                path = "path_2",
                isDraft = true,
                ownerUserId = "user_id_2",
                conversationId = "conversation_id_2",
            ),
            CellNode(
                uuid = "uuid_3",
                versionId = "version_id_3",
                path = "path_3",
                isDraft = false,
                ownerUserId = "user_id_3",
                conversationId = "conversation_id_3",
            ),
        )

        val testUserNames = listOf(
            "user_id" to "user_name",
            "user_id_2" to "user_name",
            "user_id_3" to "user_name",
            "user_id_4" to "user_name",
        )

        val testConversationNames = listOf(
            "conversation_id" to "conversation_name",
            "conversation_id_2" to "conversation_name",
            "conversation_id_3" to "conversation_name",
            "conversation_id_4" to "conversation_name",
        )

        val testAssetPaths = listOf(
            "attachment_id" to "local_path",
            "attachment_id_2" to "local_path_2",
            "attachment_id_3" to "local_path_3",
            "attachment_id_4" to "local_path_4",
        )

        val testAttachments = listOf(
            CellAssetContent(
                id = "uuid_1",
                versionId = "version_id",
                mimeType = "image/png",
                localPath = "local_path",
                assetPath = "remote_path",
                assetSize = 123,
                metadata = AssetContent.AssetMetadata.Image(
                    width = 100,
                    height = 200
                ),
                transferStatus = AssetTransferStatus.NOT_DOWNLOADED,
            ),
            CellAssetContent(
                id = "uuid_2",
                versionId = "version_id_2",
                mimeType = "image/png",
                localPath = "local_path",
                assetPath = "remote_path_2",
                assetSize = 123,
                metadata = AssetContent.AssetMetadata.Image(
                    width = 100,
                    height = 200
                ),
                transferStatus = AssetTransferStatus.NOT_DOWNLOADED,
            ),
            CellAssetContent(
                id = "uuid_3",
                versionId = "version_id_3",
                mimeType = "image/png",
                localPath = "local_path",
                assetPath = "remote_path_3",
                assetSize = 123,
                metadata = AssetContent.AssetMetadata.Image(
                    width = 100,
                    height = 200
                ),
                transferStatus = AssetTransferStatus.NOT_DOWNLOADED,
            ),
        )
    }
}
