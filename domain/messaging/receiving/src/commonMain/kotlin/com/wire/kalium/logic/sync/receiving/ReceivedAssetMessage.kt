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
package com.wire.kalium.logic.sync.receiving

import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.RegularMessageMapper
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCaseImpl
import com.wire.kalium.logic.sync.receiver.asset.AssetRestrictionContinuationStrategy
import com.wire.kalium.logic.sync.receiver.asset.prepareReceivedAssetMessage
import com.wire.kalium.logic.sync.receiver.asset.restrictedAssetMessage
import com.wire.kalium.logic.sync.receiver.asset.restrictionStrategy
import com.wire.kalium.persistence.dao.message.MessageEntity

internal suspend fun prepareReceivedAsset(
    message: Message.Regular,
    existing: MessageEntity?,
    mapper: RegularMessageMapper,
    userConfig: UserConfigRepository,
): Message.Regular? {
    val status = userConfig.isFileSharingEnabled().orThrow()
    if (existing != null && existing !is MessageEntity.Regular) return null
    val strategy = (message.content as MessageContent.Asset).restrictionStrategy(status.state, ValidateAssetFileTypeUseCaseImpl())
    return if (strategy == AssetRestrictionContinuationStrategy.RESTRICT) message.restrictedAssetMessage() else {
        prepareReceivedAssetMessage(
            message,
            (existing as? MessageEntity.Regular)?.let(mapper::fromEntity),
            strategy == AssetRestrictionContinuationStrategy.RESTRICT_WITHOUT_PREVIEW
        )
    }
}
