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

import com.waz.call.FlowManager
import com.waz.log.LogHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.PlatformContext
import com.wire.kalium.logic.util.PlatformView
import com.wire.kalium.util.KaliumDispatcher
import com.wire.kalium.util.KaliumDispatcherImpl
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.async
import kotlinx.coroutines.withContext

actual class FlowManagerServiceImpl(
    appContext: PlatformContext,
    scope: CoroutineScope,
    private val dispatchers: KaliumDispatcher = KaliumDispatcherImpl
) : FlowManagerService {

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    private val flowManager: Deferred<FlowManager> =
        scope.async(
            context = dispatchers.default,
            start = CoroutineStart.LAZY,
        ) {
            FlowManager(
                appContext.context
            ) { manager, path, method, ctype, content, ctx ->
                // TODO(Calling) Not yet implemented
                callingLogger.i("FlowManager -> RequestHandler -> $path : $method")
                0
            }.also {
                it.setEnableLogging(true)
                it.setLogHandler(object : LogHandler {
                    override fun append(msg: String?) {
                        callingLogger.i("FlowManager -> Logger -> Append -> $msg")
                    }

                    override fun upload() {
                        callingLogger.i("FlowManager -> Logger -> upload")
                    }
                })
            }
        }

    override suspend fun setVideoPreview(conversationId: ConversationId, view: PlatformView) {
        withContext(dispatchers.default) {
            flowManager.await().setVideoPreview(conversationId.toString(), view.view)
        }
    }

    override suspend fun flipToFrontCamera(conversationId: ConversationId) {
        withContext(dispatchers.default) {
            flowManager.await().setVideoCaptureDevice(conversationId.toString(), "front")
        }
    }

    override suspend fun flipToBackCamera(conversationId: ConversationId) {
        withContext(dispatchers.default) {
            flowManager.await().setVideoCaptureDevice(conversationId.toString(), "back")
        }
    }

    override suspend fun setUIRotation(rotation: Int) {
        withContext(dispatchers.default) {
            flowManager.await().setUIRotation(rotation)
        }
    }
}
