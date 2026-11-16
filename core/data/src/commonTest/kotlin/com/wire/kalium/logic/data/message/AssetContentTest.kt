/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-only
 */
package com.wire.kalium.logic.data.message

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AssetContentTest {
    @Test
    fun givenRemoteIdentityAndFullKeyAndHash_whenCheckingRemoteData_thenAcceptsAsset() {
        assertTrue(TEST_REMOTE_DATA.hasValidData())
    }

    @Test
    fun givenMissingRemoteIdentity_whenCheckingRemoteData_thenRejectsAsset() {
        assertFalse(TEST_REMOTE_DATA.copy(assetId = "").hasValidData())
    }

    @Test
    fun givenInvalidKeySizeAndFullHash_whenCheckingRemoteData_thenRejectsAsset() {
        for (size in (0 until 32).toList() + listOf(33, 64)) {
            assertFalse(TEST_REMOTE_DATA.copy(otrKey = ByteArray(size)).hasValidData(), "Key size: $size")
        }
    }

    @Test
    fun givenFullKeyAndInvalidHashSize_whenCheckingRemoteData_thenRejectsAsset() {
        for (size in (0 until 32).toList() + listOf(33, 64)) {
            assertFalse(TEST_REMOTE_DATA.copy(sha256 = ByteArray(size)).hasValidData(), "Hash size: $size")
        }
    }
}

private val TEST_REMOTE_DATA = AssetContent.RemoteData(
    assetId = "asset-key",
    otrKey = ByteArray(32) { 1 },
    sha256 = ByteArray(32) { 2 },
    assetToken = null,
    assetDomain = "domain",
    encryptionAlgorithm = MessageEncryptionAlgorithm.AES_CBC,
)
