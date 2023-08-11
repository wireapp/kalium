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
package com.wire.kalium.monkeys.importer

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
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromStream
import kotlinx.serialization.json.jsonPrimitive
import java.io.File
import java.net.URLEncoder
import java.util.UUID

private const val TEAM_ROLE: String = "member"

private val tokenStorage = mutableListOf<BearerTokens>()

private data class Team(val id: String, val backend: Backend, val owner: UserData)

object TestDataImporter {
    @OptIn(ExperimentalSerializationApi::class)
    fun importFromFile(path: String): TestData {
        val file = File(path)
        logger.d("Importing test data from ${file.absolutePath}")
        return Json.decodeFromStream<TestData>(
            file.inputStream()
        )
    }

    suspend fun generateUserData(testData: TestData): List<UserData> {
        return testData.backends.flatMap { backendConfig ->
            val httpClient = basicHttpClient(backendConfig)
            val team = httpClient.createTeam(backendConfig, backendConfig.passwordForUsers)
            (1..backendConfig.userCount.toInt()).map { httpClient.createUser(it, team, backendConfig.passwordForUsers) }
                .also { httpClient.close() }
        }
    }

}

private suspend fun HttpClient.createTeam(backendConfig: BackendConfig, ownerPassword: String): Team {
    val faker = Faker()
    val ownerName = faker.name.name()
    val ownerId = "monkey-owner-" + UUID.randomUUID().toString()
    val email = "$ownerId@wire.com"
    logger.i("Owner $email in team ${backendConfig.teamName} in backend ${backendConfig.domain}")
    post("activate/send") {
        setBody(
            mapOf(
                "email" to email
            )
        )
    }

    val code =
        get("i/users/activation-code?email=${URLEncoder.encode(email, "utf-8")}").body<JsonObject>()["code"]?.jsonPrimitive?.content
            ?: error("Failed to get activation code for owner")
    val response = post("register") {
        setBody(
            mapOf(
                "email" to email,
                "name" to ownerName,
                "password" to ownerPassword,
                "email_code" to code,
                "team" to mapOf(
                    "name" to backendConfig.teamName,
                    "icon" to "default",
                    "binding" to true
                )
            ).toJsonObject()
        )
    }.body<JsonObject>()
    val teamId = response["team"]?.jsonPrimitive?.content ?: error("Could not create team.")
    put("i/teams/$teamId/features/mls") {
        setBody(
            mapOf(
                "config" to mapOf(
                    "allowedCipherSuites" to listOf(1),
                    "defaultCipherSuite" to 1,
                    "defaultProtocol" to "proteus",
                    "protocolToggleUsers" to listOf<String>(),
                    "supportedProtocols" to listOf("mls", "proteus")
                ),
                "status" to "enabled"
            ).toJsonObject()
        )
    }
    val backend = with(backendConfig) {
        Backend(
            api,
            accounts,
            webSocket,
            blackList,
            teams,
            website,
            title,
            domain,
            teamName
        )
    }
    val userId = response["id"]?.jsonPrimitive?.content ?: error("Could not register user")
    val result = Team(teamId, backend, UserData(email, ownerPassword, UserId(userId, backend.domain), backend))
    login(result)
    put("self/handle") {
        setBody(mapOf("handle" to ownerId).toJsonObject())
    }
    return result
}

private suspend fun HttpClient.createUser(i: Int, team: Team, userPassword: String): UserData {
    val faker = Faker()
    val userName = faker.name.name()
    val userId = "monkey-user-$i-${team.id}"
    val email = "$userId@wire.com"
    val invitationCode = invite(team.id, email, userName)
    val response = post("register") {
        setBody(
            mapOf(
                "email" to email,
                "name" to userName,
                "password" to userPassword,
                "team_code" to invitationCode
            ).toJsonObject()
        )
    }.body<JsonObject>()
    // needs to login with the newly created user
//     println(put("self/handle") {
//         setBody(mapOf("handle" to userId).toJsonObject())
//     })
    val newUserId = response["id"]?.jsonPrimitive?.content ?: error("Could not register user in team")
    logger.d("Created user $email in team ${team.backend.teamName}")
    return UserData(email, userPassword, UserId(newUserId, team.backend.domain), team.backend)
}

private suspend fun HttpClient.login(team: Team) {
    val response = post("login") {
        setBody(
            mapOf(
                "email" to team.owner.email,
                "password" to team.owner.password,
                "label" to ""
            ).toJsonObject()
        )
    }.body<JsonObject>()
    val accessToken = response["access_token"]?.jsonPrimitive?.content ?: error("Could not login")
    tokenStorage.add(BearerTokens(accessToken, ""))
}

private suspend fun HttpClient.invite(teamId: String, email: String, name: String): String {
    val invitationId = post("teams/$teamId/invitations") {
        setBody(
            mapOf(
                "email" to email,
                "role" to TEAM_ROLE,
                "inviter_name" to name
            ).toJsonObject()
        )
    }.body<JsonObject>()["id"]?.jsonPrimitive?.content ?: error("Could not invite new user")
    return get("i/teams/invitation-code?team=$teamId&invitation_id=$invitationId").body<JsonObject>()["code"]?.jsonPrimitive?.content
        ?: error("Could not retrieve user invitation")
}

private fun basicHttpClient(backendConfig: BackendConfig) = HttpClient(OkHttp.create()) {
    val excludedPaths = listOf("register", "login", "activate")
    defaultRequest {
        url(backendConfig.api)
        contentType(ContentType.Application.Json)
        accept(ContentType.Application.Json)
    }
    expectSuccess = true
    install(KaliumKtorCustomLogging)
    install(UserAgent) {
        agent = "Infinite Monkeys"
    }

    install(ContentNegotiation) {
        json(KtxSerializer.json)
    }

    install(Auth) {
        bearer {
            sendWithoutRequest {
                it.url.pathSegments.isNotEmpty() && it.url.pathSegments[0] != "i" && !excludedPaths.contains(it.url.pathSegments[0])
            }
            loadTokens {
                tokenStorage.last()
            }
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
