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

package com.wire.kalium.logic.feature.call

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AppleCameraControllerTest {
    @Test
    fun selectsRequestedCameraDeviceId() {
        val selections = mutableListOf<Pair<String, String>>()
        val controller = AppleCameraController(
            devices = {
                listOf(
                    AppleCameraDevice("front-id", AppleCameraPosition.FRONT),
                    AppleCameraDevice("back-id", AppleCameraPosition.BACK),
                )
            },
            setCaptureDevice = { deviceId, conversationId -> selections += deviceId to conversationId },
        )

        assertTrue(controller.select(AppleCameraPosition.BACK, "conversation-id"))
        assertEquals(listOf("back-id" to "conversation-id"), selections)
    }

    @Test
    fun ignoresMissingCamera() {
        var selectionCount = 0
        val controller = AppleCameraController(
            devices = { listOf(AppleCameraDevice("front-id", AppleCameraPosition.FRONT)) },
            setCaptureDevice = { _, _ -> selectionCount++ },
        )

        assertFalse(controller.select(AppleCameraPosition.BACK, "conversation-id"))
        assertEquals(0, selectionCount)
    }
}
