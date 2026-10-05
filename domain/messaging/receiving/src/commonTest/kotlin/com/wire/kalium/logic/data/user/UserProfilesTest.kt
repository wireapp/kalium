/* Wire, Copyright (C) 2026 Wire Swiss GmbH, SPDX-License-Identifier: GPL-3.0-or-later */
package com.wire.kalium.logic.data.user

import com.wire.kalium.logic.data.id.toDao
import com.wire.kalium.network.api.model.AppDTO
import com.wire.kalium.network.api.model.LegalHoldStatusDTO
import com.wire.kalium.network.api.model.UserProfileDTO
import com.wire.kalium.network.api.model.UserTypeDTO
import com.wire.kalium.persistence.TestUserDatabase
import com.wire.kalium.persistence.dao.AppEntity
import com.wire.kalium.persistence.dao.UserTypeEntity
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserProfilesTest {
    @Test
    fun appsOutsideTeamMemberListArePersistedWithTheirUserProfiles() = runTest {
        val selfId = UserId("self", "example.com")
        val database = TestUserDatabase(selfId.toDao(), StandardTestDispatcher(testScheduler))
        try {
            val internal = profile("internal", "self-team")
            val external = profile("external", "other-team")
            val regular = profile("regular", "self-team").copy(type = UserTypeDTO.REGULAR, app = null)
            val profiles = userProfiles(listOf(internal, external, regular), emptyList(), selfId, "self-team")

            profiles.persist(database.builder.userDAO, database.builder.appDAO)

            for (app in listOf(internal, external)) {
                assertEquals(
                    AppEntity(app.id.toDao(), app.name, "description", "category", app.teamId, null, null),
                    database.builder.appDAO.byId(app.id.toDao())
                )
                assertEquals(UserTypeEntity.APP, database.builder.userDAO.getUserDetailsByQualifiedID(app.id.toDao())?.userType)
            }
            assertNull(database.builder.appDAO.byId(regular.id.toDao()))

            val updated = external.copy(name = "Updated app")
            userProfiles(listOf(updated), emptyList(), selfId, "self-team")
                .persist(database.builder.userDAO, database.builder.appDAO)
            assertEquals(updated.name, database.builder.appDAO.byId(updated.id.toDao())?.name)
        } finally {
            database.builder.close()
            database.delete()
        }
    }

    private fun profile(id: String, team: String) = UserProfileDTO(
        id = com.wire.kalium.network.api.model.UserId(id, "example.com"),
        name = id,
        handle = null,
        teamId = team,
        accentId = 0,
        assets = emptyList(),
        deleted = false,
        email = null,
        expiresAt = null,
        nonQualifiedId = id,
        service = null,
        supportedProtocols = null,
        legalHoldStatus = LegalHoldStatusDTO.DISABLED,
        type = UserTypeDTO.APP,
        app = AppDTO("description", "category")
    )
}
