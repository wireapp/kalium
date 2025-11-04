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
package com.wire.kalium.logic.feature.asset

import com.waz.audioeffect.AudioEffect
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder.Companion.MAX_SIZE
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder.Companion.MAX_VALUE
import com.wire.kalium.logic.feature.asset.AudioNormalizedLoudnessBuilder.Companion.TAG
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

internal actual class AudioNormalizedLoudnessBuilderImpl(
    private val dispatcher: CoroutineDispatcher,
    private val audioEffect: AudioEffect,
) : AudioNormalizedLoudnessBuilder {
    /**
     * Note: This UseCase can't be tested as we cannot mock `AudioEffect` from AVS.
     * Generates audio normalized loudness [ByteArray] for the given file path.
     *
     * @param filePath the path to the audio file.
     *@return [ByteArray] representing the normalized loudness.
     */
    actual override suspend operator fun invoke(filePath: String): ByteArray? = withContext(dispatcher) {
        kaliumLogger.i("[$TAG] -> Start generating audio waves mask")
        audioEffect.amplitudeGenerate(filePath, MAX_VALUE, MAX_SIZE)?.let {
            it.map { it.toByte() }.toByteArray() // Convert IntArray to ByteArray
        }.also {
            if (it == null) {
                kaliumLogger.w("[$TAG] -> There was an issue with generating audio normalized loudness.")
            } else {
                kaliumLogger.i("[$TAG] -> Audio normalized loudness generated successfully.")
            }
        }
    }
}
