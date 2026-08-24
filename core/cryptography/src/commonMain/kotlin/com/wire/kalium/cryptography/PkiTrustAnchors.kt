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

import com.wire.kalium.cryptography.utils.calcSHA256
import kotlin.io.encoding.Base64

internal data class PkiTrustAnchorReconciliationPlan(
    val anchorsToAdd: List<CertificateChain>,
    val fingerprintsToRemove: List<ByteArray>
)

/**
 * Plans a complete trust-anchor replacement without mutating the PKI environment.
 *
 * Core Crypto identifies a trust anchor by the SHA-256 fingerprint of the certificate's
 * DER-encoded SubjectPublicKeyInfo. Calculating the same key here lets reconciliation avoid
 * re-adding unchanged roots and remove roots that are no longer present in the backend bundle.
 */
internal fun planPkiTrustAnchorReconciliation(
    currentAnchors: List<CertificateChain>,
    desiredPemBundle: String
): PkiTrustAnchorReconciliationPlan {
    val desiredAnchors = splitPemCertificateBundle(desiredPemBundle)
    require(desiredAnchors.isNotEmpty()) { "The trust-anchor bundle does not contain a certificate" }

    // Calculate every fingerprint before returning a mutation plan. A malformed certificate must
    // fail without leaving the PKI environment partially updated.
    val currentByFingerprint = currentAnchors.associateBy { TrustAnchorFingerprint.fromPem(it) }
    val desiredByFingerprint = desiredAnchors.associateBy { TrustAnchorFingerprint.fromPem(it) }

    return PkiTrustAnchorReconciliationPlan(
        anchorsToAdd = desiredByFingerprint
            .filterKeys { it !in currentByFingerprint }
            .values
            .toList(),
        fingerprintsToRemove = currentByFingerprint
            .filterKeys { it !in desiredByFingerprint }
            .keys
            .map { it.bytes.copyOf() }
    )
}

internal fun pkiTrustAnchorFingerprint(pem: CertificateChain): ByteArray {
    val certificates = splitPemCertificateBundle(pem)
    require(certificates.size == 1) { "Expected exactly one PEM-encoded certificate" }
    return calcSHA256(extractSubjectPublicKeyInfoDer(decodeCertificatePem(certificates.single())))
}

private class TrustAnchorFingerprint private constructor(val bytes: ByteArray) {
    override fun equals(other: Any?): Boolean =
        other is TrustAnchorFingerprint && bytes.contentEquals(other.bytes)

    override fun hashCode(): Int = bytes.contentHashCode()

    companion object {
        fun fromPem(pem: CertificateChain): TrustAnchorFingerprint =
            TrustAnchorFingerprint(pkiTrustAnchorFingerprint(pem))
    }
}

private fun splitPemCertificateBundle(pemBundle: String): List<CertificateChain> {
    val result = mutableListOf<CertificateChain>()
    var cursor = 0

    while (cursor < pemBundle.length) {
        val begin = pemBundle.indexOf(PEM_CERTIFICATE_BEGIN, cursor)
        if (begin < 0) {
            require(pemBundle.substring(cursor).isBlank()) {
                "Unexpected data outside a PEM-encoded certificate"
            }
            break
        }
        require(pemBundle.substring(cursor, begin).isBlank()) {
            "Unexpected data outside a PEM-encoded certificate"
        }

        val endMarkerStart = pemBundle.indexOf(PEM_CERTIFICATE_END, begin + PEM_CERTIFICATE_BEGIN.length)
        require(endMarkerStart >= 0) { "Missing PEM certificate end marker" }
        val end = endMarkerStart + PEM_CERTIFICATE_END.length
        result += pemBundle.substring(begin, end)
        cursor = end
    }

    return result
}

private fun decodeCertificatePem(pem: String): ByteArray {
    val encoded = pem
        .substring(PEM_CERTIFICATE_BEGIN.length, pem.length - PEM_CERTIFICATE_END.length)
        .filterNot(Char::isWhitespace)
    require(encoded.isNotEmpty()) { "The PEM certificate body is empty" }

    return try {
        Base64.decode(encoded)
    } catch (exception: IllegalArgumentException) {
        throw IllegalArgumentException("The PEM certificate body is not valid Base64", exception)
    }
}

/**
 * Extracts the exact DER encoding of TBSCertificate.subjectPublicKeyInfo (RFC 5280 §4.1).
 * Hashing this whole SEQUENCE, including its tag and length, matches RustCrypto's
 * SubjectPublicKeyInfo.fingerprint_bytes() used by Core Crypto v10.4.
 */
private fun extractSubjectPublicKeyInfoDer(certificateDer: ByteArray): ByteArray {
    val certificateDocument = DerReader(certificateDer)
    val certificate = certificateDocument.read(DER_SEQUENCE)
    certificateDocument.requireFullyConsumed("certificate")

    val certificateFields = certificate.contentReader(certificateDer)
    val tbsCertificate = certificateFields.read(DER_SEQUENCE)
    certificateFields.read(DER_SEQUENCE) // signatureAlgorithm
    certificateFields.read(DER_BIT_STRING) // signatureValue
    certificateFields.requireFullyConsumed("certificate")

    val tbsFields = tbsCertificate.contentReader(certificateDer)
    if (tbsFields.peekTag() == DER_EXPLICIT_VERSION) {
        tbsFields.read(DER_EXPLICIT_VERSION)
    }
    tbsFields.read(DER_INTEGER) // serialNumber
    tbsFields.read(DER_SEQUENCE) // signature
    tbsFields.read(DER_SEQUENCE) // issuer
    tbsFields.read(DER_SEQUENCE) // validity
    tbsFields.read(DER_SEQUENCE) // subject
    val subjectPublicKeyInfo = tbsFields.read(DER_SEQUENCE)

    val spkiFields = subjectPublicKeyInfo.contentReader(certificateDer)
    spkiFields.read(DER_SEQUENCE) // algorithm
    spkiFields.read(DER_BIT_STRING) // subjectPublicKey
    spkiFields.requireFullyConsumed("SubjectPublicKeyInfo")

    return certificateDer.copyOfRange(subjectPublicKeyInfo.encodedStart, subjectPublicKeyInfo.endExclusive)
}

private data class DerElement(
    val encodedStart: Int,
    val contentStart: Int,
    val endExclusive: Int
) {
    fun contentReader(bytes: ByteArray): DerReader = DerReader(bytes, contentStart, endExclusive)
}

/** Minimal strict DER reader for the fixed outer X.509 certificate structure. */
private class DerReader(
    private val bytes: ByteArray,
    start: Int = 0,
    private val limit: Int = bytes.size
) {
    private var position: Int = start

    init {
        require(start in 0..limit && limit <= bytes.size) { "Invalid DER bounds" }
    }

    fun peekTag(): Int? = if (position < limit) bytes[position].toInt() and 0xFF else null

    fun read(expectedTag: Int): DerElement {
        val encodedStart = position
        val actualTag = readByte()
        require(actualTag == expectedTag) {
            "Unexpected DER tag 0x${actualTag.toString(16)}; expected 0x${expectedTag.toString(16)}"
        }

        val contentLength = readLength()
        val contentStart = position
        val end = contentStart.toLong() + contentLength
        require(end <= limit.toLong()) { "DER element length exceeds its container" }
        position = end.toInt()
        return DerElement(encodedStart, contentStart, position)
    }

    fun requireFullyConsumed(name: String) {
        require(position == limit) { "Unexpected trailing data in DER $name" }
    }

    private fun readLength(): Long {
        val first = readByte()
        if (first and DER_LONG_FORM_FLAG == 0) return first.toLong()

        val byteCount = first and DER_LENGTH_MASK
        require(byteCount in 1..DER_MAX_LENGTH_BYTES) { "Invalid DER length encoding" }
        require(position + byteCount <= limit) { "Truncated DER length" }
        require((bytes[position].toInt() and 0xFF) != 0) { "Non-minimal DER length encoding" }

        var length = 0L
        repeat(byteCount) {
            length = (length shl 8) or readByte().toLong()
        }
        require(length >= DER_LONG_FORM_MIN_LENGTH) { "Non-minimal DER length encoding" }
        return length
    }

    private fun readByte(): Int {
        require(position < limit) { "Truncated DER element" }
        return bytes[position++].toInt() and 0xFF
    }
}

private const val PEM_CERTIFICATE_BEGIN = "-----BEGIN CERTIFICATE-----"
private const val PEM_CERTIFICATE_END = "-----END CERTIFICATE-----"
private const val DER_INTEGER = 0x02
private const val DER_BIT_STRING = 0x03
private const val DER_SEQUENCE = 0x30
private const val DER_EXPLICIT_VERSION = 0xA0
private const val DER_LONG_FORM_FLAG = 0x80
private const val DER_LENGTH_MASK = 0x7F
private const val DER_MAX_LENGTH_BYTES = 4
private const val DER_LONG_FORM_MIN_LENGTH = 128L
