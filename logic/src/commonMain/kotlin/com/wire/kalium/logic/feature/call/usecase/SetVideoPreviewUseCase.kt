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

package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.FlowManagerService
import com.wire.kalium.logic.util.PlatformView

/**
 * This use case is responsible for setting the video preview on and off, in an ongoing call.
 */
class SetVideoPreviewUseCase internal constructor(private val flowManagerService: FlowManagerService) {

    /**
     * @param conversationId the id of the conversation.
     * @param view the target view to set the video preview on or off.
     */
    suspend operator fun invoke(conversationId: ConversationId, view: PlatformView) {
        flowManagerService.setVideoPreview(conversationId, view)
    }
}
