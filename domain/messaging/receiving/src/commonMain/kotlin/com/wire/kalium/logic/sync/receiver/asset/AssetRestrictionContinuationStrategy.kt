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

package com.wire.kalium.logic.sync.receiver.asset

import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.FileSharingStatus
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.getType
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import com.wire.kalium.logic.data.message.hasValidData

/** A nameless key follow-up may use an existing permitted preview. */
enum class AssetRestrictionContinuationStrategy { CONTINUE, RESTRICT, RESTRICT_WITHOUT_PREVIEW }

fun MessageContent.Asset.restrictionStrategy(
    state: FileSharingStatus.Value,
    validateFileType: ValidateAssetFileTypeUseCase,
): AssetRestrictionContinuationStrategy = when (state) {
    FileSharingStatus.Value.Disabled -> AssetRestrictionContinuationStrategy.RESTRICT
    FileSharingStatus.Value.EnabledAll -> AssetRestrictionContinuationStrategy.CONTINUE
    is FileSharingStatus.Value.EnabledSome -> when {
        validateFileType(value.name, value.mimeType, state.allowedType) -> AssetRestrictionContinuationStrategy.CONTINUE
        value.name.isNullOrEmpty() && value.isAssetDataComplete -> AssetRestrictionContinuationStrategy.RESTRICT_WITHOUT_PREVIEW
        else -> AssetRestrictionContinuationStrategy.RESTRICT
    }
}

fun Message.Regular.restrictedAssetMessage(): Message.Regular {
    val asset = (content as MessageContent.Asset).value
    return copy(content = MessageContent.RestrictedAsset(asset.mimeType, asset.sizeInBytes, asset.name.orEmpty()))
}

/** The caller preserves its read-failure policy when supplying the existing message. */
fun prepareReceivedAssetMessage(
    message: Message.Regular,
    existing: Message?,
    restrictWithoutPreview: Boolean,
): Message.Regular? {
    val content = message.content as MessageContent.Asset
    return if (existing == null) {
        if (restrictWithoutPreview) message.restrictedAssetMessage() else message.copy(
            visibility = if (content.value.isAssetDataComplete) Message.Visibility.VISIBLE else Message.Visibility.HIDDEN
        )
    } else if (existing.senderUserId != message.senderUserId || existing !is Message.Regular ||
        existing.senderClientId != message.senderClientId
    ) {
        kaliumLogger.e("The previously persisted message has a different sender id than the one we are trying to process")
        null
    } else existing.withAssetDecryptionKeys(content.value)
}

private fun Message.Regular.withAssetDecryptionKeys(incoming: AssetContent): Message.Regular? {
    val asset = when (val content = content) {
        is MessageContent.Asset -> content
        is MessageContent.RestrictedAsset -> null
        else -> {
            kaliumLogger.e(
                "Invalid asset message content type=${content.getType()} messageId=$id conversationId=$conversationId"
            )
            null
        }
    }
    return asset?.let {
        val remoteData = if (it.value.remoteData.hasValidData()) it.value.remoteData
        else incoming.remoteData.takeIf { data -> data.hasValidData() } ?: it.value.remoteData
        copy(
            content = it.copy(value = it.value.copy(remoteData = remoteData, preview = incoming.preview ?: it.value.preview)),
            visibility = if (remoteData.hasValidData()) Message.Visibility.VISIBLE else Message.Visibility.HIDDEN
        )
    }
}
