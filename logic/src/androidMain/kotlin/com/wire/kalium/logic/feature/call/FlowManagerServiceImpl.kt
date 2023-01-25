/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import android.content.Context
import com.waz.call.FlowManager
import com.waz.log.LogHandler
import com.wire.kalium.logic.callingLogger
import com.wire.kalium.logic.data.id.ConversationId
import com.wire.kalium.logic.util.PlatformView
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.withContext

actual class FlowManagerServiceImpl(
    appContext: Context,
    private val dispatcher: CoroutineDispatcher
) : FlowManagerService {

    @Suppress("UNUSED_ANONYMOUS_PARAMETER")
    private val flowManager: FlowManager = FlowManager(
        appContext
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

    override suspend fun setVideoPreview(conversationId: ConversationId, view: PlatformView) {
        withContext(dispatcher) {
            flowManager.setVideoPreview(conversationId.toString(), view.view)
        }
    }

    override fun setUIRotation(rotation: Int) {
        flowManager.setUIRotation(rotation)
    }
}
