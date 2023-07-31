/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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
package com.wire.kalium.logic.util.arrangement.dao

import com.wire.kalium.persistence.dao.SyncDAO
import com.wire.kalium.persistence.dao.UserIDEntity
import io.mockative.Mock
import io.mockative.given
import io.mockative.mock

interface SyncDAOArrangement {
    @Mock
    val syncDAO: SyncDAO

    fun withAllOtherUsersIdSuccess(
        result: List<UserIDEntity>,
    ) {
        given(syncDAO)
            .suspendFunction(syncDAO::allOtherUsersId)
            .whenInvoked()
            .then { result }
    }

    fun withAllOtherUsersIdFail(
        result: Throwable,
    ) {
        given(syncDAO)
            .suspendFunction(syncDAO::allOtherUsersId)
            .whenInvoked()
            .thenThrow(result)
    }
}

class SyncDAOArrangementImpl : SyncDAOArrangement {
    @Mock
    override val syncDAO: SyncDAO = mock(SyncDAO::class)
}
