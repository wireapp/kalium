/*
 * Wire
 * Copyright (C) 2025 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.data.message.AssetContent
import com.wire.kalium.logic.data.message.AssetContent.AssetMetadata
import com.wire.kalium.logic.data.message.MessageContent
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.logic.feature.message.MessageScope
import com.wire.kalium.logic.sync.DefaultWorker
import com.wire.kalium.logic.sync.Result

internal interface AudioNormalizedLoudnessWorker : DefaultWorker {
    override suspend fun doWork(): Result

    companion object Companion {
        const val NAME: String = "AudioNormalizedLoudnessWorker"
    }
}

internal class AudioNormalizedLoudnessWorkerImpl(
    private val conversationId: ConversationId,
    private val messageId: String,
    private val messageScope: MessageScope,
    private val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder
) : AudioNormalizedLoudnessWorker {
    override suspend fun doWork(): Result {
        getAssetContentOrNull()?.let { assetContent ->
            val assetMetadata = assetContent.metadata
            val assetLocalData = assetContent.localData

            if (assetLocalData != null && assetMetadata is AssetMetadata.Audio && assetMetadata.normalizedLoudness == null) {
                val normalizedLoudness = audioNormalizedLoudnessBuilder(assetLocalData.assetDataPath)
                if (normalizedLoudness != null) {
                    messageScope.updateAudioMessageNormalizedLoudnessUseCase(conversationId, messageId, normalizedLoudness)
                    return Result.Success
                }
            }
        }
        return Result.Failure // if any of the conditions are not met
    }

    private suspend fun getAssetContentOrNull(): AssetContent? = messageScope.getMessageById(conversationId, messageId).let {
        (it as? GetMessageByIdUseCase.Result.Success)?.message?.content?.let {
            (it as? MessageContent.Asset)?.value
        }
    }
}
