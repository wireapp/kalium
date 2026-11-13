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

@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)

package com.wire.kalium.calling

import avs.AVSFlowManager
import avs.AVSMediaManager
import avs.WCALL_ENV_DEFAULT
import avs.wcall_init
import avs.wcall_run
import avs.wcall_setup
import platform.Foundation.NSBundle

/**
 * Owns AVS's single process event loop on iOS. The bridge calls [setup] then [run] once, under its start lock.
 *
 * In the app process, AVSFlowManager starts and owns the loop, and wcall only initializes on top of it, as in the
 * native iOS client. Starting wcall's own loop as well makes AVSFlowManager's loop exit at once and drop the
 * process media-system reference. Releasing the last account handle then frees the media system while the media
 * manager's activation observer still uses it.
 *
 * App extensions never create UIKit media objects. They keep wcall's own loop.
 */
internal object AppleAvsPlatformRuntime {
    private val ownsMedia = !NSBundle.mainBundle.bundlePath.endsWith(".appex")
    private var flowManager: AVSFlowManager? = null
    private var mediaManager: AVSMediaManager? = null

    fun setup(): Int = if (ownsMedia) startMediaLoop() else wcall_setup()

    fun run(): Int = if (ownsMedia) wcall_init(WCALL_ENV_DEFAULT) else wcall_run()

    /** The runtime's flow manager, or null when AVS failed to start or this process has no media runtime. */
    fun flowManager(): AVSFlowManager? = if (AppleAvs.bridge.startIfAvailable()) flowManager else null

    fun mediaManager(): AVSMediaManager? = if (AppleAvs.bridge.startIfAvailable()) mediaManager else null

    private fun startMediaLoop(): Int {
        // The flow manager's worker takes the media system before the media manager registers its activation
        // observer. AVS only logs the media manager argument.
        val flow = AVSFlowManager(delegate = null, mediaManager = null)
        flow.setEnableLogging(true)
        flowManager = flow
        mediaManager = AVSMediaManager.defaultMediaManager() ?: return MEDIA_MANAGER_UNAVAILABLE
        return 0
    }

    private const val MEDIA_MANAGER_UNAVAILABLE = -1
}
