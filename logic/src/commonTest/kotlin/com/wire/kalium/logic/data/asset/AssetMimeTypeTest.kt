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
package com.wire.kalium.logic.data.asset

import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals

class AssetMimeTypeTest {

    @Test
    fun givenImageMimeTypes_whenCheckingAgainstSupportedAudioMimeTypes_thenTheyAreEquals() {
        val values = setOf(
            "image/jpg",
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp"
        )
        assertEquals(values.size, SUPPORTED_IMAGE_ASSET_MIME_TYPES.size)
        assertEquals(values, SUPPORTED_IMAGE_ASSET_MIME_TYPES)
    }

    @Test
    fun givenAudioMimeTypes_whenCheckingAgainstSupportedAudioMimeTypes_thenTheyAreEquals() {
        val values = setOf(
            "audio/mp3",
            "audio/mp4",
            "audio/mpeg",
            "audio/ogg",
            "audio/wav",
            "audio/x-wav",
            "audio/x-pn-wav",
            "audio/x-m4a"
        )
        assertEquals(values.size, SUPPORTED_AUDIO_ASSET_MIME_TYPES.size)
        assertEquals(values, SUPPORTED_AUDIO_ASSET_MIME_TYPES)
    }

    @Test
    fun givenVideoMimeTypes_whenCheckingAgainstSupportedAudioMimeTypes_thenTheyAreEquals() {
        val values = setOf(
            "video/mp4",
            "video/webm",
            "video/3gpp",
            "video/mkv"
        )
        assertEquals(values.size, SUPPORTED_VIDEO_ASSET_MIME_TYPES.size)
        assertEquals(values, SUPPORTED_VIDEO_ASSET_MIME_TYPES)
    }

    @Test
    fun givenXM4AAudioMessage_whenVerifyingIfMimeTypeIsAudio_thenReturnAttachmentTypeAudio() = runTest {
        // given
        val audioMimeType = "audio/x-m4a"

        // when
        val result = AttachmentType.fromMimeTypeString(audioMimeType)

        // then
        assertEquals(result, AttachmentType.AUDIO)
    }

    @Test
    fun givenXM4AAudioMessage_whenVerifyingIfMimeTypeIsAudio_thenReturnAttachmentTypeGenericFile() = runTest {
        // given
        val audioMimeType = "audio/x-m4a-2" // Unknown Audio Mime Type

        // when
        val result = AttachmentType.fromMimeTypeString(audioMimeType)

        // then
        assertEquals(result, AttachmentType.GENERIC_FILE)
    }
}
