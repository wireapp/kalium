@file:Suppress("TooManyFunctions", "LargeClass", "UnsafeCastFromDynamic")

package com.wire.crypto

import kotlinx.datetime.Instant
import org.khronos.webgl.Uint8Array

interface Disposable : AutoCloseable {
    fun destroy()

    override fun close() = destroy()

    companion object {
        internal fun destroy(vararg values: Any?) {
            values.forEach(::destroyValue)
        }

        private fun destroyValue(value: Any?) {
            when (value) {
                is Disposable -> value.destroy()
                is Iterable<*> -> value.forEach(::destroyValue)
                is Map<*, *> -> value.forEach { (key, item) ->
                    destroyValue(key)
                    destroyValue(item)
                }
            }
        }
    }
}

internal class WebHandle(val value: dynamic)

abstract class JsResource internal constructor(handle: WebHandle) : Disposable {
    private var jsDelegate: dynamic = handle.value

    internal fun requireJs(): dynamic = checkNotNull(jsDelegate) { "Core Crypto resource has already been closed" }

    override fun destroy() {
        val value = jsDelegate
        jsDelegate = null
        if (value != null) value.uniffiDestroy()
    }
}

enum class CipherSuite(val value: UShort) {
    MLS_128_DHKEMX25519_AES128GCM_SHA256_ED25519(1u),
    MLS_128_DHKEMP256_AES128GCM_SHA256_P256(2u),
    MLS_128_DHKEMX25519_CHACHA20POLY1305_SHA256_ED25519(3u),
    MLS_256_DHKEMX448_AES256GCM_SHA512_ED448(4u),
    MLS_256_DHKEMP521_AES256GCM_SHA512_P521(5u),
    MLS_256_DHKEMX448_CHACHA20POLY1305_SHA512_ED448(6u),
    MLS_256_DHKEMP384_AES256GCM_SHA384_P384(7u)
}

enum class CoreCryptoLogLevel(val value: UByte) { OFF(1u), TRACE(2u), DEBUG(3u), INFO(4u), WARN(5u), ERROR(6u) }
enum class CredentialType(val value: UByte) { BASIC(1u), X509(2u) }
enum class DeviceStatus(val value: UByte) { VALID(1u), EXPIRED(2u), REVOKED(3u) }
enum class E2eiConversationState(val value: UByte) { VERIFIED(1u), NOT_VERIFIED(2u), NOT_ENABLED(3u) }
enum class HttpMethod { GET, POST, PUT, DELETE, PATCH, HEAD }
enum class MlsGroupInfoEncryptionType(val value: UByte) { PLAINTEXT(1u), JWE_ENCRYPTED(2u) }
enum class MlsRatchetTreeType(val value: UByte) { FULL(1u), DELTA(2u), BY_REF(3u) }
enum class SignatureScheme(val value: UShort) {
    ECDSA_SECP256R1_SHA256(1027u),
    ECDSA_SECP384R1_SHA384(1283u),
    ECDSA_SECP521R1_SHA512(1539u),
    ED25519(2055u),
    ED448(2056u)
}

internal fun cipherSuiteFromJs(value: dynamic): CipherSuite =
    CipherSuite.entries.first { it.value.toInt() == dynamicToInt(value) }

internal fun credentialTypeFromJs(value: dynamic): CredentialType =
    CredentialType.entries.first { it.value.toInt() == dynamicToInt(value) }

internal fun signatureSchemeFromJs(value: dynamic): SignatureScheme =
    SignatureScheme.entries.first { it.value.toInt() == dynamicToInt(value) }

class Uuid internal constructor(handle: WebHandle) : JsResource(handle) {
    constructor(uuid: String) : this(WebHandle(constructJsExport("Uuid", uuid)))

    override fun toString(): String = webCallSync { requireJs().toString() as String }
    override fun equals(other: Any?): Boolean =
        other is Uuid && webCallSync { requireJs().equals(other.requireJs()) as Boolean }
    override fun hashCode(): Int = dynamicToULong(webCallSync { requireJs().hashCode() }).toInt()
}

class DeviceId internal constructor(handle: WebHandle) : JsResource(handle) {
    constructor(id: ULong) : this(WebHandle(constructJsExport("DeviceId", id.toJsBigInt())))

    fun toHexString(): String = webCallSync { requireJs().toHexString() as String }
    fun toU64(): ULong = dynamicToULong(webCallSync { requireJs().toU64() })
    override fun equals(other: Any?): Boolean =
        other is DeviceId && webCallSync { requireJs().equals(other.requireJs()) as Boolean }
    override fun hashCode(): Int = dynamicToULong(webCallSync { requireJs().hashCode() }).toInt()

    companion object {
        fun fromHexString(hexString: String): DeviceId = DeviceId(
            WebHandle(webCallSync { jsCoreCryptoModule().DeviceId.fromHexString(hexString) })
        )
    }
}

data class DeserializedClientId(
    val clientId: ClientId,
    val userId: Uuid,
    val deviceId: DeviceId,
    val domain: String
) : Disposable {
    override fun destroy() = Disposable.destroy(clientId, userId, deviceId)
}

class ClientId internal constructor(handle: WebHandle) : JsResource(handle) {
    constructor(userId: Uuid, deviceId: DeviceId, domain: String) : this(
        WebHandle(constructJsExport("ClientId", userId.requireJs(), deviceId.requireJs(), domain))
    )

    fun copyBytes(): ByteArray = webCallSync { requireJs().copyBytes().unsafeCast<Uint8Array>().toByteArray() }

    fun deserialize(): DeserializedClientId = webCallSync {
        val value = requireJs().deserialize()
        DeserializedClientId(
            clientId = ClientId(WebHandle(value.clientId)),
            userId = Uuid(WebHandle(value.userId)),
            deviceId = DeviceId(WebHandle(value.deviceId)),
            domain = value.domain as String
        )
    }

    override fun equals(other: Any?): Boolean =
        other is ClientId && webCallSync { requireJs().equals(other.requireJs()) as Boolean }
    override fun hashCode(): Int = dynamicToULong(webCallSync { requireJs().hashCode() }).toInt()
}

class ConversationId internal constructor(handle: WebHandle) : JsResource(handle) {
    constructor(bytes: ByteArray) : this(WebHandle(constructJsExport("ConversationId", bytes.toUint8Array())))

    fun copyBytes(): ByteArray = webCallSync { requireJs().copyBytes().unsafeCast<Uint8Array>().toByteArray() }
    override fun toString(): String = webCallSync { requireJs().toString() as String }
    override fun equals(other: Any?): Boolean =
        other is ConversationId && webCallSync { requireJs().equals(other.requireJs()) as Boolean }
    override fun hashCode(): Int = dynamicToULong(webCallSync { requireJs().hashCode() }).toInt()
}

class DatabaseKey(private val bytes: ByteArray) : Disposable {
    private var jsDelegate: dynamic = null

    internal fun requireJs(): dynamic {
        if (jsDelegate == null) jsDelegate = constructJsExport("DatabaseKey", bytes.toUint8Array())
        return jsDelegate
    }

    override fun destroy() {
        val value = jsDelegate
        jsDelegate = null
        if (value != null) value.uniffiDestroy()
    }

    override fun equals(other: Any?): Boolean = other is DatabaseKey && bytes.contentEquals(other.bytes)
    override fun hashCode(): Int = bytes.contentHashCode()
}

class Welcome internal constructor(handle: WebHandle) : JsResource(handle) {
    constructor(bytes: ByteArray) : this(WebHandle(constructJsExport("Welcome", bytes.toUint8Array())))
    fun serialize(): ByteArray = webCallSync { requireJs().serialize().unsafeCast<Uint8Array>().toByteArray() }
}

class GroupInfo internal constructor(handle: WebHandle) : JsResource(handle) {
    constructor(bytes: ByteArray) : this(WebHandle(constructJsExport("GroupInfo", bytes.toUint8Array())))
}

class KeyPackage internal constructor(handle: WebHandle) : JsResource(handle) {
    constructor(bytes: ByteArray) : this(WebHandle(constructJsExport("KeyPackage", bytes.toUint8Array())))
    fun serialize(): ByteArray = webCallSync { requireJs().serialize().unsafeCast<Uint8Array>().toByteArray() }
}

class SecretKey internal constructor(handle: WebHandle) : JsResource(handle) {
    fun copyBytes(): ByteArray = webCallSync { requireJs().copyBytes().unsafeCast<Uint8Array>().toByteArray() }
}

class ExternalSender internal constructor(handle: WebHandle) : JsResource(handle) {
    fun serialize(): ByteArray = webCallSync { requireJs().serialize().unsafeCast<Uint8Array>().toByteArray() }

    companion object {
        fun parse(key: ByteArray, signatureScheme: SignatureScheme): ExternalSender = ExternalSender(
            WebHandle(
                webCallSync {
                    jsCoreCryptoModule().ExternalSender.parse(key.toUint8Array(), signatureScheme.value.toInt())
                }
            )
        )
    }
}

class Credential internal constructor(handle: WebHandle) : JsResource(handle) {
    fun earliestValidity(): ULong = dynamicToULong(webCallSync { requireJs().earliestValidity() })
    fun exportPem(): String = webCallSync { requireJs().exportPem() as String }
    fun signatureScheme(): SignatureScheme = signatureSchemeFromJs(webCallSync { requireJs().signatureScheme() })
    fun type(): CredentialType = credentialTypeFromJs(webCallSync { requireJs().type() })

    companion object {
        fun basic(cipherSuite: CipherSuite, clientId: ClientId): Credential = Credential(
            WebHandle(
                webCallSync {
                    jsCoreCryptoModule().Credential.basic(cipherSuite.value.toInt(), clientId.requireJs())
                }
            )
        )
    }
}

class CredentialRef internal constructor(handle: WebHandle) : JsResource(handle) {
    fun cipherSuite(): CipherSuite = cipherSuiteFromJs(webCallSync { requireJs().cipherSuite() })
    fun clientId(): ClientId = ClientId(WebHandle(webCallSync { requireJs().clientId() }))
    fun earliestValidity(): ULong = dynamicToULong(webCallSync { requireJs().earliestValidity() })
    fun publicKeyHash(): ByteArray = webCallSync { requireJs().publicKeyHash().unsafeCast<Uint8Array>().toByteArray() }
    fun signatureScheme(): SignatureScheme = signatureSchemeFromJs(webCallSync { requireJs().signatureScheme() })
    fun type(): CredentialType = credentialTypeFromJs(webCallSync { requireJs().type() })
}

class KeyPackageRef internal constructor(handle: WebHandle) : JsResource(handle) {
    fun cipherSuite(): CipherSuite = cipherSuiteFromJs(webCallSync { requireJs().cipherSuite() })
    fun credentialType(): CredentialType = credentialTypeFromJs(webCallSync { requireJs().credentialType() })
    fun isValid(): Boolean = webCallSync { requireJs().isValid() as Boolean }
    fun signatureScheme(): SignatureScheme = signatureSchemeFromJs(webCallSync { requireJs().signatureScheme() })
}

data class GroupInfoBundle(
    val encryptionType: MlsGroupInfoEncryptionType,
    val ratchetTreeType: MlsRatchetTreeType,
    val payload: ByteArray
)

data class CommitBundle(
    val welcome: Welcome?,
    val commit: ByteArray,
    val groupInfo: GroupInfoBundle,
    val encryptedMessage: ByteArray?
) : Disposable {
    override fun destroy() = Disposable.destroy(welcome)
}

data class HistorySecret(val clientId: ClientId, val data: ByteArray) : Disposable {
    override fun destroy() = Disposable.destroy(clientId)
}

data class HttpHeader(val name: String, val value: String)
data class HttpResponse(val status: UShort, val headers: List<HttpHeader>, val body: ByteArray)
data class ProteusAutoPrekeyBundle(val id: UShort, val pkb: ByteArray)
data class WireIdentity(
    val clientId: ClientId?,
    val status: DeviceStatus,
    val thumbprint: String,
    val credentialType: CredentialType,
    val x509Identity: X509Identity?
) : Disposable {
    override fun destroy() = Disposable.destroy(clientId)
}

data class X509Identity(
    val handle: String,
    val displayName: String,
    val domain: String,
    val certificate: String,
    val serialNumber: String,
    val notBefore: Instant,
    val notAfter: Instant
)

data class X509CredentialAcquisitionConfiguration(
    val acmeDirectoryUrl: String,
    val cipherSuite: CipherSuite,
    val displayName: String,
    val clientId: ClientId,
    val handle: String,
    val domain: String,
    val team: String?,
    val validityPeriodSecs: ULong
) : Disposable {
    override fun destroy() = Disposable.destroy(clientId)

    internal fun toJs(): dynamic {
        val value = jsObject()
        value.acmeDirectoryUrl = acmeDirectoryUrl
        value.cipherSuite = cipherSuite.value.toInt()
        value.displayName = displayName
        value.clientId = clientId.requireJs()
        value.handle = handle
        value.domain = domain
        value.team = team ?: jsUndefined()
        value.validityPeriodSecs = validityPeriodSecs.toJsBigInt()
        return value
    }
}

sealed class BufferedDecryptedMessage : Disposable {
    data class Text(val plaintext: ByteArray, val senderClientId: ClientId, val identity: WireIdentity) :
        BufferedDecryptedMessage() {
        override fun destroy() = Disposable.destroy(senderClientId, identity)
    }

    data class Commit(val isActive: Boolean, val identity: WireIdentity) : BufferedDecryptedMessage() {
        override fun destroy() = Disposable.destroy(identity)
    }

    data class Proposal(val delay: ULong?, val identity: WireIdentity) : BufferedDecryptedMessage() {
        override fun destroy() = Disposable.destroy(identity)
    }
}

sealed class DecryptedMessage : Disposable {
    data class Text(val plaintext: ByteArray, val senderClientId: ClientId, val identity: WireIdentity) : DecryptedMessage() {
        override fun destroy() = Disposable.destroy(senderClientId, identity)
    }

    data class Commit(
        val isActive: Boolean,
        val bufferedMessages: List<BufferedDecryptedMessage>?,
        val identity: WireIdentity
    ) : DecryptedMessage() {
        override fun destroy() = Disposable.destroy(bufferedMessages, identity)
    }

    data class Proposal(val delay: ULong?, val identity: WireIdentity) : DecryptedMessage() {
        override fun destroy() = Disposable.destroy(identity)
    }
}

sealed class CoreCryptoException : Exception() {
    class Mls(val mlsError: MlsException) : CoreCryptoException()
    class Proteus(val exception: ProteusException) : CoreCryptoException()
    class E2ei(val e2eiError: String) : CoreCryptoException()
    class TransactionFailed(val error: String) : CoreCryptoException()
    class Other(val msg: String) : CoreCryptoException()
}

sealed class MlsException : Exception() {
    class ConversationAlreadyExists(val conversationId: ByteArray) : MlsException()
    class DuplicateMessage : MlsException()
    class BufferedFutureMessage : MlsException()
    class WrongEpoch : MlsException()
    class BufferedCommit : MlsException()
    class MessageEpochTooOld : MlsException()
    class SelfCommitIgnored : MlsException()
    class UnmergedPendingGroup : MlsException()
    class StaleProposal : MlsException()
    class StaleCommit : MlsException()
    class OrphanWelcome : MlsException()
    class MessageRejected(val reason: String) : MlsException()
    class Other(val msg: String) : MlsException()
}

sealed class ProteusException : Exception() {
    class SessionNotFound : ProteusException()
    class DuplicateMessage : ProteusException()
    class RemoteIdentityChanged : ProteusException()
    class Other(val errorCode: UShort) : ProteusException()
}

sealed class MlsTransportException : Exception() {
    class MessageRejected(val reason: String) : MlsTransportException()
}

sealed class PkiEnvironmentHooksException : Exception() {
    class Exception(val reason: String) : PkiEnvironmentHooksException()
}

interface CoreCryptoLogger {
    fun log(level: CoreCryptoLogLevel, message: String, context: String?)
}

interface EpochObserver {
    suspend fun epochChanged(conversationId: ConversationId, epoch: ULong)
}

interface MlsTransport {
    suspend fun sendCommitBundle(commitBundle: CommitBundle)
    suspend fun prepareForTransport(historySecret: HistorySecret): ByteArray
}

typealias MlsTransportData = ByteArray

interface PkiEnvironmentHooks {
    suspend fun httpRequest(method: HttpMethod, url: String, headers: List<HttpHeader>, body: ByteArray): HttpResponse
    suspend fun authenticate(idp: String, keyAuth: String, acmeAud: String, acquisitionSnapshot: ByteArray): String
    suspend fun getBackendNonce(): String
    suspend fun fetchBackendAccessToken(dpop: String): String
}

fun ByteArray.toWelcome() = Welcome(this)
fun ByteArray.toMLSKeyPackage() = KeyPackage(this)
fun ByteArray.toGroupInfo() = GroupInfo(this)
