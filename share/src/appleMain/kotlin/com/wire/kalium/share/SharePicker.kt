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

package com.wire.kalium.share

import com.wire.kalium.common.error.StorageFailure
import com.wire.kalium.common.functional.Either
import com.wire.kalium.logic.configuration.UserConfigDataSource
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.featureFlags.BuildFileRestrictionState
import com.wire.kalium.persistence.db.UserDatabaseBuilder
import com.wire.kalium.persistence.dao.conversation.ConversationEntity
import kotlinx.coroutines.flow.first
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import okio.FileSystem
import okio.Path.Companion.toPath

/**
 * What the picker lists for an account. The extension reads it from the account, and the app writes it for the
 * extension while the app owns the account (ADR 13).
 */
@Serializable
internal data class SharePicker(
    @SerialName("selfName") val selfName: String?,
    /** The team enforces App Lock, which the extension cannot unlock, so nothing is listed. */
    @SerialName("appLock") val appLock: Boolean,
    /** Null when the team disabled file sharing or the setting cannot be read. */
    @SerialName("files") val files: SharePolicy?,
    @SerialName("conversations") val conversations: List<Conversation>,
) {
    @Serializable
    data class Conversation(
        @SerialName("value") val value: String,
        @SerialName("domain") val domain: String,
        @SerialName("name") val name: String?,
        @SerialName("isGroup") val isGroup: Boolean,
        /** Whether the current destination supports files. */
        @SerialName("acceptsFiles") val acceptsFiles: Boolean,
        @SerialName("avatar") val avatar: Avatar? = null,
    )

    @Serializable
    data class Avatar(
        @SerialName("value") val value: String,
        @SerialName("domain") val domain: String,
        @SerialName("cachedPath") val cachedPath: String? = null,
    )
}

internal suspend fun UserDatabaseBuilder.sharePicker(selfUserId: UserId, restriction: BuildFileRestrictionState): SharePicker {
    val selfName = userDAO.getUserDetailsByQualifiedID(selfUserId.toDao())?.name
    if (appLockEnforcedByTeam(restriction)) return SharePicker(selfName, appLock = true, files = null, conversations = emptyList())
    return SharePicker(
        selfName = selfName,
        appLock = false,
        files = sharePolicy(selfUserId, restriction),
        conversations = shareableConversations().map { conversation ->
            val participant = conversation.otherUserId
                ?.takeIf { conversation.type == ConversationEntity.Type.ONE_ON_ONE }
                ?.let { userDAO.getUserDetailsByQualifiedID(it) }
            val avatarAssets = listOfNotNull(participant?.previewAssetId, participant?.completeAssetId).distinct()
            val avatar = avatarAssets.firstNotNullOfOrNull { asset ->
                assetDAO.getAssetByKey(asset.value).first()?.dataPath
                    ?.takeIf { path -> FileSystem.SYSTEM.exists(path.toPath()) }
                    ?.let { path -> SharePicker.Avatar(asset.value, asset.domain, path) }
            } ?: avatarAssets.firstOrNull()?.let { asset -> SharePicker.Avatar(asset.value, asset.domain) }
            SharePicker.Conversation(
                conversation.id.value,
                conversation.id.domain,
                conversation.name,
                conversation.type.isGroup,
                acceptsFiles = conversation.wireCell == null || cellsConfigured(),
                avatar = avatar,
            )
        },
    )
}

/** An unreadable setting counts as enforced. */
internal suspend fun UserDatabaseBuilder.appLockEnforcedByTeam(restriction: BuildFileRestrictionState): Boolean {
    val config = UserConfigDataSource(userPrefsDAO, userConfigDAO, lazy { restriction }).isTeamAppLockEnabled()
    return when (config) {
        is Either.Left -> config.value !is StorageFailure.DataNotFound
        is Either.Right -> config.value.isEnforced
    }
}

/** A missing or not-yet-synced Cells backend cannot accept attachments. */
internal suspend fun UserDatabaseBuilder.cellsConfigured(): Boolean =
    !userConfigDAO.getWireCellsConfig()?.backendUrl.isNullOrBlank()
