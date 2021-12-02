package com.wire.kalium.models.outbound

import com.wire.kalium.tools.Util
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.UUID

abstract class AssetBase(
    override val messageId: UUID,
    override val mimeType: String,
    val bytes: ByteArray?
) : Asset, GenericMessageIdentifiable {

    override val retention: String = "persistent"
    override val public: Boolean = false
    protected var otrKey: ByteArray = ByteArray(32).also { random.nextBytes(it) }
    var assetKey: String? = null
    var assetToken: String? = null
    final override var encryptedData: ByteArray
    val sha256: ByteArray
        get() = MessageDigest.getInstance("SHA-256").digest(encryptedData)

    protected var readReceiptsEnabled = true

    companion object {
        private val random: SecureRandom = SecureRandom()
    }

    init {
        val iv = ByteArray(16)
        random.nextBytes(iv)

        encryptedData = Util.encrypt(otrKey, bytes, iv)
    }
}
