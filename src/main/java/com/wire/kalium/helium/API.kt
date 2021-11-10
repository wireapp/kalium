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
package com.wire.helium

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.wire.xenon.WireAPI
import com.wire.xenon.assets.IAsset
import com.wire.xenon.backend.models.Conversation
import com.wire.xenon.backend.models.Member
import com.wire.xenon.backend.models.Service
import com.wire.xenon.backend.models.User
import com.wire.xenon.exceptions.HttpException
import com.wire.xenon.models.AssetKey
import com.wire.xenon.models.otr.*
import com.wire.xenon.tools.Util
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors
import javax.ws.rs.client.Client
import javax.ws.rs.client.Entity
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.GenericType
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

open class API(client: Client, convId: UUID?, token: String) : LoginClient(client), WireAPI {
    private val conversationsPath: WebTarget
    private val usersPath: WebTarget
    private val assetsPath: WebTarget
    private val teamsPath: WebTarget
    private val connectionsPath: WebTarget
    private val selfPath: WebTarget
    private val token: String
    private val convId: String?

    @Throws(HttpException::class)
    open fun sendMessage(msg: OtrMessage?, vararg ignoreMissing: Any?): Devices? {
        val response: Response = conversationsPath.path(convId).path("otr/messages").queryParam("ignore_missing", ignoreMissing).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).post(Entity.entity(msg, MediaType.APPLICATION_JSON))
        val statusCode: Int = response.getStatus()
        if (statusCode == 412) {
            return response.readEntity(Devices::class.java)
        } else if (statusCode >= 400) {
            throw HttpException(response.getStatusInfo().getReasonPhrase(), response.getStatus())
        }
        response.close()
        return Devices()
    }

    @Throws(HttpException::class)
    fun sendPartialMessage(msg: OtrMessage?, userId: UUID?): Devices {
        val response: Response = conversationsPath.path(convId).path("otr/messages").queryParam("report_missing", userId).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).post(Entity.entity(msg, MediaType.APPLICATION_JSON))
        val statusCode: Int = response.getStatus()
        if (statusCode == 412) {
            return response.readEntity(Devices::class.java)
        } else if (statusCode >= 400) {
            throw HttpException(response.getStatusInfo().getReasonPhrase(), response.getStatus())
        }
        response.close()
        return Devices()
    }

    open fun getPreKeys(missing: Missing): PreKeys? {
        return if (missing.isEmpty()) PreKeys() else usersPath.path("prekeys").request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).accept(MediaType.APPLICATION_JSON).post(Entity.entity(missing, MediaType.APPLICATION_JSON), PreKeys::class.java)
    }

    @Throws(HttpException::class)
    fun downloadAsset(assetKey: String?, assetToken: String?): ByteArray {
        val req: Invocation.Builder = assetsPath
                .path(assetKey)
                .queryParam("access_token", token)
                .request()
        if (assetToken != null) req.header("Asset-Token", assetToken)
        val response: Response = req.get()
        if (response.getStatus() >= 400) {
            val log = java.lang.String.format("%s. AssetId: %s", response.readEntity(String::class.java), assetKey)
            throw HttpException(log, response.getStatus())
        }
        return response.readEntity(ByteArray::class.java)
    }

    @Throws(HttpException::class)
    fun acceptConnection(user: UUID) {
        val connection: com.wire.helium.models.Connection = com.wire.helium.models.Connection()
        connection.setStatus("accepted")
        val response: Response = connectionsPath.path(user.toString()).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).put(Entity.entity(connection, MediaType.APPLICATION_JSON))
        if (response.getStatus() >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.getStatus())
        }
        response.close()
    }

    @Throws(Exception::class)
    fun uploadAsset(asset: IAsset): AssetKey {
        val sb = StringBuilder()

        // Part 1
        val strMetadata = java.lang.String.format("{\"public\": %s, \"retention\": \"%s\"}",
                asset.isPublic(),
                asset.getRetention())
        sb.append("--frontier\r\n")
        sb.append("Content-Type: application/json; charset=utf-8\r\n")
        sb.append("Content-Length: ")
                .append(strMetadata.length)
                .append("\r\n\r\n")
        sb.append(strMetadata)
                .append("\r\n")

        // Part 2
        sb.append("--frontier\r\n")
        sb.append("Content-Type: ")
                .append(asset.getMimeType())
                .append("\r\n")
        sb.append("Content-Length: ")
                .append(asset.getEncryptedData().length)
                .append("\r\n")
        sb.append("Content-MD5: ")
                .append(Util.calcMd5(asset.getEncryptedData()))
                .append("\r\n\r\n")

        // Complete
        val os = ByteArrayOutputStream()
        os.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        os.write(asset.getEncryptedData())
        os.write("\r\n--frontier--\r\n".toByteArray(StandardCharsets.UTF_8))
        val response: Response = assetsPath
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.entity(os.toByteArray(), "multipart/mixed; boundary=frontier"))
        return response.readEntity(AssetKey::class.java)
    }

    val conversation: Conversation
        get() {
            val conv: _Conv = conversationsPath.path(convId).request().header(HttpHeaders.AUTHORIZATION, bearer(token)).get(_Conv::class.java)
            val ret = Conversation()
            ret.name = conv.name
            ret.id = conv.id
            ret.members = conv.members!!.others
            return ret
        }

    @Throws(HttpException::class)
    fun deleteConversation(teamId: UUID): Boolean {
        val response: Response = teamsPath.path(teamId.toString()).path("conversations").path(convId).request().header(HttpHeaders.AUTHORIZATION, bearer(token)).delete()
        if (response.getStatus() >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.getStatus())
        }
        return response.getStatus() === 200
    }

    @Throws(HttpException::class)
    fun addService(serviceId: UUID, providerId: UUID): User {
        val service = _Service()
        service.service = serviceId
        service.provider = providerId
        val response: Response = conversationsPath.path(convId).path("bots").request().accept(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).post(Entity.entity(service, MediaType.APPLICATION_JSON))
        if (response.getStatus() >= 400) {
            val msg: String = response.readEntity(String::class.java)
            throw HttpException(msg, response.getStatus())
        }
        val user: User = response.readEntity(User::class.java)
        user.service = Service()
        user.service.id = serviceId
        user.service.providerId = providerId
        return user
    }

    @Throws(HttpException::class)
    fun addParticipants(vararg userIds: UUID?): User {
        val newConv = _NewConv()
        newConv.users = Arrays.asList(*userIds)
        val response: Response = conversationsPath.path(convId).path("members").request().header(HttpHeaders.AUTHORIZATION, bearer(token)).post(Entity.entity(newConv, MediaType.APPLICATION_JSON))
        if (response.getStatus() >= 400) {
            val msg: String = response.readEntity(String::class.java)
            throw HttpException(msg, response.getStatus())
        }
        return response.readEntity(User::class.java)
    }

    @Throws(HttpException::class)
    fun createConversation(name: String?, teamId: UUID?, users: List<UUID>?): Conversation {
        val newConv = _NewConv()
        newConv.name = name
        newConv.users = users
        if (teamId != null) {
            newConv.team = _TeamInfo()
            newConv.team!!.teamId = teamId
        }
        val response: Response = conversationsPath.request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).post(Entity.entity(newConv, MediaType.APPLICATION_JSON))
        if (response.getStatus() >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.getStatus())
        }
        val conv: _Conv = response.readEntity(_Conv::class.java)
        val ret = Conversation()
        ret.name = conv.name
        ret.id = conv.id
        ret.members = conv.members!!.others
        return ret
    }

    @Throws(HttpException::class)
    fun createOne2One(teamId: UUID?, userId: UUID): Conversation {
        val newConv = _NewConv()
        newConv.users = listOf(userId)
        if (teamId != null) {
            newConv.team = _TeamInfo()
            newConv.team!!.teamId = teamId
        }
        val response: Response = conversationsPath
                .path("one2one")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.entity(newConv, MediaType.APPLICATION_JSON))
        if (response.getStatus() >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.getStatus())
        }
        val conv: _Conv = response.readEntity(_Conv::class.java)
        val ret = Conversation()
        ret.name = conv.name
        ret.id = conv.id
        ret.members = conv.members!!.others
        return ret
    }

    @Throws(HttpException::class)
    fun leaveConversation(user: UUID) {
        val response: Response = conversationsPath
                .path(convId)
                .path("members")
                .path(user.toString())
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .delete()
        if (response.getStatus() >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.getStatus())
        }
    }

    fun uploadPreKeys(preKeys: ArrayList<PreKey?>?) {
        usersPath.path("prekeys").request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).accept(MediaType.APPLICATION_JSON).post(Entity.entity(preKeys, MediaType.APPLICATION_JSON))
    }

    fun getAvailablePrekeys(clientId: String?): ArrayList<Int> {
        return clientsPath.path(clientId).path("prekeys").request().header(HttpHeaders.AUTHORIZATION, bearer(token)).accept(MediaType.APPLICATION_JSON).get(object : GenericType() {})
    }

    fun getUsers(ids: Collection<UUID?>): Collection<User> {
        return usersPath.queryParam("ids", ids.toTypedArray()).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).get(object : GenericType() {})
    }

    @Throws(HttpException::class)
    fun getUser(userId: UUID): User {
        val response: Response = usersPath.path(userId.toString()).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).get()
        if (response.getStatus() !== 200) {
            throw HttpException(response.readEntity(String::class.java), response.getStatus())
        }
        return response.readEntity(User::class.java)
    }

    @Throws(HttpException::class)
    fun getUserId(handle: String?): UUID? {
        val response: Response = usersPath.path("handles").path(handle).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).get()
        if (response.getStatus() !== 200) {
            throw HttpException(response.readEntity(String::class.java), response.getStatus())
        }
        val teamMember: _TeamMember = response.readEntity(_TeamMember::class.java)
        return teamMember.user
    }

    fun hasDevice(userId: UUID, clientId: String?): Boolean {
        val response: Response = usersPath.path(userId.toString()).path("clients").path(clientId).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).get()
        response.close()
        return response.getStatus() === 200
    }

    val self: User
        get() = selfPath.request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).get(User::class.java)

    @get:Throws(HttpException::class)
    val team: UUID?
        get() {
            val response: Response = teamsPath
                    .request(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .get()
            if (response.getStatus() !== 200) {
                throw HttpException(response.readEntity(String::class.java), response.getStatus())
            }
            val teams: _Teams = response.readEntity(_Teams::class.java)
            return if (teams.teams!!.isEmpty()) null else teams.teams!![0].id
        }

    fun getTeamMembers(teamId: UUID): Collection<UUID?> {
        val team: _Team = teamsPath
                .path(teamId.toString())
                .path("members")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .get(_Team::class.java)
        return team.members!!.stream().map { x: _TeamMember -> x.user }.collect(Collectors.toList())
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class _Conv {
        @JsonProperty
        var id: UUID? = null

        @JsonProperty
        var name: String? = null

        @JsonProperty
        var members: _Members? = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    class _Members {
        @JsonProperty
        var others: List<Member>? = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal class _Service {
        var service: UUID? = null
        var provider: UUID? = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal class _Team {
        @JsonProperty
        var id: UUID? = null

        @JsonProperty
        var name: String? = null

        @JsonProperty
        var members: List<_TeamMember>? = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal class _TeamMember {
        @JsonProperty
        var user: UUID? = null
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    internal class _Teams {
        @JsonProperty
        var teams: ArrayList<_Team>? = null
    }

    internal class _NewConv {
        @JsonProperty
        var name: String? = null

        @JsonProperty
        var team: _TeamInfo? = null

        @JsonProperty
        var users: List<UUID>? = null

        @JsonProperty
        var service: _Service? = null
    }

    internal class _TeamInfo {
        @JsonProperty("teamid")
        var teamId: UUID? = null

        @JsonProperty
        var managed = false
    }

    internal class _Device {
        @JsonProperty("id")
        var clientId: String? = null

        @JsonProperty("class")
        var type: String? = null
    }

    init {
        this.convId = convId?.toString()
        this.token = token
        val target: WebTarget = client
                .target(host())
        conversationsPath = target.path("conversations")
        usersPath = target.path("users")
        assetsPath = target.path("assets/v3")
        teamsPath = target.path("teams")
        connectionsPath = target.path("connections")
        selfPath = target.path("self")
    }
}