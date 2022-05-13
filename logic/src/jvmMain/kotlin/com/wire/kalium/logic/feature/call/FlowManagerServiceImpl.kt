package com.wire.kalium.logic.feature.call

import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.logic.util.PlatformView

actual class FlowManagerServiceImpl : FlowManagerService {
    override fun setVideoPreview(conversationId: ConversationId, view: PlatformView) {
        kaliumLogger.w("setVideoPreview for JVM but not supported yet.")
    }

    override fun setUIRotation(rotation: Int) {
        kaliumLogger.w("setUIRotation for JVM but not supported yet.")
    }
}
