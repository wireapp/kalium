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

package com.wire.kalium.logic.feature.user.linkPreviews

import com.wire.kalium.common.error.CoreFailure
import com.wire.kalium.common.functional.fold
import com.wire.kalium.common.logger.kaliumLogger
import com.wire.kalium.logic.data.properties.UserPropertyRepository
import com.wire.kalium.logger.KaliumLogger.Companion.ApplicationFlow.LOCAL_STORAGE

public interface PersistLinkPreviewsStatusConfigUseCase {
    public suspend operator fun invoke(enabled: Boolean): LinkPreviewsConfigResult
}

internal class PersistLinkPreviewsStatusConfigUseCaseImpl(
    private val userPropertyRepository: UserPropertyRepository,
) : PersistLinkPreviewsStatusConfigUseCase {

    private val logger by lazy { kaliumLogger.withFeatureId(LOCAL_STORAGE) }

    override suspend fun invoke(enabled: Boolean): LinkPreviewsConfigResult {
        val result = if (enabled) {
            userPropertyRepository.setLinkPreviewsEnabled()
        } else {
            userPropertyRepository.deleteLinkPreviewsProperty()
        }

        return result.fold({
            logger.e("Failed trying to update link previews configuration")
            LinkPreviewsConfigResult.Failure(it)
        }) {
            LinkPreviewsConfigResult.Success
        }
    }
}

public sealed class LinkPreviewsConfigResult {
    public data object Success : LinkPreviewsConfigResult()
    public data class Failure(val cause: CoreFailure) : LinkPreviewsConfigResult()
}
