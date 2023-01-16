package com.wire.kalium.logic.feature.call.usecase

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.feature.call.FlowManagerService
import com.wire.kalium.logic.util.PlatformView
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.withContext

/**
 * This use case is responsible for setting the video preview on and off, in an ongoing call.
 */
class SetVideoPreviewUseCase internal constructor(
    private val flowManagerService: FlowManagerService,
    private val dispatcher: KaliumDispatcher = KaliumDispatcherImpl
) {

    /**
     * @param conversationId the id of the conversation.
     * @param view the target view to set the video preview on or off.
     */
    suspend operator fun invoke(conversationId: ConversationId, view: PlatformView) = withContext(dispatcher.default) {
        flowManagerService.setVideoPreview(conversationId, view)
    }
}
