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
     * A user on the same backend,
     * when current user doesn't belongs to any team
     */
    NONE;
}

sealed class UserTypeInfo {
    data class Regular(val type: UserType) : UserTypeInfo()
    data object App : UserTypeInfo()
    data object Bot : UserTypeInfo()
}

fun UserTypeInfo.isOwner(): Boolean = this is UserTypeInfo.Regular && this.type == UserType.OWNER

/**
 * Checks if the user is an App or Bot, including legacy bots.
 */
fun UserTypeInfo.isAppOrBot(): Boolean = this is UserTypeInfo.App || this is UserTypeInfo.Bot

fun UserTypeInfo.isTeamAdmin(): Boolean = this is UserTypeInfo.Regular && (this.type == UserType.ADMIN || this.type == UserType.OWNER)

fun UserTypeInfo.isExternal(): Boolean = this is UserTypeInfo.Regular && this.type == UserType.EXTERNAL

fun UserTypeInfo.isGuest(): Boolean = this is UserTypeInfo.Regular && this.type == UserType.GUEST

fun UserTypeInfo.isFederated(): Boolean = this is UserTypeInfo.Regular && this.type == UserType.FEDERATED

fun UserTypeInfo.isInternal(): Boolean = this is UserTypeInfo.Regular && this.type == UserType.INTERNAL

/**
 * Checks that the user is a regular team member, excluding external members and service accounts.
 */
fun UserTypeInfo.isRegularTeamMember(): Boolean = this.isTeamAdmin() || this.isInternal()

/**
 * Checks that the user is part of the entire team, including external members and service accounts.
 * [isRegularTeamMember] + [isAppOrBot] + [UserType.EXTERNAL]
 */
fun UserTypeInfo.isTeammate(): Boolean =
    isRegularTeamMember() || isAppOrBot() || (this is UserTypeInfo.Regular && type == UserType.EXTERNAL)
