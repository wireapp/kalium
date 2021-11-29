package com.wire.kalium

import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.models.backend.Conversation
import com.wire.kalium.models.backend.User
import com.wire.kalium.models.inbound.AssetKey
import com.wire.kalium.models.outbound.Asset
import com.wire.kalium.models.outbound.otr.Devices
import com.wire.kalium.models.outbound.otr.Missing
import com.wire.kalium.models.outbound.otr.OtrMessage
import com.wire.kalium.models.outbound.otr.PreKey
import com.wire.kalium.models.outbound.otr.PreKeys
import java.io.IOException
import java.util.*

interface IWireAPI {
    @Throws(HttpException::class)
    open fun sendMessage(msg: OtrMessage, flag: Boolean): Devices

    @Throws(HttpException::class)
    open fun sendPartialMessage(msg: OtrMessage, userId: UUID): Devices
    open fun getUsers(ids: MutableCollection<UUID>): MutableCollection<User>
    open fun getSelf(): User
    open fun getConversation(): Conversation
    open fun getPreKeys(missing: Missing): PreKeys
    open fun getAvailablePrekeys(client: String): ArrayList<Int>

    @Throws(IOException::class)
    open fun uploadPreKeys(preKeys: ArrayList<PreKey>)
    @Throws(Exception::class)
    open fun uploadAsset(asset: Asset): AssetKey
    @Throws(HttpException::class)
    open fun downloadAsset(assetId: String, assetToken: String?): ByteArray
    @Throws(HttpException::class)
    open fun deleteConversation(teamId: UUID): Boolean
    @Throws(HttpException::class)
    open fun addService(serviceId: UUID, providerId: UUID): User

    @Throws(HttpException::class)
    open fun addParticipants(vararg userIds: UUID): User

    @Throws(HttpException::class)
    open fun createConversation(name: String, teamId: UUID?, users: MutableList<UUID>): Conversation

    @Throws(HttpException::class)
    open fun createOne2One(teamId: UUID?, userId: UUID): Conversation

    @Throws(HttpException::class)
    open fun leaveConversation(user: UUID)
    @Throws(HttpException::class)
    open fun getUser(userId: UUID): User
    @Throws(HttpException::class)
    open fun getUserId(handle: String): UUID
    open fun hasDevice(userId: UUID, clientId: String): Boolean

    @Throws(HttpException::class)
    open fun getTeam(): UUID?
    open fun getTeamMembers(teamId: UUID): MutableCollection<UUID>
    @Throws(Exception::class)
    open fun acceptConnection(user: UUID)
}
