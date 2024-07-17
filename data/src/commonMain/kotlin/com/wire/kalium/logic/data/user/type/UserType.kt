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

package com.wire.kalium.logic.data.user.type

enum class UserType {

    /** Team member*/
    INTERNAL,

    ADMIN,

    // TODO add documentation
    OWNER,

    // TODO(user-metadata): for now External will not be implemented
    /**Team member with limited permissions */
    EXTERNAL,

    /**
     * Any user on another backend using the Wire application,
     */
    FEDERATED,

    /**
     * Any user in wire.com using the Wire application or,
     * A temporary user that joined using the guest web interface,
     * from inside the backend network or,
     * A temporary user that joined using the guest web interface,
     * from outside the backend network
     */
    GUEST,

    /**
     * Service bot,
     */
    SERVICE,

    /**
     * A user on the same backend,
     * when current user doesn't belongs to any team
     */
    NONE;
}

fun UserType.isTeammate(): Boolean =
    this in listOf(UserType.INTERNAL, UserType.ADMIN, UserType.OWNER, UserType.EXTERNAL, UserType.SERVICE)

fun UserType.isFederated(): Boolean =
    this == UserType.FEDERATED
