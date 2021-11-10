package com.wire.kalium

import com.wire.bots.cryptobox.CryptoException
import com.wire.kalium.assets.Asset
import com.wire.kalium.assets.GenericMessageIdentifiable
import com.wire.kalium.backend.models.Conversation
import com.wire.kalium.backend.models.NewBot
import com.wire.kalium.backend.models.User
import com.wire.kalium.crypto.Crypto
import com.wire.kalium.exceptions.HttpException
import com.wire.kalium.models.AssetKey
import com.wire.kalium.models.otr.Devices
import com.wire.kalium.models.otr.Missing
import com.wire.kalium.models.otr.OtrMessage
import com.wire.kalium.models.otr.PreKey
import com.wire.kalium.models.otr.Recipients
import com.wire.kalium.tools.Logger
import com.wire.kalium.tools.Util
import java.io.IOException
import java.security.MessageDigest
import java.util.Arrays
import java.util.UUID

abstract class WireClientBase protected constructor(
    private val api: WireAPI,
    private val crypto: Crypto,
    private val state: NewBot
) : WireClient {
    private var devices: Devices = Devices()

    @Throws(Exception::class)
    override fun send(message: GenericMessageIdentifiable) {
        postGenericMessageTargetingUser(message)
    }

    @Throws(Exception::class)
    override fun send(message: GenericMessageIdentifiable, userId: UUID) {
        postGenericMessageTargetingUser(message, userId)
    }

    override fun getId(): UUID {
        return state.id
    }

    override fun getDeviceId(): String {
        return state.client
    }

    override fun getConversationId(): UUID {
        return state.conversation.id
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
        val msg = OtrMessage(getDeviceId(), encrypt)
        val res = api.sendMessage(msg, false)

        if (res.hasMissing()) {
            // Fetch preKeys for the missing devices from the Backend
            handleMissingDevices(encrypt, content, msg)
        }
    }

    private fun handleMissingDevices(
        encrypt: Recipients,
        content: ByteArray,
        message: OtrMessage
    ) {
        var encrypt1 = encrypt
        val preKeys = api.getPreKeys(devices.missing)
        Logger.debug("Fetched %d preKeys for %d devices. Bot: %s", preKeys.count(), devices.size(), getId())

        // Encrypt msg for those devices that were missing. This time using preKeys
        encrypt1 = crypto.encrypt(preKeys, content)
        message.add(encrypt1)

        // reset devices so they could be pulled next time
        devices = Devices()
        devices = api.sendMessage(message, true)
        if (devices.hasMissing()) {
            Logger.error("Failed to send otr message to %d devices. Bot: %s", devices.size(), getId())
        } // FIXME: Silent failure. Missing else?
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
        val message = OtrMessage(getDeviceId(), recipients)
        val res = api.sendPartialMessage(message, userId)
        if (res.hasMissing()) {
            handleMissingDevices(recipients, content, message)
        }
    }

    override fun getSelf(): User {
        return api.getSelf()
    }

    override fun getUsers(userIds: MutableCollection<UUID>): MutableCollection<User> {
        return api.getUsers(userIds)
    }

    override fun getUser(userId: UUID): User {
        val users = api.getUsers(mutableSetOf(userId))
        return users.iterator().next()
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
        return api.getAvailablePrekeys(state.client)
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
    override fun getTeam(): UUID {
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
        if (devices.hasMissing()) {
            val deviceId = getDeviceId()
            val msg = OtrMessage(deviceId, Recipients())
            devices = api.sendMessage(msg)
        }
        return if (devices != null) devices else Devices()
    }
}
