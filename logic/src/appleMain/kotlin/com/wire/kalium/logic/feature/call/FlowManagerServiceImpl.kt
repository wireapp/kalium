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

package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.logic.util.PlatformView

actual class FlowManagerServiceImpl(
    appContext: PlatformContext
) : FlowManagerService {
    override suspend fun setVideoPreview(conversationId: ConversationId, view: PlatformView) {
        TODO("Not yet implemented")
    }

    override suspend fun flipToFrontCamera(conversationId: ConversationId) {
        TODO("Not yet implemented")
    }

    override suspend fun flipToBackCamera(conversationId: ConversationId) {
        TODO("Not yet implemented")
    }

    override suspend fun setUIRotation(rotation: Int) {
        TODO("Not yet implemented")
    }

    override suspend fun startFlowManager() {
        TODO("Not yet implemented")
    }
}
