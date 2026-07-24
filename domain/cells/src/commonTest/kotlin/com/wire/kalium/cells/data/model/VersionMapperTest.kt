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
package com.wire.kalium.cells.data.model

import kotlinx.datetime.Instant
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class VersionMapperTest {

    @Test
    fun givenEpochSeconds_whenMappingToModel_thenModifiedTimeIsInstant() {
        val result = nodeVersionDTO(modifiedTime = EPOCH_SECONDS.toString()).toModel()

        assertEquals(Instant.fromEpochSeconds(EPOCH_SECONDS), result.modifiedTime)
    }

    @Test
    fun givenInvalidModifiedTime_whenMappingToModel_thenModifiedTimeIsNull() {
        val result = nodeVersionDTO(modifiedTime = "invalid").toModel()

        assertNull(result.modifiedTime)
    }

    private fun nodeVersionDTO(modifiedTime: String?) = NodeVersionDTO(
        id = "version-id",
        hash = null,
        description = null,
        isDraft = null,
        etag = null,
        editorUrls = null,
        filePreviews = null,
        isHead = null,
        modifiedTime = modifiedTime,
        ownerName = null,
        ownerUuid = null,
        getUrl = null,
        size = null
    )

    private companion object {
        const val EPOCH_SECONDS = 1_700_000_000L
    }
}
