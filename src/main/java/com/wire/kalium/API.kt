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

import com.wire.kalium.exceptions.AuthException
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.models.backend.*
import com.wire.kalium.models.inbound.AssetKey
import com.wire.kalium.models.outbound.Asset
import com.wire.kalium.models.outbound.otr.*
import com.wire.kalium.tools.KtxSerializer
import com.wire.kalium.tools.UUIDSerializer
import com.wire.kalium.tools.Util
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import java.io.ByteArrayOutputStream
import java.nio.charset.StandardCharsets
import java.util.*
import java.util.stream.Collectors
import javax.ws.rs.client.Client
import javax.ws.rs.client.Entity
import javax.ws.rs.client.Invocation
import javax.ws.rs.client.WebTarget
import javax.ws.rs.core.HttpHeaders
import javax.ws.rs.core.MediaType
import javax.ws.rs.core.Response

open class API(client: Client, convId: UUID?, token: String) : LoginClient(client), IWireAPI {
    private val conversationsPath: WebTarget
    private val usersPath: WebTarget
    private val assetsPath: WebTarget
    private val teamsPath: WebTarget
    private val connectionsPath: WebTarget
    private val selfPath: WebTarget
    private val token: String
    private val convId: String?

    @Throws(HttpException::class)
    override fun sendMessage(msg: OtrMessage, vararg ignoreMissing: Any?): Devices {
        val msgJson = KtxSerializer.json.encodeToString(msg)
        val response: Response = conversationsPath.path(convId)
                .path("otr/messages")
                //.queryParam("ignore_missing", ignoreMissing)  //todo re-enable this
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.json(msgJson))

        val statusCode: Int = response.status
        if (statusCode == 412) {
            return response.readEntity(Devices::class.java)
        } else if (statusCode >= 400) {
            throw AuthException(response.statusInfo.reasonPhrase, response.status)
        }
        response.close()
        return Devices()
    }

    @Throws(HttpException::class)
    override fun sendPartialMessage(msg: OtrMessage, userId: UUID): Devices {
        val response: Response = conversationsPath
                .path(convId).path("otr/messages")
                .queryParam("report_missing", userId)
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.entity(msg, MediaType.APPLICATION_JSON))
        val statusCode: Int = response.getStatus()
        if (statusCode == 412) {
            return response.readEntity(Devices::class.java)
        } else if (statusCode >= 400) {
            throw HttpException(response.statusInfo.reasonPhrase, response.status)
        }
        response.close()
        return Devices()
    }

    override fun getPreKeys(missing: Missing): PreKeys {
        return if (missing.isEmpty()) {
            PreKeys()
        } else {
            val response = usersPath.path("prekeys")
                    .request(MediaType.APPLICATION_JSON)
                    .header(HttpHeaders.AUTHORIZATION, bearer(token))
                    .accept(MediaType.APPLICATION_JSON)
                    .post(Entity.entity(missing, MediaType.APPLICATION_JSON))

            return response.readEntity(PreKeys::class.java)
        }
    }

    @Throws(HttpException::class)
    override fun downloadAsset(assetId: String, assetToken: String?): ByteArray {
        val req: Invocation.Builder = assetsPath
                .path(assetId)
                .queryParam("access_token", token)
                .request()
        if (assetToken != null) req.header("Asset-Token", assetToken)
        val response: Response = req.get()
        if (response.status >= 400) {
            val log = java.lang.String.format("%s. AssetId: %s", response.readEntity(String::class.java), assetId)
            throw HttpException(log, response.status)
        }
        return response.readEntity(ByteArray::class.java)
    }

    @Throws(HttpException::class)
    override fun acceptConnection(user: UUID) {
        val connection = Connection(status = "accepted", to = user, from = user, conversation = UUID.fromString(convId))
        val response: Response = connectionsPath
                .path(user.toString())
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .put(Entity.entity(connection, MediaType.APPLICATION_JSON))
        if (response.status >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.status)
        }
        response.close()
    }

    @Throws(Exception::class)
    override fun uploadAsset(asset: Asset): AssetKey {
        val sb = StringBuilder()

        // Part 1
        val strMetadata = java.lang.String.format("{\"public\": %s, \"retention\": \"%s\"}",
                asset.public,
                asset.retention)
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
                .append(asset.mimeType)
                .append("\r\n")
        sb.append("Content-Length: ")
                .append(asset.encryptedData.size)
                .append("\r\n")
        sb.append("Content-MD5: ")
                .append(Util.calcMd5(asset.encryptedData))
                .append("\r\n\r\n")

        // Complete
        val os = ByteArrayOutputStream()
        os.write(sb.toString().toByteArray(StandardCharsets.UTF_8))
        os.write(asset.encryptedData)
        os.write("\r\n--frontier--\r\n".toByteArray(StandardCharsets.UTF_8))
        val response: Response = assetsPath
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.entity(os.toByteArray(), "multipart/mixed; boundary=frontier"))
        return response.readEntity(AssetKey::class.java)
    }

    override fun getConversation(): Conversation {
        val conv: _Conv = conversationsPath.path(convId)
                .request(MediaType.APPLICATION_JSON_TYPE)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .get(_Conv::class.java)
        return Conversation(name = conv.name!!, id = conv.id!!, members = conv.members!!.others!!)
    }

    @Throws(HttpException::class)
    override fun deleteConversation(teamId: UUID): Boolean {
        val response: Response = teamsPath
                .path(teamId.toString())
                .path("conversations")
                .path(convId).request()
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .delete()
        if (response.status >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.status)
        }
        return response.status === 200
    }

    @Throws(HttpException::class)
    override fun addService(serviceId: UUID, providerId: UUID): User {
        val service = _Service()
        service.service = serviceId
        service.provider = providerId
        val response: Response = conversationsPath
                .path(convId)
                .path("bots")
                .request()
                .accept(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.entity(service, MediaType.APPLICATION_JSON))
        if (response.getStatus() >= 400) {
            val msg: String = response.readEntity(String::class.java)
            throw HttpException(msg, response.status)
        }

        // TODO: if the service is in the response why do we need to change it
        val user: User = response.readEntity(User::class.java)
        user.service = Service(id = serviceId, provider = providerId)

        return user
    }

    @Throws(HttpException::class)
    override fun addParticipants(vararg userIds: UUID): User {
        val newConv = _NewConv()
        newConv.users = listOf(*userIds)
        val response: Response = conversationsPath
                .path(convId)
                .path("members")
                .request()
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.entity(newConv, MediaType.APPLICATION_JSON))
        if (response.status >= 400) {
            val msg: String = response.readEntity(String::class.java)
            throw HttpException(msg, response.status)
        }
        return response.readEntity(User::class.java)
    }

    @Throws(HttpException::class)
    override fun createConversation(name: String, teamId: UUID?, users: MutableList<UUID>): Conversation {
        val newConv = _NewConv()
        newConv.name = name
        newConv.users = users
        if (teamId != null) {
            newConv.team = _TeamInfo()
            newConv.team!!.teamId = teamId
        }
        val response: Response = conversationsPath
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .post(Entity.entity(newConv, MediaType.APPLICATION_JSON))
        if (response.status >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.status)
        }
        val conv: _Conv = response.readEntity(_Conv::class.java)
        // TODO: make sure this are not null
        val ret = Conversation(id = conv.id!!, name = conv.name!!, members = conv.members!!.others!!)
        return ret
    }

    @Throws(HttpException::class)
    override fun createOne2One(teamId: UUID?, userId: UUID): Conversation {
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
        if (response.status >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.getStatus())
        }
        val conv: _Conv = response.readEntity(_Conv::class.java)
        val ret = Conversation(id = conv.id!!, name = conv.name!!, members = conv.members!!.others!!)
        return ret
    }

    @Throws(HttpException::class)
    override fun leaveConversation(user: UUID) {
        val response: Response = conversationsPath
                .path(convId)
                .path("members")
                .path(user.toString())
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .delete()
        if (response.status >= 400) {
            throw HttpException(response.readEntity(String::class.java), response.status)
        }
    }

    override fun uploadPreKeys(preKeys: ArrayList<PreKey>) {
        usersPath.path("prekeys").request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).accept(MediaType.APPLICATION_JSON).post(Entity.entity(preKeys, MediaType.APPLICATION_JSON))
    }

    // FIXME: GenericType
    override fun getAvailablePrekeys(client: String): ArrayList<Int> {
        TODO("Fix me")

//        return clientsPath.path(client)
//                .path("prekeys")
//                .request()
//                .header(HttpHeaders.AUTHORIZATION, bearer(token))
//                .accept(MediaType.APPLICATION_JSON)
//                .get(object : GenericType() {})
    }

    override fun getUsers(ids: MutableCollection<UUID>): MutableCollection<User> {
        TODO("Fix me")
//        return usersPath.queryParam("ids", ids.toTypedArray())
//                .request(MediaType.APPLICATION_JSON)
//                .header(HttpHeaders.AUTHORIZATION, bearer(token))
//                .get(object : GenericType() {})
    }

    @Throws(HttpException::class)
    override fun getUser(userId: UUID): User {
        val response: Response = usersPath.path(userId.toString()).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).get()
        if (response.status !== 200) {
            throw HttpException(response.readEntity(String::class.java), response.status)
        }
        return response.readEntity(User::class.java)
    }

    @Throws(HttpException::class)
    override fun getUserId(handle: String): UUID {
        val response: Response = usersPath.path("handles").path(handle).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).get()
        if (response.status !== 200) {
            throw HttpException(response.readEntity(String::class.java), response.getStatus())
        }
        val teamMember: _TeamMember = response.readEntity(_TeamMember::class.java)
        return teamMember.user!!
    }

    override fun hasDevice(userId: UUID, clientId: String): Boolean {
        val response: Response = usersPath.path(userId.toString()).path("clients").path(clientId).request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).get()
        response.close()
        return response.status === 200
    }

    override fun getSelf(): User = selfPath.request(MediaType.APPLICATION_JSON).header(HttpHeaders.AUTHORIZATION, bearer(token)).get(User::class.java)

    @Throws(HttpException::class)
    override fun getTeam(): UUID? {
        val response: Response = teamsPath
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .get()
        if (response.status !== 200) {
            throw HttpException(response.readEntity(String::class.java), response.status)
        }
        val teams: _Teams = response.readEntity(_Teams::class.java)
        return if (teams.teams!!.isEmpty()) null else teams.teams!![0].id
    }


    override fun getTeamMembers(teamId: UUID): MutableCollection<UUID> {
        val team: _Team = teamsPath
                .path(teamId.toString())
                .path("members")
                .request(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, bearer(token))
                .accept(MediaType.APPLICATION_JSON)
                .get(_Team::class.java)
        return team.members!!.stream().map { x: _TeamMember -> x.user }.collect(Collectors.toList())
    }

    //@JsonIgnoreProperties(ignoreUnknown = true)
    @Serializable
    class _Conv {
        //@JsonProperty
        @Serializable(with = UUIDSerializer::class)
        var id: UUID? = null

        //@JsonProperty
        var name: String? = null

        //@JsonProperty
        var members: _Members? = null
    }

    //@JsonIgnoreProperties(ignoreUnknown = true)

    @Serializable
    class _Members {
        //@JsonProperty
        var others: List<ConversationMember>? = null
    }

    //@JsonIgnoreProperties(ignoreUnknown = true)
    @Serializable
    internal class _Service {
        @Serializable(with = UUIDSerializer::class)
        var service: UUID? = null

        @Serializable(with = UUIDSerializer::class)
        var provider: UUID? = null
    }

    //@JsonIgnoreProperties(ignoreUnknown = true)
    @Serializable
    internal class _Team {
        //@JsonProperty
        @Serializable(with = UUIDSerializer::class)
        var id: UUID? = null

        //@JsonProperty
        var name: String? = null

        //@JsonProperty
        var members: List<_TeamMember>? = null
    }

    //JsonIgnoreProperties(ignoreUnknown = true)
    @Serializable
    internal class _TeamMember {
        //@JsonProperty
        @Serializable(with = UUIDSerializer::class)
        var user: UUID? = null
    }

    //@JsonIgnoreProperties(ignoreUnknown = true)
    @Serializable
    internal class _Teams {
        //@JsonProperty
        var teams: ArrayList<_Team>? = null
    }

    @Serializable
    internal class _NewConv {
        //@JsonProperty
        var name: String? = null

        //@JsonProperty
        var team: _TeamInfo? = null

        //@JsonProperty
        var users: List<@Serializable(with = UUIDSerializer::class) UUID>? = null

        //@JsonProperty
        var service: _Service? = null
    }

    @Serializable
    internal class _TeamInfo {
        //@JsonProperty("teamid")
        @Serializable(with = UUIDSerializer::class)
        var teamId: UUID? = null

        //@JsonProperty
        var managed = false
    }

    @Serializable
    internal class _Device {
        //@JsonProperty("id")
        var clientId: String? = null

        //@JsonProperty("class")
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
