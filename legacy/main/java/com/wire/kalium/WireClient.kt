package com.wire.kalium

import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.models.backend.Access
import com.wire.kalium.models.backend.Conversation
import com.wire.kalium.models.backend.User
import com.wire.kalium.models.inbound.AssetKey
import com.wire.kalium.models.outbound.Asset
import com.wire.kalium.models.outbound.GenericMessageIdentifiable
import com.wire.kalium.models.outbound.otr.Devices
import com.wire.kalium.models.outbound.otr.Missing
import com.wire.kalium.models.outbound.otr.OtrMessage
import com.wire.kalium.models.outbound.otr.PreKey
import com.wire.kalium.models.outbound.otr.Recipients
import com.wire.kalium.tools.Util
import java.io.IOException
import java.security.MessageDigest
import java.util.Arrays
import java.util.UUID

class WireClient(
    protected val api: IWireAPI,
    protected val crypto: Crypto,
    protected val access: Access,
    protected val clientId: String

) : IWireClient {
    private var devices: Devices = Devices()

    @Throws(Exception::class)
    override fun send(message: GenericMessageIdentifiable) {
        postGenericMessageTargetingUser(message)
    }

    @Throws(Exception::class)
    override fun send(message: GenericMessageIdentifiable, userId: UUID) {
        postGenericMessageTargetingUser(message, userId)
    }

    override fun getUserId(): UUID {
        return access.user
    }

    @Throws(IOException::class)
    override fun close() {
        crypto.close()
    }

    override fun isClosed(): Boolean {
        return crypto.isClosed()
    }

    /**
     * Encrypt whole message for participants in the conversation.
     * Implements the fallback for the 412 error code and missing
     * devices.
     *
     * @param generic generic message to be sent
     * @throws Exception CryptoBox exception
     */
    @Throws(Exception::class)
    protected fun postGenericMessageTargetingUser(generic: GenericMessageIdentifiable) {
        val content = generic.createGenericMsg().toByteArray()

        // Try to encrypt the msg for those devices that we have the session already
        val encrypt = encrypt(content, getAllDevices())
        val msg = OtrMessage(clientId, encrypt)
        val res = api.sendMessage(msg, false)

        if (res.hasMissing()) {
            // Fetch preKeys for the missing devices from the Backend
            handleMissingDevices(res.missing, content, msg)
        }
    }

    private fun handleMissingDevices(
        missing: Missing,
        content: ByteArray,
        message: OtrMessage
    ) {
        val preKeys = api.getPreKeys(missing)

        // Encrypt msg for those devices that were missing. This time using preKeys
        val encrypt = crypto.encrypt(preKeys, content)
        message.add(encrypt)

        devices = api.sendMessage(message, true)
    }

    @Throws(Exception::class)
    protected fun postGenericMessageTargetingUser(generic: GenericMessageIdentifiable, userId: UUID) {
        // Try to encrypt the msg for those devices that we have the session already
        val allDevices = getAllDevices()
        val missing = Missing()
        allDevices.ofUser(userId)?.forEach { client ->
            missing.add(userId, client)
        }
        val content = generic.createGenericMsg().toByteArray()
        val recipients = encrypt(content, missing)
        val message = OtrMessage(clientId, recipients)
        val res = api.sendPartialMessage(message, userId)
        if (res.hasMissing()) {
            handleMissingDevices(res.missing, content, message)
        }
    }

    override fun getSelf(): User {
        return api.getSelf()
    }

    override fun getUsers(userIds: MutableCollection<UUID>): MutableCollection<User> {
        return api.getUsers(userIds)
    }

    override fun getUser(userId: UUID): User {
        return api.getUser(userId)
    }

    override fun getConversation(): Conversation {
        return api.getConversation()
    }

    @Throws(Exception::class)
    override fun acceptConnection(user: UUID) {
        api.acceptConnection(user)
    }

    @Throws(IOException::class)
    override fun uploadPreKeys(preKeys: ArrayList<PreKey>) {
        api.uploadPreKeys(preKeys)
    }

    override fun getAvailablePrekeys(): ArrayList<Int> {
        return api.getAvailablePrekeys(clientId)
    }

    @Throws(HttpException::class)
    override fun downloadProfilePicture(assetKey: String): ByteArray {
        return api.downloadAsset(assetKey, null)
    }

    @Throws(Exception::class)
    override fun uploadAsset(asset: Asset): AssetKey {
        return api.uploadAsset(asset)
    }

    @Throws(CryptoException::class)
    fun encrypt(content: ByteArray, missing: Missing): Recipients {
        return crypto.encrypt(missing, content)
    }

    @Throws(CryptoException::class)
    fun encrypt(content: ByteArray, missing: HashMap<String, List<String>>): Recipients {
        return crypto.encrypt(missing, content)
    }

    @Throws(CryptoException::class)
    override fun decrypt(userId: UUID, clientId: String, cypher: String): String {
        return crypto.decrypt(userId, clientId, cypher)
    }

    @Throws(CryptoException::class)
    override fun newLastPreKey(): PreKey {
        return crypto.newLastPreKey()
    }

    @Throws(CryptoException::class)
    override fun newPreKeys(from: Int, count: Int): ArrayList<PreKey> {
        return crypto.newPreKeys(from, count)
    }

    @Throws(Exception::class)
    override fun downloadAsset(assetKey: String, assetToken: String, sha256Challenge: ByteArray, otrKey: ByteArray): ByteArray {
        val cipher = api.downloadAsset(assetKey, assetToken)
        val sha256 = MessageDigest.getInstance("SHA-256").digest(cipher)
        if (!Arrays.equals(sha256, sha256Challenge)) throw Exception("Failed sha256 check")
        return Util.decrypt(otrKey, cipher)
    }

    @Throws(HttpException::class)
    override fun getTeam(): UUID? {
        return api.getTeam()
    }

    @Throws(HttpException::class)
    override fun createConversation(name: String, teamId: UUID, users: MutableList<UUID>): Conversation {
        return api.createConversation(name, teamId, users)
    }

    @Throws(HttpException::class)
    override fun createOne2One(teamId: UUID, userId: UUID): Conversation {
        return api.createOne2One(teamId, userId)
    }

    @Throws(HttpException::class)
    override fun leaveConversation(userId: UUID) {
        api.leaveConversation(userId)
    }

    @Throws(HttpException::class)
    override fun addParticipants(vararg userIds: UUID): User {
        return api.addParticipants(*userIds)
    }

    @Throws(HttpException::class)
    override fun addService(serviceId: UUID, providerId: UUID): User {
        return api.addService(serviceId, providerId)
    }

    @Throws(HttpException::class)
    override fun deleteConversation(teamId: UUID): Boolean {
        return api.deleteConversation(teamId)
    }

    @Throws(HttpException::class)
    override fun getUserId(username: String): UUID {
        return api.getUserId(username)
    }

    @Throws(HttpException::class)
    private fun getAllDevices(): Missing {
        return fetchDevices().missing
    }

    /**
     * This method will send an empty message to BE and collect the list of missing client ids
     * When empty message is sent the Backend will respond with error 412 and a list of missing clients.
     *
     * @return List of all participants in this conversation and their clientIds
     */
    @Throws(HttpException::class)
    private fun fetchDevices(): Devices {
        val msg = OtrMessage(clientId, Recipients())
        val devices = api.sendMessage(msg, false)
        return devices
    }
}
