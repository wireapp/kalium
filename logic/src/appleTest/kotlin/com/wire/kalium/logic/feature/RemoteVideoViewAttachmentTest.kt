/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.feature

import com.wire.kalium.logic.util.PlatformView
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertNotSame
import kotlin.test.assertSame

class RemoteVideoViewAttachmentTest {
    @Test
    fun differentViewsStayAttachedAndDetachIndependently() {
        val fixture = NativeAttachments()
        val firstView = remoteVideoTestView()
        val secondView = remoteVideoTestView()
        val first = fixture.owner.attach(firstView, "first@domain", "client-a")
        val second = fixture.owner.attach(secondView, "second@domain", "client-b")

        assertEquals(listOf(firstView, secondView), fixture.views)
        fixture.owner.detach(first)
        fixture.owner.detach(first)
        assertEquals(listOf(secondView), fixture.views)
        fixture.owner.detach(second)
        fixture.owner.detach(second)

        assertEquals(emptyList(), fixture.views)
        assertEquals(listOf("attach:first@domain:client-a", "attach:second@domain:client-b", "detach", "detach"), fixture.events)
    }

    @Test
    fun differentViewsCanRenderTheSameStream() {
        val fixture = NativeAttachments()
        val first = fixture.owner.attach(remoteVideoTestView(), "first@domain", "client-a")
        val secondView = remoteVideoTestView()
        val second = fixture.owner.attach(secondView, "first@domain", "client-a")

        assertNotSame(first, second)
        assertEquals(2, fixture.views.size)
        fixture.owner.detach(first)
        assertEquals(listOf(secondView), fixture.views)
        fixture.owner.detach(second)
        assertEquals(emptyList(), fixture.views)
    }

    @Test
    fun anotherWrapperForTheSameNativeViewAndStreamReturnsTheSameLease() {
        val fixture = NativeAttachments()
        val view = remoteVideoTestView()
        val first = fixture.owner.attach(view, "first@domain", "client-a")

        assertSame(first, fixture.owner.attach(PlatformView(view.view), "first@domain", "client-a"))
        assertEquals(listOf("attach:first@domain:client-a"), fixture.events)
        fixture.owner.detach(first)
        assertEquals(emptyList(), fixture.views)
    }

    @Test
    fun rebindingTheSameNativeViewInvalidatesItsPreviousLeaseOnly() {
        val fixture = NativeAttachments()
        val view = remoteVideoTestView()
        val old = fixture.owner.attach(view, "first@domain", "client-a")
        val otherView = remoteVideoTestView()
        val other = fixture.owner.attach(otherView, "second@domain", "client-b")
        val replacementView = PlatformView(view.view)
        val replacement = fixture.owner.attach(replacementView, "first@domain", "client-c")

        fixture.owner.detach(old)
        assertEquals(listOf(otherView, replacementView), fixture.views)
        fixture.owner.detach(replacement)
        assertEquals(listOf(otherView), fixture.views)
        fixture.owner.detach(other)
        assertEquals(emptyList(), fixture.views)
        assertEquals(
            listOf("attach:first@domain:client-a", "attach:second@domain:client-b", "detach",
                "attach:first@domain:client-c", "detach", "detach"),
            fixture.events,
        )
    }

    @Test
    fun rebindingTheSameViewDistinguishesQualifiedUsers() {
        val fixture = NativeAttachments()
        val view = remoteVideoTestView()
        val old = fixture.owner.attach(view, "first@domain-a", "client-a")
        val replacement = fixture.owner.attach(view, "first@domain-b", "client-a")

        assertNotSame(old, replacement)
        fixture.owner.detach(old)
        assertEquals(listOf(view), fixture.views)
        assertEquals(listOf("attach:first@domain-a:client-a", "detach", "attach:first@domain-b:client-a"), fixture.events)
    }

    @Test
    fun failedAttachLeavesOtherViewsAttachedAndCanBeRetried() {
        val fixture = NativeAttachments()
        val firstView = remoteVideoTestView()
        val first = fixture.owner.attach(firstView, "first@domain", "client-a")
        val secondView = remoteVideoTestView()
        fixture.attachSucceeds = false

        assertFailsWith<IllegalStateException> { fixture.owner.attach(secondView, "second@domain", "client-b") }
        assertEquals(listOf(firstView), fixture.views)
        fixture.attachSucceeds = true
        val second = fixture.owner.attach(secondView, "second@domain", "client-b")
        assertEquals(listOf(firstView, secondView), fixture.views)
        fixture.owner.detach(second)
        assertEquals(listOf(firstView), fixture.views)
        fixture.owner.detach(first)
        assertEquals(emptyList(), fixture.views)
    }

    @Test
    fun failedDetachRetainsItsLeaseWithoutBlockingOtherViews() {
        val fixture = NativeAttachments()
        val firstView = remoteVideoTestView()
        val first = fixture.owner.attach(firstView, "first@domain", "client-a")
        val secondView = remoteVideoTestView()
        val second = fixture.owner.attach(secondView, "second@domain", "client-b")
        fixture.failedDetachView = firstView

        assertFailsWith<IllegalStateException> { fixture.owner.detach(first) }
        assertSame(first, fixture.owner.attach(firstView, "first@domain", "client-a"))
        fixture.owner.detach(second)
        assertEquals(listOf(firstView), fixture.views)
        fixture.failedDetachView = null
        fixture.owner.detach(first)
        assertEquals(emptyList(), fixture.views)
    }

    @Test
    fun failedDetachDuringRebindPreservesTheOldLeaseAndStream() {
        val fixture = NativeAttachments()
        val view = remoteVideoTestView()
        val old = fixture.owner.attach(view, "first@domain", "client-a")
        fixture.failedDetachView = view

        assertFailsWith<IllegalStateException> { fixture.owner.attach(view, "second@domain", "client-b") }
        assertSame(old, fixture.owner.attach(view, "first@domain", "client-a"))
        assertEquals(listOf(view), fixture.views)
        assertEquals(listOf("attach:first@domain:client-a", "detach"), fixture.events)
        fixture.failedDetachView = null
        val replacement = fixture.owner.attach(view, "second@domain", "client-b")
        fixture.owner.detach(old)
        assertEquals(listOf(view), fixture.views)
        fixture.owner.detach(replacement)
        assertEquals(emptyList(), fixture.views)
    }

    @Test
    fun failedAttachAfterRebindLeavesOnlyThatViewDetached() {
        val fixture = NativeAttachments()
        val view = remoteVideoTestView()
        val old = fixture.owner.attach(view, "first@domain", "client-a")
        val otherView = remoteVideoTestView()
        fixture.owner.attach(otherView, "second@domain", "client-b")
        fixture.attachSucceeds = false

        assertFailsWith<IllegalStateException> { fixture.owner.attach(view, "first@domain", "client-c") }
        assertEquals(listOf(otherView), fixture.views)
        fixture.attachSucceeds = true
        val replacement = fixture.owner.attach(view, "first@domain", "client-c")
        fixture.owner.detach(old)
        assertEquals(listOf(otherView, view), fixture.views)
        fixture.owner.detach(replacement)
        assertEquals(listOf(otherView), fixture.views)
    }

    private class NativeAttachments {
        val views = mutableListOf<PlatformView>()
        val events = mutableListOf<String>()
        var attachSucceeds = true
        var failedDetachView: PlatformView? = null
        val owner = RemoteVideoViewAttachmentOwner(
            attachNative = { view, userId, clientId ->
                events += "attach:$userId:$clientId"
                if (attachSucceeds) views.add(view)
                attachSucceeds
            },
            detachNative = { view ->
                events += "detach"
                if (view === failedDetachView) {
                    false
                } else {
                    views.remove(view)
                }
            },
        )
    }
}

internal expect fun remoteVideoTestView(): PlatformView
