package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.FlowManagerService
import com.wire.kalium.logic.util.PlatformView

class SetVideoPreviewUseCase(private val flowManagerService: FlowManagerService) {

    operator fun invoke(conversationId: ConversationId, view: PlatformView) {
        flowManagerService.setVideoPreview(conversationId, view)
    }
}
