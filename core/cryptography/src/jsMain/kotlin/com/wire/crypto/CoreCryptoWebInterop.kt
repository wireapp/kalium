@file:Suppress(
    "CyclomaticComplexMethod",
    "ReturnCount",
    "TooGenericExceptionCaught",
    "TooManyFunctions",
    "UnsafeCastFromDynamic",
    "UNCHECKED_CAST_TO_EXTERNAL_INTERFACE",
    "unused"
)

package com.wire.crypto

import kotlinx.coroutines.await
import kotlinx.coroutines.DelicateCoroutinesApi
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.promise
import org.khronos.webgl.Uint8Array
import kotlin.js.Promise

private var moduleInitialization: Promise<dynamic>? = null
private var loadedCoreCryptoModule: dynamic = null

@OptIn(DelicateCoroutinesApi::class)
internal suspend fun ensureCoreCryptoWasmInitialized() {
    val initialization = moduleInitialization ?: GlobalScope.promise {
        val module = js("import('@wireapp/core-crypto/browser')")
            .unsafeCast<Promise<dynamic>>()
            .await()
        val wasmAsset = js("import('kalium-core-crypto-wasm')")
            .unsafeCast<Promise<dynamic>>()
            .await()
        module.initWasmModule(wasmAsset.default)
            .unsafeCast<Promise<Unit>>()
            .await()
        module
    }.also {
        moduleInitialization = it
    }
    try {
        loadedCoreCryptoModule = initialization.await()
    } catch (throwable: Throwable) {
        if (moduleInitialization === initialization) moduleInitialization = null
        throw throwable
    }
}

internal fun jsCoreCryptoModule(): dynamic = checkNotNull(loadedCoreCryptoModule) {
    "Core Crypto Wasm has not been initialized"
}

internal fun jsObject(): dynamic = js("({})")

internal fun jsUndefined(): dynamic = js("undefined")

internal fun constructJsExport(name: String, vararg arguments: dynamic): dynamic = webCallSync {
    val constructor = jsCoreCryptoModule()[name]
    js("Reflect").construct(constructor, arguments)
}

internal fun constructJs(constructor: dynamic, vararg arguments: dynamic): dynamic = webCallSync {
    js("Reflect").construct(constructor, arguments)
}

internal fun jsMapEntries(value: dynamic): Array<dynamic> =
    js("Array").from(value.entries()).unsafeCast<Array<dynamic>>()

internal fun ByteArray.toUint8Array(): Uint8Array = Uint8Array(toTypedArray())

internal fun Uint8Array.toByteArray(): ByteArray =
    ByteArray(length) { index -> (asDynamic()[index] as Number).toByte() }

internal fun dynamicToULong(value: dynamic): ULong = value?.toString()?.toULong() ?: 0u

internal fun ULong.toJsBigInt(): dynamic = js("BigInt")(toString())

internal fun dynamicToUInt(value: dynamic): UInt = (value as Number).toInt().toUInt()

internal fun dynamicToUShort(value: dynamic): UShort = (value as Number).toInt().toUShort()

internal fun dynamicToInt(value: dynamic): Int = (value as Number).toInt()

internal suspend fun awaitCoreCrypto(promise: dynamic): dynamic = try {
    promise.unsafeCast<Promise<dynamic>>().await()
} catch (throwable: Throwable) {
    throw mapCoreCryptoError(throwable)
}

// Core Crypto owns transaction error propagation. Mapping inside the callback would
// turn its JS error into a Kotlin error that UniFFI cannot recognize and preserve.
internal inline fun <T> webCallSync(block: () -> T): T = try {
    block()
} catch (throwable: Throwable) {
    throw mapCoreCryptoError(throwable)
}

private fun mapCoreCryptoError(throwable: Throwable): Throwable {
    if (throwable is CoreCryptoException || throwable is ProteusException) return throwable

    val error = throwable.asDynamic()
    val tag = error.tag?.toString() ?: return throwable
    val inner = error.inner
    val message = error.message?.toString()
    val mapped = when (tag) {
        "Mls" -> CoreCryptoException.Mls(mapMlsException(inner?.mlsError, message))
        "Proteus" -> CoreCryptoException.Proteus(mapProteusException(inner?.exception))
        "E2ei" -> CoreCryptoException.E2ei(inner?.e2eiError?.toString() ?: message ?: "Unknown E2EI error")
        "TransactionFailed" -> CoreCryptoException.TransactionFailed(
            inner?.error?.toString() ?: message ?: "Transaction failed"
        )
        "Other" -> CoreCryptoException.Other(inner?.msg?.toString() ?: message ?: "Unknown Core Crypto error")
        "SessionNotFound", "DuplicateMessage", "RemoteIdentityChanged" -> mapProteusException(error)
        else -> throwable
    }
    if (mapped !== throwable) mapped.asDynamic().__kaliumCoreCryptoJsError = error
    return mapped
}

internal fun originalCoreCryptoJsErrorOrSelf(throwable: Throwable): Throwable {
    var current: Throwable? = throwable
    repeat(MAX_ERROR_CAUSE_DEPTH) {
        current ?: return throwable
        val original = current.asDynamic().__kaliumCoreCryptoJsError
        if (original != null) return original.unsafeCast<Throwable>()
        current = current.cause
    }
    return throwable
}

private fun mapMlsException(error: dynamic, fallbackMessage: String?): MlsException = when (error?.tag?.toString()) {
    "ConversationAlreadyExists" -> MlsException.ConversationAlreadyExists(
        error.inner?.conversationId?.unsafeCast<Uint8Array>()?.toByteArray() ?: byteArrayOf()
    )
    "DuplicateMessage" -> MlsException.DuplicateMessage()
    "BufferedFutureMessage" -> MlsException.BufferedFutureMessage()
    "WrongEpoch" -> MlsException.WrongEpoch()
    "BufferedCommit" -> MlsException.BufferedCommit()
    "MessageEpochTooOld" -> MlsException.MessageEpochTooOld()
    "SelfCommitIgnored" -> MlsException.SelfCommitIgnored()
    "UnmergedPendingGroup" -> MlsException.UnmergedPendingGroup()
    "StaleProposal" -> MlsException.StaleProposal()
    "StaleCommit" -> MlsException.StaleCommit()
    "OrphanWelcome" -> MlsException.OrphanWelcome()
    "MessageRejected" -> MlsException.MessageRejected(
        error.inner?.reason?.toString() ?: fallbackMessage ?: "Message rejected"
    )
    else -> MlsException.Other(error?.inner?.msg?.toString() ?: fallbackMessage ?: "Unknown MLS error")
}

private fun mapProteusException(error: dynamic): ProteusException = when (error?.tag?.toString()) {
    "SessionNotFound" -> ProteusException.SessionNotFound()
    "DuplicateMessage" -> ProteusException.DuplicateMessage()
    "RemoteIdentityChanged" -> ProteusException.RemoteIdentityChanged()
    else -> ProteusException.Other(dynamicToUShort(error?.inner?.errorCode ?: 0))
}

internal fun mlsTransportError(reason: String): dynamic {
    val inner = jsObject()
    inner.reason = reason
    return constructJs(jsCoreCryptoModule().MlsTransportError.MessageRejected, inner)
}

internal fun pkiEnvironmentHooksError(reason: String): dynamic {
    val inner = jsObject()
    inner.reason = reason
    return constructJs(jsCoreCryptoModule().PkiEnvironmentHooksError.Error, inner)
}

private const val MAX_ERROR_CAUSE_DEPTH = 8
