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
package com.wire.kalium.network.api.v16.authenticated

import com.wire.kalium.network.AuthenticatedNetworkClient
import com.wire.kalium.network.api.authenticated.meeting.UpsertMeetingRequest
import com.wire.kalium.network.api.authenticated.meeting.UpsertMeetingResponse
import com.wire.kalium.network.api.authenticated.meeting.MeetingDTO
import com.wire.kalium.network.api.model.MeetingId
import com.wire.kalium.network.api.v15.authenticated.MeetingApiV15
import com.wire.kalium.network.utils.NetworkResponse
import com.wire.kalium.network.utils.wrapRequest
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody

internal open class MeetingApiV16 internal constructor(
    private val authenticatedNetworkClient: AuthenticatedNetworkClient
) : MeetingApiV15() {

    protected val httpClient get() = authenticatedNetworkClient.httpClient

    override suspend fun fetchMeetings(): NetworkResponse<List<MeetingDTO>> = wrapRequest {
        httpClient.get("$PATH_MEETINGS/$PATH_LIST")
    }

    override suspend fun deleteMeeting(meetingId: MeetingId): NetworkResponse<Unit> = wrapRequest {
        httpClient.delete("$PATH_MEETINGS/${meetingId.domain}/${meetingId.value}")
    }

    override suspend fun createNewMeeting(request: UpsertMeetingRequest): NetworkResponse<UpsertMeetingResponse> = wrapRequest {
        httpClient.post(PATH_MEETINGS) {
            setBody(request)
        }
    }

    override suspend fun updateMeeting(meetingId: MeetingId, request: UpsertMeetingRequest): NetworkResponse<UpsertMeetingResponse> =
        wrapRequest {
            httpClient.put("$PATH_MEETINGS/${meetingId.domain}/${meetingId.value}") {
                setBody(request)
            }
        }

    companion object {
        const val PATH_MEETINGS = "meetings"
        const val PATH_LIST = "list"
    }
}
