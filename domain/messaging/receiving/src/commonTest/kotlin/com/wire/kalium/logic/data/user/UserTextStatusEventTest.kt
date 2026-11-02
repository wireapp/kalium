/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.event.userUpdate
import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.network.api.authenticated.notification.EventContentDTO
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.ConnectionEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserTextStatusEventTest {
    @Test
    fun profileRefreshAndLiveEventsAgreeOnStatusAndExplicitClears() = runTest {
        val selfId = UserId("self", "example.com")
        val otherId = UserId("other", selfId.domain)
        val database = TestUserDatabase(selfId.toDao(), StandardTestDispatcher(testScheduler))
        try {
            val profile = UserProfileDTO(
                id = com.wire.kalium.network.api.model.UserId(otherId.value, otherId.domain),
                name = "Other", handle = null, teamId = "team", accentId = 0, assets = emptyList(), deleted = false,
                email = null, expiresAt = null, nonQualifiedId = otherId.value, service = null, supportedProtocols = null,
                legalHoldStatus = LegalHoldStatusDTO.DISABLED, type = UserTypeDTO.REGULAR, app = null, textStatus = "🎧 In a meeting",
            )
            val users = database.builder.userDAO
            users.upsertUser(profile.toUserEntity(ConnectionEntity.State.ACCEPTED, UserTypeEntity.STANDARD))
            assertEquals(profile.textStatus, users.getUserDetailsByQualifiedID(otherId.toDao())?.textStatus)
            val handler = UserUpdateEventHandler(users)

            suspend fun apply(fields: String) {
                val dto = KtxSerializer.json.decodeFromString<EventContentDTO.User.UpdateDTO>(
                    """{"user":{"id":"other",$fields}}"""
                )
                handler.handle(userUpdate(selfId, "event", dto))
            }

            apply(""""name":"Changed name"""")
            assertEquals(profile.textStatus, users.getUserDetailsByQualifiedID(otherId.toDao())?.textStatus)
            apply(""""text_status":"Out sick"""")
            assertEquals("Out sick", users.getUserDetailsByQualifiedID(otherId.toDao())?.textStatus)
            apply(""""text_status":" """")
            assertNull(users.getUserDetailsByQualifiedID(otherId.toDao())?.textStatus)
            assertNull(profile.copy(textStatus = " ").toUserEntity(ConnectionEntity.State.ACCEPTED, UserTypeEntity.STANDARD).textStatus)
        } finally {
            database.builder.close()
            database.delete()
        }
    }
}
