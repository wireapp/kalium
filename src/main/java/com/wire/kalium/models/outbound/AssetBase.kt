package com.wire.kalium.models.outbound

import com.wire.kalium.tools.Util
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.*

abstract class AssetBase(
        override val messageId: UUID,
        override val mimeType: String,
        val bytes: ByteArray?
) : Asset, GenericMessageIdentifiable {

    override val retention: String = "persistent"
    override val public: Boolean = false
    protected var otrKey: ByteArray = ByteArray(32).also { random.nextBytes(it) }
    open var assetKey: String? = null
        set(value) {
            field = value
        }
    open var assetToken: String? = null
        set(value) {
            field = value
        }
    override val encryptedData: ByteArray =
            ByteArray(16).also { random.nextBytes(it) }.also { Util.encrypt(otrKey, bytes, it) }
    val sha256: ByteArray = MessageDigest.getInstance("SHA-256").digest(encryptedData)

    protected var readReceiptsEnabled = true

    companion object {
        private val random: SecureRandom = SecureRandom()
    }
}
