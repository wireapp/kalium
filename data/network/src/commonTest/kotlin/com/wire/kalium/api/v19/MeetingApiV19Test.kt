/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.api.v19

import com.wire.kalium.api.ApiTest
import com.wire.kalium.mocks.responses.conversation.ConversationResponseJson
import com.wire.kalium.network.api.authenticated.meeting.UpsertMeetingResponse
import com.wire.kalium.network.api.authenticated.meeting.RotateMeetingLinkRequest
import com.wire.kalium.network.api.model.MeetingId
import com.wire.kalium.network.api.v18.authenticated.MeetingApiV18
import com.wire.kalium.network.api.v19.authenticated.MeetingApiV19
import com.wire.kalium.network.exceptions.APINotSupported
import com.wire.kalium.network.utils.NetworkResponse
import io.ktor.http.HttpStatusCode
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertContains
import kotlin.test.assertIs
import kotlin.test.fail

internal class MeetingApiV19Test : ApiTest() {
    @Test
    fun givenPassword_whenRotatingLink_thenPostsPasswordToQualifiedMeetingPath() = runTest {
        val client = mockAuthenticatedNetworkClient(meetingResponse(hasPassword = true), HttpStatusCode.OK, assertion = {
            assertPost()
            assertPathEqual("/meetings/wire.test/meeting-id/link/refresh")
            assertAuthorizationHeaderExist()
            assertNoQueryParams()
            assertJsonBodyContent("""{"password":"Secret123!"}""")
        })

        val response = assertIs<NetworkResponse.Success<UpsertMeetingResponse>>(
            MeetingApiV19(client).rotateMeetingLink(MEETING_ID, RotateMeetingLinkRequest("Secret123!"))
        )
        assertEquals("https://wire.test/new-link", response.value.link)
        assertEquals(true, response.value.hasPassword)
    }

    @Test
    fun givenNoPassword_whenRotatingLink_thenPostsEmptyObject() = runTest {
        val client = mockAuthenticatedNetworkClient(meetingResponse(hasPassword = false), HttpStatusCode.OK, assertion = {
            assertPost()
            assertJsonBodyContent("{}")
        })

        val response = assertIs<NetworkResponse.Success<UpsertMeetingResponse>>(
            MeetingApiV19(client).rotateMeetingLink(MEETING_ID, RotateMeetingLinkRequest())
        )
        assertEquals("https://wire.test/new-link", response.value.link)
        assertEquals(false, response.value.hasPassword)
    }

    @Test
    fun givenApiV18_whenRotatingLink_thenDoesNotSendRequest() = runTest {
        val client = mockAuthenticatedNetworkClient("", HttpStatusCode.OK, assertion = {
            fail("Link refresh requires API v19")
        })
        val error = assertIs<NetworkResponse.Error>(
            MeetingApiV18(client).rotateMeetingLink(MEETING_ID, RotateMeetingLinkRequest())
        )
        assertContains(assertIs<APINotSupported>(error.kException).errorBody, "API V19")
    }

    private fun meetingResponse(hasPassword: Boolean) = """
        {"qualified_id":{"id":"meeting-id","domain":"wire.test"},
         "qualified_conversation":{"id":"conversation","domain":"wire.test"},
         "qualified_creator":{"id":"creator","domain":"wire.test"},
         "created_at":"2026-11-13T08:00:00Z","updated_at":null,"title":"Meeting",
         "start_time":"2026-11-13T10:00:00Z","end_time":"2026-11-13T11:00:00Z","recurrence":null,
         "conversation":${ConversationResponseJson.v3.rawJson},
         "link":"https://wire.test/new-link","has_password":$hasPassword}
    """.trimIndent()

    private companion object {
        val MEETING_ID = MeetingId("meeting-id", "wire.test")
    }
}
