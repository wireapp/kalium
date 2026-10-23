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

package com.wire.kalium.persistence.backup

import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.wire.kalium.persistence.BaseDatabaseTest
import com.wire.kalium.persistence.dao.message.AssetPreviewEntity
import com.wire.kalium.persistence.dao.message.MessageEntityContent
import com.wire.kalium.persistence.utils.stubs.newConversationEntity
import com.wire.kalium.persistence.utils.stubs.newRegularMessageEntity
import com.wire.kalium.persistence.utils.stubs.newUserEntity
import kotlinx.coroutines.test.runTest
import java.io.File
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertNull

class ObfuscatedCopyExporterTest : BaseDatabaseTest() {
    @Suppress("LongMethod")
    @Test
    fun obfuscatedCopyDestroysPreviewDecryptionKeyAndDownloadCredentials() = runTest {
        val self = newUserEntity("obfuscation-self")
        val conversation = newConversationEntity("obfuscation-conversation")
        val source = createDatabase(self.id, null, false)
        val copy = File.createTempFile("obfuscated-preview-", ".db").also { it.delete() }
        try {
            source.userDAO.upsertUser(self)
            source.conversationDAO.insertConversation(conversation)
            val preview = AssetPreviewEntity(
                mimeType = "image/jpeg", size = 1234, width = 512, height = 288,
                id = "secret-preview-identifier", domain = "secret.domain", token = "secret-preview-token",
                otrKey = ByteArray(32) { 1 }, sha256 = ByteArray(32) { 2 }, encryptionAlgorithm = "AES_GCM",
            )
            val original = MessageEntityContent.Asset(
                assetSizeInBytes = 10000, assetMimeType = "video/mp4", assetId = "original-video",
                assetOtrKey = ByteArray(32) { 3 }, assetSha256Key = ByteArray(32) { 4 }, assetEncryptionAlgorithm = "AES_GCM",
            )
            val message = newRegularMessageEntity("preview", conversationId = conversation.id, senderUserId = self.id,
                content = original.copy(assetPreview = preview))
            source.messageDAO.insertOrIgnoreMessages(listOf(
                message, message.copy(id = "without-preview", content = original),
                message.copy(id = "optional-null", content = original.copy(assetPreview = preview.copy(domain = null, token = null))),
            ))
            val plaintext = "private video preview frame".encodeToByteArray()
            val nonce = ByteArray(12) { 5 }
            val encrypted = cipher(Cipher.ENCRYPT_MODE, preview.otrKey, nonce).doFinal(plaintext)
            assertContentEquals(plaintext, cipher(Cipher.DECRYPT_MODE, preview.otrKey, nonce).doFinal(encrypted))

            // Copy first, then execute exactly the asset sanitization used by the support exporter.
            source.sqlDriver.execute(null, "VACUUM INTO ?", 1) { bindString(0, copy.absolutePath) }
            JdbcSqliteDriver("jdbc:sqlite:${copy.absolutePath}").use { exported ->
                obfuscateAssetContent(exported)
                val exportedKey = exported.blob("SELECT preview_asset_otr_key FROM MessageAssetContent WHERE message_id = 'preview'")!!
                val exportedHash = exported.blob("SELECT preview_asset_sha256 FROM MessageAssetContent WHERE message_id = 'preview'")!!
                assertEquals(32, exportedKey.size)
                assertEquals(32, exportedHash.size)
                assertFalse(preview.otrKey.contentEquals(exportedKey))
                assertFalse(preview.sha256.contentEquals(exportedHash))
                // Disabling hash verification still cannot reveal the original preview.
                assertFailsWith<AEADBadTagException> { cipher(Cipher.DECRYPT_MODE, exportedKey, nonce).doFinal(encrypted) }
                assertFalse(preview.id == exported.text("SELECT preview_asset_id FROM MessageAssetContent WHERE message_id = 'preview'"))
                assertFalse(preview.token == exported.text(
                    "SELECT preview_asset_token FROM MessageAssetContent WHERE message_id = 'preview'"
                ))
                assertFalse(preview.domain == exported.text(
                    "SELECT preview_asset_domain FROM MessageAssetContent WHERE message_id = 'preview'"
                ))
                assertEquals("image/jpeg", exported.text(
                    "SELECT preview_asset_mime_type FROM MessageAssetContent WHERE message_id = 'preview'"
                ))
                listOf(
                    "preview_asset_id", "preview_asset_token", "preview_asset_domain", "preview_asset_otr_key", "preview_asset_sha256"
                ).forEach {
                    assertNull(exported.text("SELECT $it FROM MessageAssetContent WHERE message_id = 'without-preview'"))
                }
                assertNull(exported.text("SELECT preview_asset_token FROM MessageAssetContent WHERE message_id = 'optional-null'"))
                assertNull(exported.text("SELECT preview_asset_domain FROM MessageAssetContent WHERE message_id = 'optional-null'"))
            }
            val sourceContent = source.messageDAO.getMessageById(message.id, message.conversationId)!!.content as MessageEntityContent.Asset
            val sourcePreview = sourceContent.assetPreview!!
            assertEquals(preview, sourcePreview)
        } finally {
            source.sqlDriver.close()
            deleteDatabase(self.id)
            copy.delete()
        }
    }

    private fun cipher(mode: Int, key: ByteArray, nonce: ByteArray): Cipher = Cipher.getInstance("AES/GCM/NoPadding").apply {
        init(mode, SecretKeySpec(key, "AES"), GCMParameterSpec(128, nonce))
    }

    private fun SqlDriver.text(sql: String): String? = executeQuery(null, sql, { cursor ->
        check(cursor.next().value)
        QueryResult.Value(cursor.getString(0))
    }, 0).value

    private fun SqlDriver.blob(sql: String): ByteArray? = executeQuery(null, sql, { cursor ->
        check(cursor.next().value)
        QueryResult.Value(cursor.getBytes(0))
    }, 0).value
}
