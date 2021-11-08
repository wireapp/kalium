package com.wire.kalium.assets

import java.util.UUID
import java.security.MessageDigest
import com.wire.kalium.tools.Util
import java.security.SecureRandom

abstract class AssetBase(protected var messageId: UUID?, protected val mimeType: String?) : IAsset, IGeneric {
    protected var encBytes: ByteArray?
    protected var otrKey: ByteArray?
    protected var assetKey: String? = null
    protected var assetToken: String? = null
    protected var sha256: ByteArray?
    protected var retention: String? = "persistent"
    protected var readReceiptsEnabled = true

    constructor(messageId: UUID?, mimeType: String?, bytes: ByteArray?) : this(messageId, mimeType) {
        otrKey = ByteArray(32)
        random.nextBytes(otrKey)
        val iv = ByteArray(16)
        random.nextBytes(iv)
        encBytes = Util.encrypt(otrKey, bytes, iv)
        sha256 = MessageDigest.getInstance("SHA-256").digest(encBytes)
    }

    override fun isPublic(): Boolean {
        return false
    }

    override fun getMessageId(): UUID? {
        return messageId
    }

    fun setMessageId(messageId: UUID?) {
        this.messageId = messageId
    }

    fun getAssetKey(): String? {
        return assetKey
    }

    fun setAssetKey(assetKey: String?) {
        this.assetKey = assetKey
    }

    fun getAssetToken(): String? {
        return assetToken
    }

    fun setAssetToken(assetToken: String?) {
        this.assetToken = assetToken
    }

    fun isReadReceiptsEnabled(): Boolean {
        return readReceiptsEnabled
    }

    fun setReadReceiptsEnabled(readReceiptsEnabled: Boolean) {
        this.readReceiptsEnabled = readReceiptsEnabled
    }

    fun getOtrKey(): ByteArray? {
        return otrKey
    }

    fun setOtrKey(otrKey: ByteArray?) {
        this.otrKey = otrKey
    }

    fun getSha256(): ByteArray? {
        return sha256
    }

    fun setSha256(sha256: ByteArray?) {
        this.sha256 = sha256
    }

    companion object {
        private val random: SecureRandom? = SecureRandom()
    }
}
