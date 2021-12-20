package com.wire.kalium.cryptography

import com.wire.kalium.cryptography.exceptions.ProteusException
import com.wire.WireCryptobox.*
import kotlinx.cinterop.ObjCObjectVar
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.allocArrayOf
import kotlinx.cinterop.convert
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import platform.Foundation.NSData
import platform.Foundation.NSDictionary
import platform.Foundation.NSError
import platform.Foundation.NSFileManager
import platform.Foundation.NSMakeRange
import platform.Foundation.NSNumber
import platform.Foundation.NSURL
import platform.Foundation.create
import platform.Foundation.valueForKey
import platform.posix.memcpy

actual class ProteusClient actual constructor(private val rootDir: String, private val userId: String) {

    private var box: EncryptionContext? = null

    @Throws(ProteusException::class)
    actual fun open() {
        val path = "${rootDir}/${userId}"
        NSFileManager.defaultManager.createDirectoryAtPath(path, withIntermediateDirectories = true, null, null)
        box = EncryptionContext(NSURL.fileURLWithPath(path))
    }

    @Throws(ProteusException::class)
    actual fun close() {
        box = null
        // the underlaying cbox is currently closed on deinit so we need to force a GC collection.
        kotlin.native.internal.GC.collect()
    }

    @Throws(ProteusException::class)
    actual fun getIdentity(): ByteArray {
        TODO("Not yet implemented")
    }

    @Throws(ProteusException::class)
    actual fun getLocalFingerprint(): ByteArray {
        lateinit var fingerprint: NSData
        box?.perform { session ->
            fingerprint = session?.localFingerprint()!!
        }
        return toData(fingerprint)
    }

    @Throws(ProteusException::class)
    actual fun newPreKeys(from: Int, count: Int): ArrayList<PreKey> {
        lateinit var preKeys: ArrayList<PreKey>
        box?.perform { session ->
            val range = NSMakeRange(from.convert(), count.convert())
            memScoped {
                val errorPtr: ObjCObjectVar<NSError?> = alloc()
                val rawPreKeys = session?.generatePrekeys(range, errorPtr.ptr)
                errorPtr.value?.let { error ->
                    throw toException(error)
                }
                preKeys = rawPreKeys?.map { bar ->
                    val dict = bar as NSDictionary
                    val id = dict.valueForKey("id") as NSNumber
                    val foo = dict.valueForKey("prekey") as String
                    toPreKey(id.intValue, foo)
                } as ArrayList<PreKey>
            }
            session?.commitCache()
        }
        return preKeys
    }

    @Throws(ProteusException::class)
    actual fun newLastPreKey(): PreKey {
        lateinit var preKey: PreKey
        box?.perform { session ->
            memScoped {
                val errorPtr: ObjCObjectVar<NSError?> = alloc()
                val pk = session?.generateLastPrekeyAndReturnError(errorPtr.ptr)!!
                errorPtr.value?.let { error ->
                    throw toException(error)
                }
                preKey = toPreKey(65535, pk)
            }
            session?.commitCache()
        }
        return preKey
    }

    @Throws(ProteusException::class)
    actual fun createSession(preKey: PreKey, sessionId: CryptoSessionId) {
        box?.perform { session ->
            memScoped {
                val errorPtr: ObjCObjectVar<NSError?> = alloc()
                session?.createClientSession(toSessionId(sessionId), toPreKey(preKey), errorPtr.ptr)
                session?.commitCache() // NOTE necessary since objects are not immediately GC collected.
                errorPtr.value?.let { error ->
                    throw toException(error)
                }
            }

        }
    }

    @Throws(ProteusException::class)
    actual fun decrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray {
        lateinit var decrypted: NSData
        box?.perform { session ->
            if (session?.hasSessionFor(toSessionId(sessionId)) == true) {
                memScoped {
                    val errorPtr: ObjCObjectVar<NSError?> = alloc()
                    decrypted = session.decrypt(toData(message), toSessionId(sessionId), errorPtr.ptr)!!
                    errorPtr.value?.let { error ->
                        throw toException(error)
                    }
                }

            } else {
                memScoped {
                    val errorPtr: ObjCObjectVar<NSError?> = alloc()
                    decrypted = session?.createClientSessionAndReturnPlaintextFor(toSessionId(sessionId), toData(message), errorPtr.ptr)!!
                    errorPtr.value?.let { error ->
                        throw toException(error)
                    }
                }

            }
            session?.commitCache()
        }
        return toData(decrypted)
    }

    @Throws(ProteusException::class)
    actual fun encrypt(message: ByteArray, sessionId: CryptoSessionId): ByteArray? {
        var encrypted: NSData? = null
        box?.perform { session ->
            if (session?.hasSessionFor(toSessionId(sessionId)) == true) {
                memScoped {
                    val errorPtr: ObjCObjectVar<NSError?> = alloc()
                    encrypted = session.encrypt(toData(message), toSessionId(sessionId), errorPtr.ptr)!!
                    errorPtr.value?.let { error ->
                        throw toException(error)
                    }
                }
            }

        }
        return encrypted?.let { toData(it) }
    }

    @Throws(ProteusException::class)
    actual fun encryptWithPreKey(
        message: ByteArray,
        preKey: PreKey,
        sessionId: CryptoSessionId
    ): ByteArray {
        createSession(preKey, sessionId)
        return encrypt(message, sessionId)!!
    }

    companion object {
        private fun toPreKey(preKey: PreKey): String = preKey.encodedData

        private fun toPreKey(id: Int, preKey: String): PreKey =
            PreKey(id, preKey)

        private fun toData(nsData: NSData): ByteArray = ByteArray(nsData.length.toInt()).apply {
            usePinned {
                memcpy(it.addressOf(0), nsData.bytes, nsData.length)
            }
        }

        private fun toData(data: ByteArray): NSData = memScoped {
            NSData.create(bytes = allocArrayOf(data), length = data.size.toULong())
        }

        private fun toSessionId(sessionId: CryptoSessionId): EncryptionSessionIdentifier {
            return EncryptionSessionIdentifier(null, sessionId.userId.value, sessionId.cryptoClientId.value)
        }

        private fun toException(error: NSError): ProteusException {
            return ProteusException(message = error.description,  code = error.code.toInt())
        }
    }

}
