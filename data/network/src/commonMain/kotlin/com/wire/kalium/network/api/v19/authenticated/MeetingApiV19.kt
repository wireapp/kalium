/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 * SPDX-License-Identifier: GPL-3.0-or-later
 */
package com.wire.kalium.network.api.v19.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.meeting.RotateMeetingLinkRequest
import com.wire.kalium.network.api.authenticated.meeting.CreateMeetingRequest
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.authenticated.meeting.UpdateMeetingRequest
import com.wire.kalium.network.api.authenticated.meeting.UpsertMeetingResponse
import com.wire.kalium.network.api.model.MeetingId
import com.wire.kalium.network.api.v18.authenticated.MeetingApiV18
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.request.parameter
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.request.url
import io.ktor.http.appendPathSegments

internal class MeetingApiV19 internal constructor(
    authenticatedNetworkClient: AuthenticatedNetworkClient
) : MeetingApiV18(authenticatedNetworkClient) {
    override suspend fun createNewMeeting(request: CreateMeetingRequest): NetworkResponse<UpsertMeetingResponse> = wrapRequest {
        httpClient.post(PATH_MEETINGS) { setBody(request) }
    }

    override suspend fun updateMeeting(meetingId: MeetingId, request: UpdateMeetingRequest): NetworkResponse<UpsertMeetingResponse> =
        wrapRequest {
            httpClient.put("$PATH_MEETINGS/${meetingId.domain}/${meetingId.value}") { setBody(request) }
        }

    override suspend fun rotateMeetingLink(
        meetingId: MeetingId,
        request: RotateMeetingLinkRequest
    ): NetworkResponse<UpsertMeetingResponse> = wrapRequest {
        httpClient.post("$PATH_MEETINGS/${meetingId.domain}/${meetingId.value}/link/refresh") { setBody(request) }
    }

    override suspend fun joinViaCode(domain: String, key: String, code: String, password: String?): NetworkResponse<MeetingDTO> =
        wrapRequest {
            httpClient.post {
                url { appendPathSegments("meeting", domain, key, code, "join", encodeSlash = true) }
                password?.let { parameter("password", it) }
            }
        }
}
