/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.data.message.linkpreview

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class LinkPreviewAssetTest {
    @Test
    fun givenRemoteIdentityAndFullKeyAndHash_whenCheckingRemoteData_thenAcceptsImage() {
        assertTrue(TEST_IMAGE.hasRemoteAssetData())
    }

    @Test
    fun givenMissingRemoteIdentity_whenCheckingRemoteData_thenRejectsImage() {
        for (assetKey in listOf(null, "", " ")) {
            assertFalse(TEST_IMAGE.copy(assetKey = assetKey).hasRemoteAssetData())
        }
    }

    @Test
    fun givenInvalidKeySizeAndFullHash_whenCheckingRemoteData_thenRejectsImage() {
        for (size in (0 until 32).toList() + listOf(33, 64)) {
            assertFalse(TEST_IMAGE.copy(otrKey = ByteArray(size)).hasRemoteAssetData(), "Key size: $size")
        }
    }

    @Test
    fun givenFullKeyAndInvalidHashSize_whenCheckingRemoteData_thenRejectsImage() {
        for (size in (0 until 32).toList() + listOf(33, 64)) {
            assertFalse(TEST_IMAGE.copy(sha256Key = ByteArray(size)).hasRemoteAssetData(), "Hash size: $size")
        }
    }
}

private val TEST_IMAGE = LinkPreviewAsset(
    mimeType = "image/png",
    assetDataPath = null,
    assetDataSize = 128,
    assetHeight = 630,
    assetWidth = 1200,
    assetKey = "asset-key",
    otrKey = ByteArray(32) { 1 },
    sha256Key = ByteArray(32) { 2 },
)
