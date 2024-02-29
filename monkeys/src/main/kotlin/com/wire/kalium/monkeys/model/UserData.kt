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
package com.wire.kalium.monkeys.model

import com.wire.kalium.logic.data.user.UserId
import io.ktor.client.HttpClient
import io.ktor.client.plugins.ClientRequestException
import io.ktor.http.HttpStatusCode

data class UserData(
    val email: String,
    val password: String,
    val userId: UserId,
    val team: Team,
    var oldCode: String?
) {
    fun backendConfig() = BackendConfig(
        this.team.backend.api,
        this.team.backend.accounts,
        this.team.backend.webSocket,
        this.team.backend.blackList,
        this.team.backend.teams,
        this.team.backend.website,
        this.team.backend.title,
        this.password,
        this.team.backend.domain,
        this.team.name,
        this.team.backend.authUser,
        this.team.backend.authPassword,
        1u,
        this.team.backend.secondFactorAuthEnabled,
        presetTeam = TeamConfig(
            this.team.id,
            UserAccount(this.team.owner.email, this.team.owner.userId.value),
            users = listOf(UserAccount(this.email, this.userId.value))
        )
    )

    suspend fun request2FA(): String? {
        this.oldCode = if (this.team.backend.secondFactorAuthEnabled) this.team.request2FA(this.email, this.userId, this.oldCode) else null
        return this.oldCode
    }
}

@Suppress("LongParameterList")
class Team(
    val name: String,
    val id: String,
    val backend: Backend,
    ownerEmail: String,
    ownerPassword: String,
    ownerId: UserId,
    ownerOldCode: String?,
    private val client: HttpClient
) {
    val owner: UserData

    init {
        this.owner = UserData(ownerEmail, ownerPassword, ownerId, this, ownerOldCode)
    }

    suspend fun usersFromTeam(): List<UserId> = this.client.teamParticipants(this)

    internal suspend fun request2FA(email: String, userId: UserId, oldCode: String?) = try {
        this.client.request2FA(email, userId)
    } catch (e: ClientRequestException) {
        if (e.response.status == HttpStatusCode.TooManyRequests) {
            oldCode
        } else {
            throw e
        }
    }

    override fun equals(other: Any?): Boolean {
        return other != null && other is Team && other.id == this.id
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }
}

data class Backend(
    val api: String,
    val accounts: String,
    val webSocket: String,
    val blackList: String,
    val teams: String,
    val website: String,
    val title: String,
    val domain: String,
    val secondFactorAuthEnabled: Boolean,
    val authUser: String,
    val authPassword: String,
) {
    companion object {
        fun fromConfig(config: BackendConfig): Backend = with(config) {
            Backend(api, accounts, webSocket, blackList, teams, website, title, domain, !forceDisable2fa && secondFactorAuth, authUser, authPassword)
        }
    }
}
