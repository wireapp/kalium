package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.PlatformView

interface FlowManagerService {
    fun setVideoPreview(conversationId: ConversationId, view: PlatformView)
    fun setUIRotation(rotation: Int)
}

expect class FlowManagerServiceImpl : FlowManagerService
