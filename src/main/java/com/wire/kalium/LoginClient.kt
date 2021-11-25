//
// Wire
// Copyright (C) 2016 Wire Swiss GmbH
//
// This program is free software: you can redistribute it and/or modify
// it under the terms of the GNU General Public License as published by
// the Free Software Foundation, either version 3 of the License, or
// (at your option) any later version.
//
// This program is distributed in the hope that it will be useful,
// but WITHOUT ANY WARRANTY; without even the implied warranty of
// MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
// GNU General Public License for more details.
//
// You should have received a copy of the GNU General Public License
// along with this program. If not, see http://www.gnu.org/licenses/.
//
package com.wire.kalium

import com.wire.kalium.api.user.client.LocationResponse
import com.wire.kalium.exceptions.AuthException
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.models.backend.Access
import com.wire.kalium.models.backend.NewClient
import com.wire.kalium.models.backend.NotificationList
import com.wire.kalium.models.outbound.otr.PreKey
import com.wire.kalium.models.system.Cookie
import com.wire.kalium.tools.KtxSerializer
import com.wire.kalium.tools.Logger
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import java.util.*
import javax.ws.rs.client.Client
import javax.ws.rs.client.Entity
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.NewCookie
import javax.ws.rs.core.Response

open class LoginClient(client: Client) {
    @JvmField
    protected val clientsPath: WebTarget
    private val loginPath: WebTarget
    private val accessPath: WebTarget
    private val cookiesPath: WebTarget
    private val notificationsPath: WebTarget
    fun host(): String {
        val host = System.getenv("WIRE_API_HOST")
        return host ?: "https://prod-nginz-https.wire.com"
    }

    @JvmOverloads
    @Throws(HttpException::class)
    fun login(email: String, password: String, persisted: Boolean = false): Access {
        val login = LoginRequestBody(email = email, password = password, label =  LABEL)

        val loginRequestJson = KtxSerializer.json.encodeToString(login)
        val response: Response = loginPath.queryParam("persist", persisted)
                .request(MediaType.APPLICATION_JSON)
                .post(Entity.json(loginRequestJson))

        println(response.date)

        val status: Int = response.status
        if (status == 401) {   //todo nginx returns text/html for 401. Cannot deserialize as json
            response.readEntity(String::class.java)
            throw AuthException(message = null, code = status)
        }
        if (status == 403) {
            val entity: String = response.readEntity(String::class.java)
            Logger.error(entity)
            //throw AuthException(message = entity, code = status)
        }
        if (status >= 400) {
            val entity: String = response.readEntity(String::class.java)
            throw AuthException(message = entity, code = status)
        }
        val entity: String = response.readEntity(String::class.java)

        val access = KtxSerializer.json.decodeFromString<Access>(entity)

        Logger.error(entity)
        Logger.error(access.toString())

        val zuid: NewCookie? = response.cookies[COOKIE_NAME]
        if (zuid != null) {
            access.cookie = Cookie(name = zuid.name, value = zuid.value)
        }
        return access
    }

    @Throws(HttpException::class)
    fun registerClient(token: String, password: String, preKeys: ArrayList<PreKey>, lastKey: PreKey): String? {
        val deviceClass = "tablet"
        val type = "temporary"
        return registerClient(token, password, preKeys, lastKey, deviceClass, type, LABEL)
    }

    /**
     * @param password Wire password
     * @param clazz    "tablet" | "phone" | "desktop"
     * @param type     "permanent" | "temporary"
     * @param label    can be anything
     * @return Client id
     */
    @Throws(HttpException::class)
    fun registerClient(token: String, password: String, preKeys: ArrayList<PreKey>, lastKey: PreKey,
                       clazz: String, type: String, label: String): String? {
        val newClient = NewClient(
                password = password,
                lastkey = lastKey,
                prekeys = preKeys,
                clazz = clazz,
                label = label,
                type = type
        )

        // FIXME: why are sigkeys deleted from NewClient class
        //newClient.sigkeys.enckey = Base64.getEncoder().encodeToString(ByteArray(32))
        //newClient.sigkeys.mackey = Base64.getEncoder().encodeToString(ByteArray(32))
        val newClientJson = KtxSerializer.json.encodeToString(newClient)
        val response: Response = clientsPath
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.json(newClientJson))
                //.post(Entity.entity(newClient, MediaType.APPLICATION_JSON))
        println(response)
        val status: Int = response.status
        if (status == 401) {   //todo nginx returns text/html for 401. Cannot deserialize as json
            val entity = response.readEntity(String::class.java)
            throw AuthException(message = entity, code = status)
        } else if (status >= 400) {
            Logger.error(response.readEntity(String::class.java))
            //throw response.readEntity(HttpException::class.java)
        }

        val responseJson = response.readEntity(String::class.java)
        val _clientResponse = KtxSerializer.json.decodeFromString<_Client>(responseJson)

        return _clientResponse.id
    }

    @Throws(HttpException::class)
    fun renewAccessToken(cookie: Cookie): Access {
        val cookie1 = javax.ws.rs.core.Cookie(cookie.name, cookie.value)
        val builder: Invocation.Builder = accessPath
                .request(MediaType.APPLICATION_JSON)
                .cookie(cookie1)
        val response: Response = builder.post(Entity.entity(null, MediaType.APPLICATION_JSON))
        val status: Int = response.getStatus()
        if (status == 401) {   //todo nginx returns text/html for 401. Cannot deserialize as json
            response.readEntity(String::class.java)
            throw AuthException(code = status)
        } else if (status == 403) {
            throw response.readEntity(AuthException::class.java)
        } else if (status >= 400) {
            throw response.readEntity(HttpException::class.java)
        }
        val access: Access = response.readEntity(Access::class.java)

        //TODO uncomment this
        val zuid: NewCookie? = response.cookies[COOKIE_NAME]
        if (zuid != null) {
            access.cookie = Cookie(name = zuid.name, value = zuid.value)
        }
        return access
    }

    @Throws(HttpException::class)
    fun logout(cookie: javax.ws.rs.core.Cookie, token: String) {
        val response: Response = accessPath
                .path("logout")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .cookie(cookie)
                .post(Entity.entity(null, MediaType.APPLICATION_JSON))
        val status: Int = response.getStatus()
        if (status == 401) {   //todo nginx returns text/html for 401. Cannot deserialize as json
            response.readEntity(String::class.java)
            throw AuthException(code = status)
        } else if (status == 403) {
            throw response.readEntity(AuthException::class.java)
        } else if (status >= 400) {
            throw response.readEntity(HttpException::class.java)
        }
    }

    @Throws(HttpException::class)
    fun removeCookies(token: String, password: String?) {
        val removeCookies = _RemoveCookies()
        removeCookies.password = password
        removeCookies.labels = listOf(LABEL)
        val response: Response = cookiesPath.request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.entity(removeCookies, MediaType.APPLICATION_JSON))
        val status: Int = response.status
        if (status == 401) {   //todo nginx returns text/html for 401. Cannot deserialize as json
            response.readEntity(String::class.java)
            throw AuthException(code = status)
        } else if (status >= 400) {
            throw response.readEntity(HttpException::class.java)
        }
    }

    @Throws(HttpException::class)
    fun retrieveNotifications(clientId: String, since: UUID?, token: String, size: Int): NotificationList {
        var webTarget: WebTarget = notificationsPath
                .queryParam("client", clientId)
                .queryParam("size", size)
        if (since != null) {
            webTarget = webTarget
                    .queryParam("since", since.toString())
        }
        val response: Response = webTarget
                .request(MediaType.APPLICATION_JSON)
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .get()
        val status: Int = response.getStatus()
        if (status == 200) {
            val content = response.readEntity(String::class.java)
            return KtxSerializer.json.decodeFromString(content)
        } else if (status == 404) {  //todo what???
            return response.readEntity(NotificationList::class.java)
        } else if (status == 401) {   //todo nginx returns text/html for 401. Cannot deserialize as json
            response.readEntity(String::class.java)
            throw AuthException(code = status)
        } else if (status == 403) {
            throw response.readEntity(AuthException::class.java)
        }
        throw response.readEntity(HttpException::class.java)
    }

    @Serializable
    internal data class LoginRequestBody (
            val email: String,
            val password: String,
            val label: String
    )

    @Serializable
    internal class _Client {
        var id: String? = null
        var time: String? = null
        val location: LocationResponse? = null
        val type: String? = null
        @SerialName("class") var clazz: String? = null
        var label: String? = null
        var capabilities: _Capabilities? = null
    }

    @Serializable
    internal class _Capabilities {
        var capabilities: List<String>? = null
    }

    @Serializable
    internal class _RemoveCookies {
        var password: String? = null
        var labels: List<String>? = null
    }

    companion object {
        private const val LABEL = "wbots"
        private const val COOKIE_NAME = "zuid"

        @JvmStatic
        fun bearer(token: String): String {
            return "Bearer $token"
        }
    }

    init {
        val host = host()
        loginPath = client
                .target(host)
                .path("login")
        clientsPath = client
                .target(host)
                .path("clients")
        accessPath = client
                .target(host)
                .path("access")
        cookiesPath = client
                .target(host)
                .path("cookies")
        notificationsPath = client
                .target(host)
                .path("notifications")
    }
}
