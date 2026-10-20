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
import com.wire.kalium.logic.di.GlobalBinding
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder
import com.wire.kalium.logic.feature.asset.GetMessageAssetUseCase
import com.wire.kalium.logic.feature.asset.MessageAssetResult
import com.wire.kalium.logic.feature.asset.UpdateAudioMessageNormalizedLoudnessUseCase
import com.wire.kalium.logic.feature.message.GetMessageByIdUseCase
import com.wire.kalium.logic.feature.message.LegacyMessageBinding
import com.wire.kalium.logic.sync.DefaultWorker
import com.wire.kalium.logic.sync.Result
import dev.zacsweers.metro.Assisted
import dev.zacsweers.metro.AssistedFactory
import dev.zacsweers.metro.AssistedInject
import dev.zacsweers.metro.Provider

internal interface AudioNormalizedLoudnessWorker : DefaultWorker {
    override suspend fun doWork(): Result

    companion object Companion {
        const val NAME: String = "AudioNormalizedLoudnessWorker"
    }
}

/** Providers preserve fresh lookup at each old facade getter read, including repeated work. */
@AssistedInject
@Suppress("LongParameterList") // Two operation IDs, three deferred use cases and the existing global builder.
internal class AudioNormalizedLoudnessWorkerImpl(
    @Assisted private val conversationId: ConversationId,
    @Assisted private val messageId: String,
    @LegacyMessageBinding("getMessageById")
    private val getMessageById: Provider<GetMessageByIdUseCase>,
    @LegacyMessageBinding("getAssetMessage")
    private val getAssetMessage: Provider<GetMessageAssetUseCase>,
    @LegacyMessageBinding("updateAudioMessageNormalizedLoudnessUseCase")
    private val updateAudioMessageNormalizedLoudness: Provider<UpdateAudioMessageNormalizedLoudnessUseCase>,
    @GlobalBinding("audioNormalizedLoudnessBuilder")
    private val audioNormalizedLoudnessBuilder: AudioNormalizedLoudnessBuilder,
) : AudioNormalizedLoudnessWorker {
    override suspend fun doWork(): Result {
        getAssetContentOrNull()?.let { assetContent ->
            val assetMetadata = assetContent.metadata

            if (assetMetadata is AssetMetadata.Audio && assetMetadata.normalizedLoudness == null) {
                val assetDataPath = getAssetPathOrNull()
                val normalizedLoudness = if (assetDataPath != null) {
                    audioNormalizedLoudnessBuilder(assetDataPath)
                } else {
                    null
                }
                if (normalizedLoudness != null) {
                    updateAudioMessageNormalizedLoudness()(conversationId, messageId, normalizedLoudness)
                    return Result.Success
                }
            }
        }
        return Result.Failure // if any of the conditions are not met
    }

    private suspend fun getAssetContentOrNull(): AssetContent? = getMessageById()(conversationId, messageId).let {
        (it as? GetMessageByIdUseCase.Result.Success)?.message?.content?.let {
            (it as? MessageContent.Asset)?.value
        }
    }

    private suspend fun getAssetPathOrNull(): String? = when (
        val result = getAssetMessage()(
            conversationId,
            messageId
        )
            .await()
    ) {
        is MessageAssetResult.Success -> result.decodedAssetPath.toString()
        else -> null
    }

    @AssistedFactory
    interface Factory {
        fun create(conversationId: ConversationId, messageId: String): AudioNormalizedLoudnessWorkerImpl
    }
}
