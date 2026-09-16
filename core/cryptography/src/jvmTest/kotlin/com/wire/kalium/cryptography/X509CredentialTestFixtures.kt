/*
 * Wire
 * Copyright (C) 2026 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.cryptography

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import org.bouncycastle.asn1.pkcs.PKCSObjectIdentifiers
import org.bouncycastle.asn1.x500.X500Name
import org.bouncycastle.asn1.x509.BasicConstraints
import org.bouncycastle.asn1.x509.Extension
import org.bouncycastle.asn1.x509.Extensions
import org.bouncycastle.asn1.x509.KeyUsage
import org.bouncycastle.cert.X509CertificateHolder
import org.bouncycastle.cert.jcajce.JcaX509CertificateConverter
import org.bouncycastle.cert.jcajce.JcaX509ExtensionUtils
import org.bouncycastle.cert.jcajce.JcaX509v3CertificateBuilder
import org.bouncycastle.jce.interfaces.ECPublicKey as BouncyCastleECPublicKey
import org.bouncycastle.jce.provider.BouncyCastleProvider
import org.bouncycastle.openssl.jcajce.JcaPEMKeyConverter
import org.bouncycastle.openssl.jcajce.JcaPEMWriter
import org.bouncycastle.operator.jcajce.JcaContentSignerBuilder
import org.bouncycastle.pkcs.PKCS10CertificationRequest
import java.io.StringWriter
import java.math.BigInteger
import java.security.KeyPair
import java.security.KeyPairGenerator
import java.security.PublicKey
import java.security.cert.X509Certificate
import java.security.spec.ECGenParameterSpec
import java.time.Instant
import java.time.temporal.ChronoUnit
import java.util.Base64
import java.util.Date

@Suppress("UNUSED_PARAMETER")
internal class X509TestAcmeHooks(
    private val certificateAuthority: X509TestCertificateAuthority
) : PkiEnvironmentHooks {
    private var nonce = 0
    private lateinit var identifiers: JsonArray
    private var notBefore: JsonElement? = null
    private var notAfter: JsonElement? = null
    private lateinit var leafCertificatePem: String

    override suspend fun httpRequest(
        method: PkiHttpMethod,
        url: String,
        headers: List<PkiHttpHeader>,
        body: ByteArray
    ): PkiHttpResponse = when {
        method == PkiHttpMethod.GET && url == DIRECTORY_URL -> jsonResponse(
            buildJsonObject {
                put("newNonce", NEW_NONCE_URL)
                put("newAccount", NEW_ACCOUNT_URL)
                put("newOrder", NEW_ORDER_URL)
                put("revokeCert", REVOKE_CERT_URL)
            }
        )

        method == PkiHttpMethod.HEAD && url == NEW_NONCE_URL -> emptyResponse()
        method == PkiHttpMethod.POST && url == NEW_ACCOUNT_URL -> jsonResponse(
            buildJsonObject {
                put("status", "valid")
                put("orders", ACCOUNT_ORDERS_URL)
            }
        )

        method == PkiHttpMethod.POST && url == NEW_ORDER_URL -> {
            val payload = decodeJwsPayload(body)
            identifiers = payload.getValue("identifiers").jsonArray
            notBefore = payload["notBefore"]
            notAfter = payload["notAfter"]
            jsonResponse(orderResponse("pending"))
        }

        method == PkiHttpMethod.POST && url == DEVICE_AUTHORIZATION_URL ->
            jsonResponse(authorizationResponse("wireapp-device", DPOP_CHALLENGE_URL, "wire-dpop-01", DPOP_TARGET))

        method == PkiHttpMethod.POST && url == USER_AUTHORIZATION_URL ->
            jsonResponse(authorizationResponse("wireapp-user", OIDC_CHALLENGE_URL, "wire-oidc-01", OIDC_TARGET))

        method == PkiHttpMethod.POST && url == DPOP_CHALLENGE_URL ->
            jsonResponse(challengeResponse("wire-dpop-01", DPOP_CHALLENGE_URL, DPOP_TARGET, "valid"))

        method == PkiHttpMethod.POST && url == OIDC_CHALLENGE_URL ->
            jsonResponse(challengeResponse("wire-oidc-01", OIDC_CHALLENGE_URL, OIDC_TARGET, "valid"))

        method == PkiHttpMethod.POST && url == FINALIZE_URL -> {
            val csr = decodeJwsPayload(body).getValue("csr").jsonPrimitive.content
            leafCertificatePem = certificateAuthority.issueCertificate(Base64.getUrlDecoder().decode(csr))
            jsonResponse(orderResponse("valid", certificateUrl = CERTIFICATE_URL))
        }

        method == PkiHttpMethod.POST && url == CERTIFICATE_URL -> response(leafCertificatePem.encodeToByteArray())
        else -> error("Unexpected ACME request: $method $url")
    }

    override suspend fun authenticate(idp: String, keyAuth: String, acmeAud: String): String = "id-token"

    override suspend fun getBackendNonce(): String = "YmFja2VuZC1ub25jZS0x"

    override suspend fun fetchBackendAccessToken(dpop: String): String = "access-token"

    private fun authorizationResponse(
        identifierType: String,
        challengeUrl: String,
        challengeType: String,
        target: String
    ): JsonObject = buildJsonObject {
        put("status", "pending")
        put("expires", FAR_FUTURE)
        put("identifier", identifiers.single { it.jsonObject.getValue("type").jsonPrimitive.content == identifierType })
        put("challenges", buildJsonArray {
            add(challengeResponse(challengeType, challengeUrl, target, "pending"))
        })
    }

    private fun challengeResponse(type: String, url: String, target: String, status: String): JsonObject =
        buildJsonObject {
            put("type", type)
            put("url", url)
            put("status", status)
            put("token", CHALLENGE_TOKEN)
            put("target", target)
        }

    private fun orderResponse(status: String, certificateUrl: String? = null): JsonObject = buildJsonObject {
        put("status", status)
        put("expires", FAR_FUTURE)
        put("finalize", FINALIZE_URL)
        put("identifiers", identifiers)
        put("authorizations", buildJsonArray {
            add(JsonPrimitive(DEVICE_AUTHORIZATION_URL))
            add(JsonPrimitive(USER_AUTHORIZATION_URL))
        })
        notBefore?.let { put("notBefore", it) }
        notAfter?.let { put("notAfter", it) }
        certificateUrl?.let { put("certificate", it) }
    }

    private fun decodeJwsPayload(body: ByteArray): JsonObject {
        val jws = Json.parseToJsonElement(body.decodeToString()).jsonObject
        val payload = Base64.getUrlDecoder().decode(jws.getValue("payload").jsonPrimitive.content)
        return Json.parseToJsonElement(payload.decodeToString()).jsonObject
    }

    private fun jsonResponse(body: JsonObject): PkiHttpResponse = response(body.toString().encodeToByteArray())

    private fun emptyResponse(): PkiHttpResponse = response(byteArrayOf())

    private fun response(body: ByteArray): PkiHttpResponse = PkiHttpResponse(
        status = 200.toUShort(),
        headers = listOf(PkiHttpHeader("replay-nonce", "nonce-${nonce++}")),
        body = body
    )

    companion object {
        private const val BASE_URL = "https://acme.test"
        const val DIRECTORY_URL = "$BASE_URL/directory"
        private const val NEW_NONCE_URL = "$BASE_URL/new-nonce"
        private const val NEW_ACCOUNT_URL = "$BASE_URL/new-account"
        private const val NEW_ORDER_URL = "$BASE_URL/new-order"
        private const val REVOKE_CERT_URL = "$BASE_URL/revoke-cert"
        private const val ACCOUNT_ORDERS_URL = "$BASE_URL/account/test/orders"
        private const val DEVICE_AUTHORIZATION_URL = "$BASE_URL/authz/device"
        private const val USER_AUTHORIZATION_URL = "$BASE_URL/authz/user"
        private const val DPOP_CHALLENGE_URL = "$BASE_URL/challenge/dpop"
        private const val OIDC_CHALLENGE_URL = "$BASE_URL/challenge/oidc"
        private const val FINALIZE_URL = "$BASE_URL/order/test/finalize"
        private const val CERTIFICATE_URL = "$BASE_URL/certificate/test"
        private const val DPOP_TARGET = "https://wire.test/clients/fb4b58152e20/access-token"
        private const val OIDC_TARGET = "https://idp.test/authorize"
        private const val CHALLENGE_TOKEN = "MDEyMzQ1Njc4OWFiY2RlZg"
        private const val FAR_FUTURE = "2999-01-01T00:00:00Z"
    }
}

internal class X509TestCertificateAuthority {
    private val provider = BouncyCastleProvider()
    private val keyPair: KeyPair
    private val rootCertificate: X509Certificate
    private lateinit var issuedPublicKey: PublicKey

    val rootPem: String
        get() = rootCertificate.toPem()

    val externalSenderKey: ByteArray
        get() = keyPair.public.toCoreCryptoPublicKey(UNCOMPRESSED_P256_KEY_SIZE)

    init {
        keyPair = KeyPairGenerator.getInstance("EC", provider).apply {
            initialize(ECGenParameterSpec("secp256r1"))
        }.generateKeyPair()
        rootCertificate = createRootCertificate()
    }

    fun issuedPublicKeyBytes(expectedSize: Int): ByteArray =
        issuedPublicKey.toCoreCryptoPublicKey(expectedSize)

    fun issueCertificate(csrDer: ByteArray): String {
        val csr = PKCS10CertificationRequest(csrDer)
        val publicKey = JcaPEMKeyConverter()
            .setProvider(provider)
            .getPublicKey(csr.subjectPublicKeyInfo)
        issuedPublicKey = publicKey
        val now = Instant.now()
        val certificateBuilder = JcaX509v3CertificateBuilder(
            rootCertificate,
            BigInteger.TWO,
            Date.from(now.minus(1, ChronoUnit.MINUTES)),
            Date.from(now.plus(1, ChronoUnit.DAYS)),
            X500Name("O=wire.test,CN=Alice Smith"),
            publicKey
        )
        val extensionUtils = JcaX509ExtensionUtils()
        certificateBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(false))
        certificateBuilder.addExtension(Extension.keyUsage, true, KeyUsage(KeyUsage.digitalSignature))
        certificateBuilder.addExtension(
            Extension.subjectKeyIdentifier,
            false,
            extensionUtils.createSubjectKeyIdentifier(publicKey)
        )
        certificateBuilder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extensionUtils.createAuthorityKeyIdentifier(rootCertificate)
        )
        val requestedExtensions = csr.requestedExtensions()
        requestedExtensions.extensionOIDs.forEach { oid ->
            if (oid !in GENERATED_LEAF_EXTENSIONS) {
                val extension = requestedExtensions.getExtension(oid)
                certificateBuilder.addExtension(oid, extension.isCritical, extension.parsedValue)
            }
        }

        val certificate = certificateBuilder.build(contentSigner()).toCertificate()
        certificate.verify(keyPair.public)
        return certificate.toPem()
    }

    private fun createRootCertificate(): X509Certificate {
        val now = Instant.now()
        val subject = X500Name("CN=Kalium Test Root,O=Wire Test")
        val certificateBuilder = JcaX509v3CertificateBuilder(
            subject,
            BigInteger.ONE,
            Date.from(now.minus(1, ChronoUnit.DAYS)),
            Date.from(now.plus(365, ChronoUnit.DAYS)),
            subject,
            keyPair.public
        )
        val extensionUtils = JcaX509ExtensionUtils()
        certificateBuilder.addExtension(Extension.basicConstraints, true, BasicConstraints(true))
        certificateBuilder.addExtension(
            Extension.keyUsage,
            true,
            KeyUsage(KeyUsage.keyCertSign or KeyUsage.cRLSign)
        )
        val subjectKeyIdentifier = extensionUtils.createSubjectKeyIdentifier(keyPair.public)
        certificateBuilder.addExtension(Extension.subjectKeyIdentifier, false, subjectKeyIdentifier)
        certificateBuilder.addExtension(
            Extension.authorityKeyIdentifier,
            false,
            extensionUtils.createAuthorityKeyIdentifier(keyPair.public)
        )
        return certificateBuilder.build(contentSigner()).toCertificate().also { it.verify(keyPair.public) }
    }

    private fun contentSigner() = JcaContentSignerBuilder("SHA256withECDSA")
        .setProvider(provider)
        .build(keyPair.private)

    private fun X509CertificateHolder.toCertificate(): X509Certificate =
        JcaX509CertificateConverter()
            .setProvider(provider)
            .getCertificate(this)

    private fun PKCS10CertificationRequest.requestedExtensions(): Extensions {
        val attribute = getAttributes(PKCSObjectIdentifiers.pkcs_9_at_extensionRequest).single()
        return Extensions.getInstance(attribute.attrValues.getObjectAt(0))
    }

    private fun X509Certificate.toPem(): String = StringWriter().also { output ->
        JcaPEMWriter(output).use { it.writeObject(this) }
    }.toString()

    private companion object {
        const val UNCOMPRESSED_P256_KEY_SIZE = 65
        val GENERATED_LEAF_EXTENSIONS = setOf(
            Extension.basicConstraints,
            Extension.keyUsage,
            Extension.subjectKeyIdentifier,
            Extension.authorityKeyIdentifier
        )
    }
}

private fun PublicKey.toCoreCryptoPublicKey(expectedSize: Int): ByteArray {
    val uncompressedKey = (this as BouncyCastleECPublicKey).q.getEncoded(false)
    return when (expectedSize) {
        uncompressedKey.size -> uncompressedKey
        uncompressedKey.size - 1 -> uncompressedKey.copyOfRange(1, uncompressedKey.size)
        else -> error("Unsupported Core Crypto EC public key size: $expectedSize")
    }
}
