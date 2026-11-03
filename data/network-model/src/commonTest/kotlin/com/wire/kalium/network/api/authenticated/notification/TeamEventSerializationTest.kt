/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.network.api.authenticated.notification

import com.wire.kalium.network.api.authenticated.notification.team.TeamMemberIdData
import com.wire.kalium.network.tools.KtxSerializer
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.time.Instant

class TeamEventSerializationTest {
    @Test
    fun memberJoinPreservesMemberTeamAndTime() {
        val event = assertIs<EventContentDTO.Team.MemberJoin>(
            KtxSerializer.json.decodeFromString<EventContentDTO>(
                """
                {
                    "type":"team.member-join",
                    "team":"team-id",
                    "time":"$TIME",
                    "data":{"user":"member-id"}
                }
                """.trimIndent()
            )
        )

        assertEquals(TeamMemberIdData("member-id"), event.teamMember)
        assertEquals("team-id", event.teamId)
        assertEquals(TIME, event.time)
    }

    private companion object {
        val TIME = Instant.parse("2026-10-28T12:00:00Z")
    }
}
