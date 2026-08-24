@file:Suppress("ThrowsCount", "UnsafeCastFromDynamic", "OPT_IN_USAGE")

package com.wire.crypto

import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import kotlinx.datetime.Instant
import org.khronos.webgl.Uint8Array

internal fun commitBundleFromJs(value: dynamic): CommitBundle {
    val welcome = if (value.welcome == null) null else Welcome(WebHandle(value.welcome))
    val groupInfo = value.groupInfo
    return CommitBundle(
        welcome = welcome,
        commit = value.commit.unsafeCast<Uint8Array>().toByteArray(),
        groupInfo = GroupInfoBundle(
            encryptionType = MlsGroupInfoEncryptionType.entries.first {
                it.value.toInt() == dynamicToInt(groupInfo.encryptionType)
            },
            ratchetTreeType = MlsRatchetTreeType.entries.first {
                it.value.toInt() == dynamicToInt(groupInfo.ratchetTreeType)
            },
            payload = groupInfo.payload.unsafeCast<Uint8Array>().toByteArray()
        ),
        encryptedMessage = if (value.encryptedMessage == null) {
            null
        } else {
            value.encryptedMessage.unsafeCast<Uint8Array>().toByteArray()
        }
    )
}

internal fun historySecretFromJs(value: dynamic): HistorySecret = HistorySecret(
    clientId = ClientId(WebHandle(value.clientId)),
    data = value.data.unsafeCast<Uint8Array>().toByteArray()
)

internal fun HistorySecret.toJs(): dynamic {
    val value = jsObject()
    value.clientId = clientId.requireJs()
    value.data = data.toUint8Array()
    return value
}

internal fun wireIdentityFromJs(value: dynamic): WireIdentity = WireIdentity(
    clientId = if (value.clientId == null) null else ClientId(WebHandle(value.clientId)),
    status = DeviceStatus.entries.first { it.value.toInt() == dynamicToInt(value.status) },
    thumbprint = value.thumbprint as String,
    credentialType = credentialTypeFromJs(value.credentialType),
    x509Identity = if (value.x509Identity == null) null else x509IdentityFromJs(value.x509Identity)
)

private fun x509IdentityFromJs(value: dynamic): X509Identity = X509Identity(
    handle = value.handle as String,
    displayName = value.displayName as String,
    domain = value.domain as String,
    certificate = value.certificate as String,
    serialNumber = value.serialNumber as String,
    notBefore = Instant.fromEpochMilliseconds((value.notBefore.getTime() as Number).toLong()),
    notAfter = Instant.fromEpochMilliseconds((value.notAfter.getTime() as Number).toLong())
)

internal fun decryptedMessageFromJs(value: dynamic): DecryptedMessage = when (value.tag?.toString()) {
    "Text" -> DecryptedMessage.Text(
        plaintext = value.inner.plaintext.unsafeCast<Uint8Array>().toByteArray(),
        senderClientId = ClientId(WebHandle(value.inner.senderClientId)),
        identity = wireIdentityFromJs(value.inner.identity)
    )
    "Commit" -> DecryptedMessage.Commit(
        isActive = value.inner.isActive as Boolean,
        bufferedMessages = if (value.inner.bufferedMessages == null) {
            null
        } else {
            value.inner.bufferedMessages.unsafeCast<Array<dynamic>>().map(::bufferedDecryptedMessageFromJs)
        },
        identity = wireIdentityFromJs(value.inner.identity)
    )
    "Proposal" -> DecryptedMessage.Proposal(
        delay = if (value.inner.delay == null) null else dynamicToULong(value.inner.delay),
        identity = wireIdentityFromJs(value.inner.identity)
    )
    else -> error("Unknown Core Crypto decrypted message variant: ${value.tag}")
}

private fun bufferedDecryptedMessageFromJs(value: dynamic): BufferedDecryptedMessage = when (value.tag?.toString()) {
    "Text" -> BufferedDecryptedMessage.Text(
        plaintext = value.inner.plaintext.unsafeCast<Uint8Array>().toByteArray(),
        senderClientId = ClientId(WebHandle(value.inner.senderClientId)),
        identity = wireIdentityFromJs(value.inner.identity)
    )
    "Commit" -> BufferedDecryptedMessage.Commit(
        isActive = value.inner.isActive as Boolean,
        identity = wireIdentityFromJs(value.inner.identity)
    )
    "Proposal" -> BufferedDecryptedMessage.Proposal(
        delay = if (value.inner.delay == null) null else dynamicToULong(value.inner.delay),
        identity = wireIdentityFromJs(value.inner.identity)
    )
    else -> error("Unknown Core Crypto buffered message variant: ${value.tag}")
}

internal fun mlsTransportToJs(transport: MlsTransport): dynamic {
    val value = jsObject()
    value.sendCommitBundle = { bundle: dynamic ->
        GlobalScope.promise {
            try {
                transport.sendCommitBundle(commitBundleFromJs(bundle))
            } catch (exception: MlsTransportException.MessageRejected) {
                throw mlsTransportError(exception.reason).unsafeCast<Throwable>()
            }
        }
    }
    value.prepareForTransport = { secret: dynamic ->
        GlobalScope.promise {
            transport.prepareForTransport(historySecretFromJs(secret)).toUint8Array()
        }
    }
    return value
}

internal fun pkiEnvironmentHooksToJs(hooks: PkiEnvironmentHooks): dynamic {
    val value = jsObject()
    value.httpRequest = { method: Int, url: String, headers: Array<dynamic>, body: Uint8Array ->
        GlobalScope.promise {
            try {
                hooks.httpRequest(
                    method = HttpMethod.entries[method],
                    url = url,
                    headers = headers.map { HttpHeader(it.name as String, it.value as String) },
                    body = body.toByteArray()
                ).toJs()
            } catch (exception: PkiEnvironmentHooksException.Exception) {
                throw pkiEnvironmentHooksError(exception.reason).unsafeCast<Throwable>()
            }
        }
    }
    value.authenticate = { idp: String, keyAuth: String, acmeAud: String, snapshot: Uint8Array ->
        GlobalScope.promise {
            try {
                hooks.authenticate(idp, keyAuth, acmeAud, snapshot.toByteArray())
            } catch (exception: PkiEnvironmentHooksException.Exception) {
                throw pkiEnvironmentHooksError(exception.reason).unsafeCast<Throwable>()
            }
        }
    }
    value.getBackendNonce = {
        GlobalScope.promise {
            try {
                hooks.getBackendNonce()
            } catch (exception: PkiEnvironmentHooksException.Exception) {
                throw pkiEnvironmentHooksError(exception.reason).unsafeCast<Throwable>()
            }
        }
    }
    value.fetchBackendAccessToken = { dpop: String ->
        GlobalScope.promise {
            try {
                hooks.fetchBackendAccessToken(dpop)
            } catch (exception: PkiEnvironmentHooksException.Exception) {
                throw pkiEnvironmentHooksError(exception.reason).unsafeCast<Throwable>()
            }
        }
    }
    return value
}

private fun HttpResponse.toJs(): dynamic {
    val value = jsObject()
    value.status = status.toInt()
    value.headers = headers.map { header ->
        val jsHeader = jsObject()
        jsHeader.name = header.name
        jsHeader.value = header.value
        jsHeader
    }.toTypedArray()
    value.body = body.toUint8Array()
    return value
}
