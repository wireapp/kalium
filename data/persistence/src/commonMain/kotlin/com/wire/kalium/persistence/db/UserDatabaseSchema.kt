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

package com.wire.kalium.persistence.db

import app.cash.sqldelight.SuspendingTransacterImpl
import app.cash.sqldelight.async.coroutines.await
import app.cash.sqldelight.db.AfterVersion
import app.cash.sqldelight.db.QueryResult
import app.cash.sqldelight.db.SqlDriver
import app.cash.sqldelight.db.SqlSchema
import com.wire.kalium.persistence.UserDatabase
import com.wire.kalium.persistence.dao.message.AssetPreviewEntity
import com.wire.kalium.protobuf.messages.Asset
import pbandk.decodeFromByteArray

/** Adds the one-time legacy preview conversion to every production user-database opening path. */
internal object UserDatabaseSchema : SqlSchema<QueryResult.AsyncValue<Unit>> by UserDatabase.Schema {
    override fun migrate(
        driver: SqlDriver,
        oldVersion: Long,
        newVersion: Long,
        vararg callbacks: AfterVersion,
    ): QueryResult.AsyncValue<Unit> = QueryResult.AsyncValue {
        object : SuspendingTransacterImpl(driver) {}.transaction {
            if (oldVersion <= LAST_ENVELOPE_SCHEMA_VERSION && newVersion > LAST_ENVELOPE_SCHEMA_VERSION) {
                // 160 adds typed columns; 161 removes the envelope after this conversion succeeds.
                UserDatabase.Schema.migrate(driver, oldVersion, LAST_ENVELOPE_SCHEMA_VERSION, *callbacks).await()
                driver.convertLegacyAssetPreviews()
                UserDatabase.Schema.migrate(driver, LAST_ENVELOPE_SCHEMA_VERSION, newVersion, *callbacks).await()
            } else {
                UserDatabase.Schema.migrate(driver, oldVersion, newVersion, *callbacks).await()
            }
        }
    }
}

private data class LegacyPreview(val rowId: Long, val bytes: ByteArray)

@Suppress("MagicNumber") // SQL bind positions match the explicit column list below.
private suspend fun SqlDriver.convertLegacyAssetPreviews() {
    var previousRowId = Long.MIN_VALUE
    while (true) {
        // Read one envelope at a time, keeping migration memory independent of message history size.
        val row = executeQuery(
            null,
            "SELECT rowid, asset_preview FROM MessageAssetContent WHERE rowid > ? AND asset_preview IS NOT NULL ORDER BY rowid LIMIT 1",
            { cursor ->
                QueryResult.Value(
                    if (cursor.next().value) LegacyPreview(checkNotNull(cursor.getLong(0)), checkNotNull(cursor.getBytes(1))) else null
                )
            },
            1,
        ) { bindLong(0, previousRowId) }.await() ?: break
        val preview = runCatching { Asset.Preview.decodeFromByteArray(row.bytes).toTypedPreview() }.getOrNull()
        // A corrupt/incomplete legacy envelope invalidates only its preview; the original asset is untouched.
        execute(
            null,
            """
                UPDATE MessageAssetContent SET
                    preview_asset_mime_type = ?, preview_asset_size = ?, preview_asset_width = ?, preview_asset_height = ?,
                    preview_asset_id = ?, preview_asset_domain = ?, preview_asset_token = ?, preview_asset_otr_key = ?,
                    preview_asset_sha256 = ?, preview_asset_encryption_algorithm = ?, asset_preview = NULL
                WHERE rowid = ?
            """.trimIndent(),
            11,
        ) {
            bindString(0, preview?.mimeType)
            bindLong(1, preview?.size)
            bindLong(2, preview?.width?.toLong())
            bindLong(3, preview?.height?.toLong())
            bindString(4, preview?.id)
            bindString(5, preview?.domain)
            bindString(6, preview?.token)
            bindBytes(7, preview?.otrKey)
            bindBytes(8, preview?.sha256)
            bindString(9, preview?.encryptionAlgorithm)
            bindLong(10, row.rowId)
        }.await()
        previousRowId = row.rowId
    }
}

@Suppress("ReturnCount")
private fun Asset.Preview.toTypedPreview(): AssetPreviewEntity? {
    val data = remote ?: return null
    val image = image ?: return null
    return AssetPreviewEntity(
        mimeType = mimeType,
        size = size,
        width = image.width,
        height = image.height,
        id = data.assetId ?: return null,
        domain = data.assetDomain,
        token = data.assetToken,
        otrKey = data.otrKey.array,
        sha256 = data.sha256.array,
        encryptionAlgorithm = data.encryption?.name,
    )
}

private const val LAST_ENVELOPE_SCHEMA_VERSION = 161L
