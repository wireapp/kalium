/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature

import com.wire.kalium.logic.util.PlatformView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertSame

class RemoteVideoViewAttachmentTest {
    @Test
    fun replacingAViewDetachesTheOldViewAndStaleLeaseCannotDetachTheNewOne() {
        val events = mutableListOf<String>()
        val owner = RemoteVideoViewAttachmentOwner(
            attachNative = { _, userId, clientId -> events += "attach:$userId:$clientId"; true },
            detachNative = { _ -> events += "detach"; true },
        )
        val firstView = PlatformView(null)
        val secondView = PlatformView(null)

        val first = owner.attach(firstView, "first@domain", "client-a")
        assertSame(first, owner.attach(firstView, "first@domain", "client-a"))
        val second = owner.attach(secondView, "second@domain", "client-b")
        owner.detach(first)
        owner.detach(second)
        owner.detach(second)

        assertEquals(
            listOf("attach:first@domain:client-a", "detach", "attach:second@domain:client-b", "detach"),
            events,
        )
    }

    @Test
    fun rebindingTheSameViewUpdatesItsStreamIdentity() {
        val events = mutableListOf<String>()
        val view = PlatformView(null)
        val owner = RemoteVideoViewAttachmentOwner(
            attachNative = { _, userId, clientId -> events += "attach:$userId:$clientId"; true },
            detachNative = { _ -> events += "detach"; true },
        )

        owner.attach(view, "first@domain", "client-a")
        owner.attach(view, "first@domain", "client-b")

        assertEquals(listOf("attach:first@domain:client-a", "detach", "attach:first@domain:client-b"), events)
    }
}
