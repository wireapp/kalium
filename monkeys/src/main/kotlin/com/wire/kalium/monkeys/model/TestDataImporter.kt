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
import com.wire.kalium.monkeys.logger
import com.wire.kalium.network.KaliumKtorCustomLogging
import com.wire.kalium.network.tools.KtxSerializer
import com.wire.kalium.util.serialization.toJsonObject
import io.github.serpro69.kfaker.Faker
import io.ktor.client.HttpClient
import io.ktor.client.call.body
import io.ktor.client.engine.okhttp.OkHttp
import io.ktor.client.plugins.UserAgent
import io.ktor.client.plugins.auth.Auth
import io.ktor.client.plugins.auth.providers.BasicAuthCredentials
import io.ktor.client.plugins.auth.providers.BearerTokens
import io.ktor.client.plugins.auth.providers.basic
import io.ktor.client.plugins.auth.providers.bearer
import io.ktor.client.plugins.contentnegotiation.ContentNegotiation
import io.ktor.client.plugins.defaultRequest
import io.ktor.client.request.accept
import io.ktor.client.request.get
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.http.ContentType
import io.ktor.http.contentType
import io.ktor.serialization.kotlinx.json.json
import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder
import java.util.UUID

private const val TEAM_ROLE: String = "member"

private var token: BearerTokens? = null

object TestDataImporter {
    @OptIn(ExperimentalSerializationApi::class)
    fun importFromFile(path: String): TestData {
        val file = File(path)
        logger.d("Importing test data from ${file.absolutePath}")
        return Json.decodeFromStream<TestData>(
            file.inputStream()
        )
    }

    private val jsonSerializer: Json by lazy {
        Json {
            prettyPrint = true
        }
    }

    private fun dumpUsers(team: Team, users: List<UserData>) {
        val json = mapOf("id" to team.id, "owner" to mapOf(
            "email" to team.owner.email, "id" to team.owner.userId.value
        ), "users" to users.map {
            mapOf(
                "email" to it.email, "id" to it.userId.value
            )
        }).toJsonObject()
        File("${team.name}.json").writeText(jsonSerializer.encodeToString(json))
    }

    suspend fun generateUserData(backends: List<BackendConfig>): List<UserData> {
        return backends.flatMap { backendConfig ->
            val httpClient = httpClient(backendConfig)
            if (backendConfig.presetTeam != null) {
                val backend = Backend.fromConfig(backendConfig)
                val team = Team(
                    backendConfig.teamName,
                    backendConfig.presetTeam.id,
                    backend,
                    backendConfig.presetTeam.owner.email,
                    backendConfig.passwordForUsers,
                    UserId(backendConfig.presetTeam.owner.unqualifiedId, backendConfig.domain),
                    null,
                    httpClient
                )
                backendConfig.presetTeam.users.map { user ->
                    UserData(
                        user.email, backendConfig.passwordForUsers, UserId(user.unqualifiedId, backendConfig.domain), team, null
                    )
                }
            } else {
                val team = httpClient.createTeam(backendConfig)
                val users = (1..backendConfig.userCount.toInt()).map { httpClient.createUser(it, team, backendConfig.passwordForUsers) }
                if (backendConfig.dumpUsers) {
                    dumpUsers(team, users.plus(team.owner))
                }
                users.forEach { setUserHandle(backendConfig, it) }
                users.plus(team.owner)
            }
        }
    }
}

private suspend fun HttpClient.createTeam(backendConfig: BackendConfig): Team {
    val faker = Faker()
    val ownerName = faker.name.name()
    val ownerId = "monkey-owner-" + UUID.randomUUID().toString()
    val email = "$ownerId@wire.com"
    post("activate/send") { setBody(mapOf("email" to email)) }

    val code = get("i/users/activation-code?email=${URLEncoder.encode(email, "utf-8")}").body<JsonObject>()["code"]?.jsonPrimitive?.content
        ?: error("Failed to get activation code for owner")
    val user = post("register") {
        setBody(
            mapOf(
                "email" to email, "name" to ownerName, "password" to backendConfig.passwordForUsers, "email_code" to code, "team" to mapOf(
                    "name" to backendConfig.teamName, "icon" to "default", "binding" to true
                )
            ).toJsonObject()
        )
    }.body<JsonObject>()
    val teamId = user["team"]?.jsonPrimitive?.content ?: error("Could not create team.")
    put("i/teams/$teamId/features/mls") {
        setBody(
            mapOf(
                "config" to mapOf(
                    "allowedCipherSuites" to listOf(1),
                    "defaultCipherSuite" to 1,
                    "defaultProtocol" to "proteus",
                    "protocolToggleUsers" to listOf<String>(),
                    "supportedProtocols" to listOf("mls", "proteus")
                ), "status" to "enabled"
            ).toJsonObject()
        )
    }
    if (backendConfig.forceDisable2fa) {
        put("i/teams/$teamId/features/sndFactorPasswordChallenge/unlocked")
        put("i/teams/$teamId/features/sndFactorPasswordChallenge") {
            setBody(
                mapOf(
                    "status" to "disabled", "ttl" to "unlimited"
                ).toJsonObject()
            )
        }
    }

    val backend = Backend.fromConfig(backendConfig)
    val userId = user["id"]?.jsonPrimitive?.content ?: error("Could not register user")
    val team = Team(
        name = backendConfig.teamName,
        id = teamId,
        backend = backend,
        ownerEmail = email,
        ownerPassword = backendConfig.passwordForUsers,
        ownerId = UserId(userId, backend.domain),
        ownerOldCode = null,
        client = this
    )
    login(team.owner.email, team.owner.password, team.owner.request2FA())

    put("self/handle") { setBody(mapOf("handle" to ownerId).toJsonObject()) }
    logger.i("Owner $email (id $userId) of team ${backendConfig.teamName} (id: $teamId) in backend ${backendConfig.domain}")
    return team
}

private suspend fun setUserHandle(backendConfig: BackendConfig, userData: UserData) {
    lateinit var token: BearerTokens
    val httpClient = httpClient(backendConfig) { token }
    val secondFactor = if (backendConfig.secondFactorAuth) httpClient.request2FA(userData.email, userData.userId) else null
    httpClient.login(userData.email, userData.password, secondFactor) { accessToken ->
        token = BearerTokens(accessToken, "")
    }
    userData.oldCode = secondFactor
    httpClient.put("self/handle") { setBody(mapOf("handle" to "monkey-${userData.userId.value}").toJsonObject()) }
}

private suspend fun HttpClient.createUser(i: Int, team: Team, userPassword: String): UserData {
    val faker = Faker()
    val userName = faker.name.name()
    val email = "monkey-user-$i-${team.id}@${team.name}.wire.com"
    val invitationCode = invite(team.id, email, userName)
    val response = post("register") {
        setBody(
            mapOf(
                "email" to email, "name" to userName, "password" to userPassword, "team_code" to invitationCode
            ).toJsonObject()
        )
    }.body<JsonObject>()
    val userId = response["id"]?.jsonPrimitive?.content ?: error("Could not register user in team")
    logger.d("Created user $email (id $userId) in team ${team.name}")
    return UserData(email, userPassword, UserId(userId, team.backend.domain), team, null)
}

private suspend fun HttpClient.login(
    email: String,
    password: String,
    secondFactor: String?,
    tokenProvider: (accessToken: String) -> Unit = { accessToken ->
        token = BearerTokens(accessToken, "")
    }
) {
    val response = post("login") {
        setBody(
            mapOf(
                "email" to email, "password" to password, "label" to "", "verification_code" to secondFactor
            ).toJsonObject()
        )
    }.body<JsonObject>()
    val accessToken = response["access_token"]?.jsonPrimitive?.content ?: error("Could not login")
    tokenProvider(accessToken)
}

internal suspend fun HttpClient.teamParticipants(team: Team): List<UserId> {
    this.login(team.owner.email, team.owner.password, team.owner.request2FA())
    val members = get("teams/${team.id}/members").body<JsonObject>()
    return members["members"]!!.jsonArray.map { member ->
        UserId(member.jsonObject["user"]!!.jsonPrimitive.content, team.backend.domain)
    }
}

suspend fun HttpClient.request2FA(email: String, userId: UserId): String {
    this.post("verification-code/send") {
        setBody(
            mapOf(
                "action" to "login", "email" to email
            ).toJsonObject()
        )
    }
    return this.get("i/users/${userId.value}/verification-code/login").body<JsonPrimitive>().content
}

private suspend fun HttpClient.invite(teamId: String, email: String, name: String): String {
    val invitationId = post("teams/$teamId/invitations") {
        setBody(
            mapOf(
                "email" to email, "role" to TEAM_ROLE, "inviter_name" to name
            ).toJsonObject()
        )
    }.body<JsonObject>()["id"]?.jsonPrimitive?.content ?: error("Could not invite new user")
    return get("i/teams/invitation-code?team=$teamId&invitation_id=$invitationId").body<JsonObject>()["code"]?.jsonPrimitive?.content
        ?: error("Could not retrieve user invitation")
}

internal fun httpClient(backendConfig: BackendConfig, tokenProvider: () -> BearerTokens? = { token }) = HttpClient(OkHttp.create()) {
    val excludedPaths = listOf("verification-code", "register", "login", "activate")
    defaultRequest {
        url(backendConfig.api)
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json)
    }
    expectSuccess = true
    install(KaliumKtorCustomLogging)
    install(UserAgent) {
        agent = "Wire Infinite Monkeys"
    }

    install(ContentNegotiation) {
        json(KtxSerializer.json)
    }

    install(Auth) {
        bearer {
            sendWithoutRequest {
                it.url.pathSegments.isNotEmpty() && it.url.pathSegments[0] != "i" && !excludedPaths.contains(it.url.pathSegments[0])
            }
            loadTokens(tokenProvider)
        }
        basic {
            sendWithoutRequest {
                it.url.pathSegments.isNotEmpty() && it.url.pathSegments[0] == "i" && !excludedPaths.contains(it.url.pathSegments[0])
            }
            credentials {
                BasicAuthCredentials(username = backendConfig.authUser, password = backendConfig.authPassword)
            }
        }
    }
}
