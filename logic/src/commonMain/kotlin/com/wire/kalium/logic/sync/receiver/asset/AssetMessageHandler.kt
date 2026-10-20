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

import com.wire.kalium.common.functional.getOrNull
import com.wire.kalium.common.functional.onSuccess
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.configuration.UserConfigRepository
import com.wire.kalium.logic.data.message.Message
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.data.message.MessageRepository
import com.wire.kalium.logic.data.message.PersistMessageUseCase
import com.wire.kalium.logic.di.UserSessionBinding
import com.wire.kalium.logic.feature.asset.ValidateAssetFileTypeUseCase
import dev.zacsweers.metro.Inject

internal interface AssetMessageHandler {
    suspend fun handle(message: Message.Regular)
}

@Inject
internal class AssetMessageHandlerImpl(
    @UserSessionBinding("messageRepository")
    private val messageRepository: MessageRepository,
    @UserSessionBinding("persistMessage")
    private val persistMessage: PersistMessageUseCase,
    @UserSessionBinding("userConfigRepository")
    private val userConfigRepository: UserConfigRepository,
    @UserSessionBinding("validateAssetMimeType")
    private val validateAssetMimeTypeUseCase: ValidateAssetFileTypeUseCase
) : AssetMessageHandler {
    override suspend fun handle(message: Message.Regular) {
        val content = message.content as? MessageContent.Asset ?: run {
            kaliumLogger.e("The asset message trying to be processed has invalid content data")
            return
        }
        userConfigRepository.isFileSharingEnabled().onSuccess { status ->
            val strategy = content.restrictionStrategy(status.state, validateAssetMimeTypeUseCase)
            val received = if (strategy == AssetRestrictionContinuationStrategy.RESTRICT) {
                message.restrictedAssetMessage()
            } else {
                // Preserve the app's existing fallback: a failed read behaves like a missing preview.
                val existing = messageRepository.getMessageById(message.conversationId, message.id).getOrNull()
                prepareReceivedAssetMessage(message, existing, strategy == AssetRestrictionContinuationStrategy.RESTRICT_WITHOUT_PREVIEW)
            }
            received?.let { persistMessage(it) }
        }
    }
}
