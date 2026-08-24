@file:Suppress("UnsafeCastFromDynamic")

package com.wire.crypto

class PkiEnvironment internal constructor(handle: WebHandle) : JsResource(handle) {
    suspend fun addIntermediateCert(certPem: String) {
        awaitCoreCrypto(requireJs().addIntermediateCert(certPem))
    }

    suspend fun addTrustAnchor(certPem: String) {
        awaitCoreCrypto(requireJs().addTrustAnchor(certPem))
    }

    suspend fun getTrustAnchors(): List<String> =
        awaitCoreCrypto(requireJs().getTrustAnchors()).unsafeCast<Array<String>>().toList()

    suspend fun removeTrustAnchor(fingerprint: ByteArray) {
        awaitCoreCrypto(requireJs().removeTrustAnchor(fingerprint.toUint8Array()))
    }

    companion object
}

suspend fun PkiEnvironment.Companion.new(hooks: PkiEnvironmentHooks, database: Database): PkiEnvironment = PkiEnvironment(
    WebHandle(
        awaitCoreCrypto(
            jsCoreCryptoModule().PkiEnvironment.create(pkiEnvironmentHooksToJs(hooks), database.requireJs())
        )
    )
)

class X509CredentialAcquisition internal constructor(handle: WebHandle) : JsResource(handle) {
    constructor(pkiEnvironment: PkiEnvironment, config: X509CredentialAcquisitionConfiguration) : this(
        WebHandle(
            constructJsExport("X509CredentialAcquisition", pkiEnvironment.requireJs(), config.toJs())
        )
    )

    suspend fun finalize(): Credential = Credential(
        WebHandle(awaitCoreCrypto(requireJs().finalize()))
    )

    companion object {
        fun fromBytes(pkiEnvironment: PkiEnvironment, bytes: ByteArray): X509CredentialAcquisition =
            X509CredentialAcquisition(
                WebHandle(
                    webCallSync {
                        jsCoreCryptoModule().X509CredentialAcquisition.fromBytes(
                            pkiEnvironment.requireJs(),
                            bytes.toUint8Array()
                        )
                    }
                )
            )
    }
}

suspend fun X509CredentialAcquisition.Companion.newFromCredentialRef(
    pkiEnvironment: PkiEnvironment,
    config: X509CredentialAcquisitionConfiguration,
    credentialRef: CredentialRef,
    coreCryptoDatabase: Database? = null
): X509CredentialAcquisition {
    val promise = if (coreCryptoDatabase == null) {
        jsCoreCryptoModule().X509CredentialAcquisition.newFromCredentialRef(
            pkiEnvironment.requireJs(),
            config.toJs(),
            credentialRef.requireJs()
        )
    } else {
        jsCoreCryptoModule().X509CredentialAcquisition.newFromCredentialRef(
            pkiEnvironment.requireJs(),
            config.toJs(),
            credentialRef.requireJs(),
            coreCryptoDatabase.requireJs()
        )
    }
    return X509CredentialAcquisition(WebHandle(awaitCoreCrypto(promise)))
}
