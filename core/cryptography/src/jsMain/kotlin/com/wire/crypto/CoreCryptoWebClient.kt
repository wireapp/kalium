@file:Suppress("TooManyFunctions", "LargeClass", "TooGenericExceptionCaught", "UnsafeCastFromDynamic", "OPT_IN_USAGE")

package com.wire.crypto

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.khronos.webgl.Uint8Array

class Database internal constructor(handle: WebHandle) : JsResource(handle) {
    companion object
}

suspend fun Database.Companion.open(location: String, key: DatabaseKey): Database {
    ensureCoreCryptoWasmInitialized()
    return Database(
        WebHandle(awaitCoreCrypto(jsCoreCryptoModule().Database.open(location, key.requireJs())))
    )
}

fun setLogger(logger: CoreCryptoLogger) {
    val jsLogger = jsObject()
    jsLogger.log = { level: Int, message: String, context: String? ->
        logger.log(CoreCryptoLogLevel.entries.first { it.value.toInt() == level }, message, context)
    }
    webCallSync { jsCoreCryptoModule().setLogger(jsLogger) }
}

fun setMaxLogLevel(level: CoreCryptoLogLevel) {
    webCallSync { jsCoreCryptoModule().setMaxLogLevel(level.value.toInt()) }
}

suspend fun exportDatabaseCopy(database: Database, destinationPath: String): Nothing {
    @Suppress("UNUSED_VARIABLE")
    val keepApiShape = database to destinationPath
    throw UnsupportedOperationException("Core Crypto browser databases cannot be exported as filesystem copies")
}

class CoreCrypto(database: Database) {
    private var jsDelegate: dynamic = webCallSync {
        jsCoreCryptoModule().CoreCrypto.new(database.requireJs())
    }

    private fun requireJs(): dynamic = checkNotNull(jsDelegate) { "Core Crypto has already been destroyed" }

    suspend fun <R> transaction(block: suspend (context: CoreCryptoContext) -> R): R {
        val result = awaitCoreCrypto(
            requireJs().transaction { context: dynamic ->
                GlobalScope.promise {
                    try {
                        block(CoreCryptoContext(WebHandle(context)))
                    } catch (throwable: Throwable) {
                        throw originalCoreCryptoJsErrorOrSelf(throwable)
                    }
                }
            }
        )
        return result.unsafeCast<R>()
    }

    suspend fun publicKey(credentialRef: CredentialRef): ByteArray =
        awaitCoreCrypto(requireJs().publicKey(credentialRef.requireJs())).unsafeCast<Uint8Array>().toByteArray()

    suspend fun findCredentials(
        clientId: ClientId? = null,
        publicKey: ByteArray? = null,
        cipherSuite: CipherSuite? = null,
        credentialType: CredentialType? = null,
        earliestValidity: ULong? = null
    ): List<CredentialRef> {
        val filters = jsObject()
        clientId?.let { filters.clientId = it.requireJs() }
        publicKey?.let { filters.publicKey = it.toUint8Array() }
        cipherSuite?.let { filters.cipherSuite = it.value.toInt() }
        credentialType?.let { filters.credentialType = it.value.toInt() }
        earliestValidity?.let { filters.earliestValidity = it.toJsBigInt() }
        return awaitCoreCrypto(requireJs().findCredentials(filters))
            .unsafeCast<Array<dynamic>>()
            .map { CredentialRef(WebHandle(it)) }
    }

    suspend fun e2eiConversationState(conversationId: ConversationId): E2eiConversationState =
        E2eiConversationState.entries.first {
            it.value.toInt() == dynamicToInt(
                awaitCoreCrypto(requireJs().e2eiConversationState(conversationId.requireJs()))
            )
        }

    suspend fun getUserIdentities(conversationId: ConversationId, userIds: List<Uuid>): Map<Uuid, List<WireIdentity>> =
        identitiesMapFromJs(
            awaitCoreCrypto(
                requireJs().getUserIdentities(conversationId.requireJs(), userIds.map { it.requireJs() }.toTypedArray())
            )
        )

    suspend fun registerEpochObserver(scope: CoroutineScope, epochObserver: EpochObserver) {
        val jsObserver = jsObject()
        jsObserver.epochChanged = { conversationId: dynamic, epoch: dynamic ->
            scope.promise {
                epochObserver.epochChanged(clientConversationId(conversationId), dynamicToULong(epoch))
            }
        }
        awaitCoreCrypto(requireJs().registerEpochObserver(jsObserver))
    }

    suspend fun setPkiEnvironment(pkiEnvironment: PkiEnvironment?) {
        awaitCoreCrypto(requireJs().setPkiEnvironment(pkiEnvironment?.requireJs() ?: jsUndefined()))
    }

    fun close() {
        val value = jsDelegate
        jsDelegate = null
        if (value != null) value.uniffiDestroy()
    }

    companion object {
        fun proteusLastResortPrekeyId(): UShort = webCallSync {
            dynamicToUShort(jsCoreCryptoModule().proteusLastResortPrekeyId())
        }

        fun proteusFingerprintPrekeybundle(prekey: ByteArray): String = webCallSync {
            jsCoreCryptoModule().proteusFingerprintPrekeybundle(prekey.toUint8Array()) as String
        }
    }
}

private fun clientConversationId(value: dynamic) = ConversationId(WebHandle(value))

private fun identitiesMapFromJs(value: dynamic): Map<Uuid, List<WireIdentity>> = buildMap {
    jsMapEntries(value).forEach { entry ->
        val pair = entry.unsafeCast<Array<dynamic>>()
        put(
            Uuid(WebHandle(pair[0])),
            pair[1].unsafeCast<Array<dynamic>>().map(::wireIdentityFromJs)
        )
    }
}

class CoreCryptoContext internal constructor(private val handle: WebHandle) {
    private fun requireJs(): dynamic = handle.value

    suspend fun addClientsToConversation(conversationId: ConversationId, keyPackages: List<KeyPackage>) {
        awaitCoreCrypto(
            requireJs().addClientsToConversation(
                conversationId.requireJs(),
                keyPackages.map { it.requireJs() }.toTypedArray()
            )
        )
    }

    suspend fun addCredential(credential: Credential): CredentialRef = CredentialRef(
        WebHandle(awaitCoreCrypto(requireJs().addCredential(credential.requireJs())))
    )

    suspend fun checkCredentials() {
        awaitCoreCrypto(requireJs().checkCredentials())
    }

    suspend fun commitPendingProposals(conversationId: ConversationId) {
        awaitCoreCrypto(requireJs().commitPendingProposals(conversationId.requireJs()))
    }

    suspend fun conversationCredential(conversationId: ConversationId): CredentialRef = CredentialRef(
        WebHandle(awaitCoreCrypto(requireJs().conversationCredential(conversationId.requireJs())))
    )

    suspend fun conversationEpoch(conversationId: ConversationId): ULong =
        dynamicToULong(awaitCoreCrypto(requireJs().conversationEpoch(conversationId.requireJs())))

    suspend fun conversationExists(conversationId: ConversationId): Boolean =
        awaitCoreCrypto(requireJs().conversationExists(conversationId.requireJs())) as Boolean

    suspend fun createConversation(
        conversationId: ConversationId,
        credentialRef: CredentialRef,
        externalSender: ExternalSender? = null
    ) {
        val promise = if (externalSender == null) {
            requireJs().createConversation(conversationId.requireJs(), credentialRef.requireJs())
        } else {
            requireJs().createConversation(conversationId.requireJs(), credentialRef.requireJs(), externalSender.requireJs())
        }
        awaitCoreCrypto(promise)
    }

    suspend fun decryptMessage(conversationId: ConversationId, payload: ByteArray): DecryptedMessage =
        decryptedMessageFromJs(
            awaitCoreCrypto(requireJs().decryptMessage(conversationId.requireJs(), payload.toUint8Array()))
        )

    suspend fun e2eiConversationState(conversationId: ConversationId): E2eiConversationState =
        E2eiConversationState.entries.first {
            it.value.toInt() == dynamicToInt(
                awaitCoreCrypto(requireJs().e2eiConversationState(conversationId.requireJs()))
            )
        }

    suspend fun e2eiIsEnabled(cipherSuite: CipherSuite): Boolean =
        awaitCoreCrypto(requireJs().e2eiIsEnabled(cipherSuite.value.toInt())) as Boolean

    suspend fun encryptMessage(conversationId: ConversationId, message: ByteArray): ByteArray =
        awaitCoreCrypto(requireJs().encryptMessage(conversationId.requireJs(), message.toUint8Array()))
            .unsafeCast<Uint8Array>()
            .toByteArray()

    suspend fun exportSecretKey(conversationId: ConversationId, keyLength: UInt): SecretKey = SecretKey(
        WebHandle(awaitCoreCrypto(requireJs().exportSecretKey(conversationId.requireJs(), keyLength.toInt())))
    )

    suspend fun generateKeyPackage(credentialRef: CredentialRef): KeyPackage = KeyPackage(
        WebHandle(awaitCoreCrypto(requireJs().generateKeyPackage(credentialRef.requireJs())))
    )

    suspend fun getClientIds(conversationId: ConversationId): List<ClientId> =
        awaitCoreCrypto(requireJs().getClientIds(conversationId.requireJs()))
            .unsafeCast<Array<dynamic>>()
            .map { ClientId(WebHandle(it)) }

    suspend fun getDeviceIdentities(conversationId: ConversationId, deviceIds: List<ClientId>): List<WireIdentity> =
        awaitCoreCrypto(
            requireJs().getDeviceIdentities(
                conversationId.requireJs(),
                deviceIds.map { it.requireJs() }.toTypedArray()
            )
        ).unsafeCast<Array<dynamic>>().map(::wireIdentityFromJs)

    suspend fun getExternalSender(conversationId: ConversationId): ExternalSender = ExternalSender(
        WebHandle(awaitCoreCrypto(requireJs().getExternalSender(conversationId.requireJs())))
    )

    suspend fun getKeyPackages(): List<KeyPackageRef> =
        awaitCoreCrypto(requireJs().getKeyPackages())
            .unsafeCast<Array<dynamic>>()
            .map { KeyPackageRef(WebHandle(it)) }

    suspend fun getUserIdentities(conversationId: ConversationId, userIds: List<Uuid>): Map<Uuid, List<WireIdentity>> =
        identitiesMapFromJs(
            awaitCoreCrypto(
                requireJs().getUserIdentities(conversationId.requireJs(), userIds.map { it.requireJs() }.toTypedArray())
            )
        )

    suspend fun joinByExternalCommit(groupInfo: GroupInfo, credentialRef: CredentialRef): ConversationId = ConversationId(
        WebHandle(awaitCoreCrypto(requireJs().joinByExternalCommit(groupInfo.requireJs(), credentialRef.requireJs())))
    )

    suspend fun mlsInit(clientId: ClientId, transport: MlsTransport) {
        awaitCoreCrypto(requireJs().mlsInit(clientId.requireJs(), mlsTransportToJs(transport)))
    }

    suspend fun processWelcomeMessage(welcomeMessage: Welcome): ConversationId = ConversationId(
        WebHandle(awaitCoreCrypto(requireJs().processWelcomeMessage(welcomeMessage.requireJs())))
    )

    suspend fun proteusDecryptSafe(sessionId: String, ciphertext: ByteArray): ByteArray =
        awaitCoreCrypto(requireJs().proteusDecryptSafe(sessionId, ciphertext.toUint8Array()))
            .unsafeCast<Uint8Array>()
            .toByteArray()

    suspend fun proteusEncrypt(sessionId: String, plaintext: ByteArray): ByteArray =
        awaitCoreCrypto(requireJs().proteusEncrypt(sessionId, plaintext.toUint8Array()))
            .unsafeCast<Uint8Array>()
            .toByteArray()

    suspend fun proteusEncryptBatched(sessions: List<String>, plaintext: ByteArray): Map<String, ByteArray> = buildMap {
        val value = awaitCoreCrypto(requireJs().proteusEncryptBatched(sessions.toTypedArray(), plaintext.toUint8Array()))
        jsMapEntries(value).forEach { entry ->
            val pair = entry.unsafeCast<Array<dynamic>>()
            put(pair[0] as String, pair[1].unsafeCast<Uint8Array>().toByteArray())
        }
    }

    suspend fun proteusFingerprint(): String = awaitCoreCrypto(requireJs().proteusFingerprint()) as String
    suspend fun proteusFingerprintRemote(sessionId: String): String =
        awaitCoreCrypto(requireJs().proteusFingerprintRemote(sessionId)) as String

    suspend fun proteusInit() {
        awaitCoreCrypto(requireJs().proteusInit())
    }

    suspend fun proteusLastResortPrekey(): ByteArray =
        awaitCoreCrypto(requireJs().proteusLastResortPrekey()).unsafeCast<Uint8Array>().toByteArray()

    suspend fun proteusNewPrekey(prekeyId: UShort): ByteArray =
        awaitCoreCrypto(requireJs().proteusNewPrekey(prekeyId.toInt())).unsafeCast<Uint8Array>().toByteArray()

    suspend fun proteusSessionDelete(sessionId: String) {
        awaitCoreCrypto(requireJs().proteusSessionDelete(sessionId))
    }

    suspend fun proteusSessionExists(sessionId: String): Boolean =
        awaitCoreCrypto(requireJs().proteusSessionExists(sessionId)) as Boolean

    suspend fun proteusSessionFromPrekey(sessionId: String, prekey: ByteArray) {
        awaitCoreCrypto(requireJs().proteusSessionFromPrekey(sessionId, prekey.toUint8Array()))
    }

    suspend fun removeClientsFromConversation(conversationId: ConversationId, clients: List<ClientId>) {
        awaitCoreCrypto(
            requireJs().removeClientsFromConversation(
                conversationId.requireJs(),
                clients.map { it.requireJs() }.toTypedArray()
            )
        )
    }

    suspend fun removeCredential(credentialRef: CredentialRef) {
        awaitCoreCrypto(requireJs().removeCredential(credentialRef.requireJs()))
    }

    suspend fun removeKeyPackage(kpRef: KeyPackageRef) {
        awaitCoreCrypto(requireJs().removeKeyPackage(kpRef.requireJs()))
    }

    suspend fun removeKeyPackagesFor(credentialRef: CredentialRef) {
        awaitCoreCrypto(requireJs().removeKeyPackagesFor(credentialRef.requireJs()))
    }

    suspend fun setConversationCredential(conversationId: ConversationId, credentialRef: CredentialRef) {
        awaitCoreCrypto(requireJs().setConversationCredential(conversationId.requireJs(), credentialRef.requireJs()))
    }

    suspend fun updateKeyingMaterial(conversationId: ConversationId) {
        awaitCoreCrypto(requireJs().updateKeyingMaterial(conversationId.requireJs()))
    }

    suspend fun wipeConversation(conversationId: ConversationId) {
        awaitCoreCrypto(requireJs().wipeConversation(conversationId.requireJs()))
    }
}
