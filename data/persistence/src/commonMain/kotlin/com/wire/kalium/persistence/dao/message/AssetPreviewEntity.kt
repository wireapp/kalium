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

package com.wire.kalium.persistence.dao.message

/** Independently addressable preview metadata. Binary columns contain only cryptographic material. */
data class AssetPreviewEntity(
    val mimeType: String,
    val size: Long,
    val width: Int,
    val height: Int,
    val id: String,
    val domain: String?,
    val token: String?,
    val otrKey: ByteArray,
    val sha256: ByteArray,
    val encryptionAlgorithm: String?,
) {
    override fun equals(other: Any?): Boolean = other is AssetPreviewEntity &&
        mimeType == other.mimeType && size == other.size && width == other.width && height == other.height &&
        id == other.id && domain == other.domain && token == other.token &&
        otrKey.contentEquals(other.otrKey) && sha256.contentEquals(other.sha256) && encryptionAlgorithm == other.encryptionAlgorithm

    override fun hashCode(): Int {
        var result = mimeType.hashCode()
        result = 31 * result + size.hashCode()
        result = 31 * result + width
        result = 31 * result + height
        result = 31 * result + id.hashCode()
        result = 31 * result + (domain?.hashCode() ?: 0)
        result = 31 * result + (token?.hashCode() ?: 0)
        result = 31 * result + otrKey.contentHashCode()
        result = 31 * result + sha256.contentHashCode()
        result = 31 * result + (encryptionAlgorithm?.hashCode() ?: 0)
        return result
    }
}

@Suppress("LongParameterList")
internal fun assetPreviewEntity(
    mimeType: String?,
    size: Long?,
    width: Int?,
    height: Int?,
    id: String?,
    domain: String?,
    token: String?,
    otrKey: ByteArray?,
    sha256: ByteArray?,
    encryptionAlgorithm: String?,
): AssetPreviewEntity? = mimeType?.let {
    AssetPreviewEntity(
        mimeType = it,
        size = requireNotNull(size),
        width = requireNotNull(width),
        height = requireNotNull(height),
        id = requireNotNull(id),
        domain = domain,
        token = token,
        otrKey = requireNotNull(otrKey),
        sha256 = requireNotNull(sha256),
        encryptionAlgorithm = encryptionAlgorithm,
    )
}
