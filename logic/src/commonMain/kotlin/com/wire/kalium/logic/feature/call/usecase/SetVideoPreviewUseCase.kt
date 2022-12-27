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
