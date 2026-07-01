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
package com.wire.kalium.cells.data

import io.ktor.http.HttpHeaders
import io.ktor.util.date.GMTDate
import okio.ByteString.Companion.encodeUtf8
import okio.ByteString.Companion.toByteString

internal class AwsSigV4Signer(
    private val region: String,
    private val service: String,
) {
    internal fun sign(
        method: String,
        url: S3Url,
        headers: Map<String, String>,
        payloadHash: String,
        credentials: S3Credentials,
        signingDate: AwsSigningDate,
    ): SignedS3Request {
        val canonicalHeaders = headers.canonicalHeaders()
        val signedHeaders = canonicalHeaders.signedHeaders
        val canonicalRequest = buildCanonicalRequest(
            method = method,
            canonicalUri = url.canonicalUri,
            canonicalQueryString = url.canonicalQueryString,
            canonicalHeaders = canonicalHeaders.value,
            signedHeaders = signedHeaders,
            payloadHash = payloadHash,
        )
        val credentialScope = signingDate.credentialScope(region, service)
        val stringToSign = stringToSign(signingDate.dateTime, credentialScope, canonicalRequest)
        val signature = signature(credentials.secretAccessKey, signingDate.date, stringToSign)
        val authorizationHeader = "$ALGORITHM Credential=${credentials.accessKeyId}/$credentialScope, " +
                "SignedHeaders=$signedHeaders, Signature=$signature"

        return SignedS3Request(
            url = url.url,
            headers = headers + (HttpHeaders.Authorization to authorizationHeader),
            canonicalRequest = canonicalRequest,
            stringToSign = stringToSign,
            signature = signature,
        )
    }

    internal fun presignGetUrl(
        url: S3Url,
        credentials: S3Credentials,
        signingDate: AwsSigningDate,
        expiresSeconds: Int,
    ): String {
        val credentialScope = signingDate.credentialScope(region, service)
        val signingQueryParameters = listOf(
            S3QueryParameter("X-Amz-Algorithm", ALGORITHM),
            S3QueryParameter("X-Amz-Credential", "${credentials.accessKeyId}/$credentialScope"),
            S3QueryParameter("X-Amz-Date", signingDate.dateTime),
            S3QueryParameter("X-Amz-Expires", expiresSeconds.toString()),
            S3QueryParameter("X-Amz-SignedHeaders", HOST_HEADER_NAME),
        )
        val presignedUrl = url.withQueryParameters(url.queryParameters + signingQueryParameters)
        val canonicalRequest = buildCanonicalRequest(
            method = "GET",
            canonicalUri = presignedUrl.canonicalUri,
            canonicalQueryString = presignedUrl.canonicalQueryString,
            canonicalHeaders = "$HOST_HEADER_NAME:${url.hostHeader}\n",
            signedHeaders = HOST_HEADER_NAME,
            payloadHash = UNSIGNED_PAYLOAD,
        )
        val stringToSign = stringToSign(signingDate.dateTime, credentialScope, canonicalRequest)
        val signature = signature(credentials.secretAccessKey, signingDate.date, stringToSign)
        return presignedUrl.withQueryParameters(
            presignedUrl.queryParameters + S3QueryParameter("X-Amz-Signature", signature)
        ).url
    }

    internal fun buildCanonicalRequest(
        method: String,
        canonicalUri: String,
        canonicalQueryString: String,
        canonicalHeaders: String,
        signedHeaders: String,
        payloadHash: String,
    ): String = buildString {
        append(method)
        append('\n')
        append(canonicalUri)
        append('\n')
        append(canonicalQueryString)
        append('\n')
        append(canonicalHeaders)
        append('\n')
        append(signedHeaders)
        append('\n')
        append(payloadHash)
    }

    private fun stringToSign(dateTime: String, credentialScope: String, canonicalRequest: String): String = buildString {
        append(ALGORITHM)
        append('\n')
        append(dateTime)
        append('\n')
        append(credentialScope)
        append('\n')
        append(sha256Hex(canonicalRequest))
    }

    private fun signature(secretAccessKey: String, date: String, stringToSign: String): String {
        val dateKey = hmacSha256(("AWS4$secretAccessKey").encodeUtf8().toByteArray(), date)
        val dateRegionKey = hmacSha256(dateKey, region)
        val dateRegionServiceKey = hmacSha256(dateRegionKey, service)
        val signingKey = hmacSha256(dateRegionServiceKey, AWS4_REQUEST)
        return hmacSha256(signingKey, stringToSign).toByteString().hex()
    }

    private fun Map<String, String>.canonicalHeaders(): CanonicalHeaders {
        val normalized = entries
            .map { (name, value) -> name.lowercase() to value.normalizeHeaderValue() }
            .sortedBy { it.first }
        return CanonicalHeaders(
            value = normalized.joinToString(separator = "") { (name, value) -> "$name:$value\n" },
            signedHeaders = normalized.joinToString(separator = ";") { it.first },
        )
    }

    private fun String.normalizeHeaderValue(): String =
        trim().replace(Regex("\\s+"), " ")

    private fun sha256Hex(value: String): String =
        value.encodeUtf8().sha256().hex()

    private fun hmacSha256(key: ByteArray, value: String): ByteArray =
        value.encodeUtf8().hmacSha256(key.toByteString()).toByteArray()

    private data class CanonicalHeaders(
        val value: String,
        val signedHeaders: String,
    )

    private companion object {
        const val ALGORITHM = "AWS4-HMAC-SHA256"
        const val AWS4_REQUEST = "aws4_request"
        const val HOST_HEADER_NAME = "host"
        const val UNSIGNED_PAYLOAD = "UNSIGNED-PAYLOAD"
    }
}

internal data class SignedS3Request(
    val url: String,
    val headers: Map<String, String>,
    val canonicalRequest: String,
    val stringToSign: String,
    val signature: String,
)

internal data class AwsSigningDate(
    val date: String,
    val dateTime: String,
) {
    fun credentialScope(region: String, service: String): String =
        "$date/$region/$service/aws4_request"

    companion object {
        fun now(): AwsSigningDate = GMTDate().toAwsSigningDate()
    }
}

internal fun GMTDate.toAwsSigningDate(): AwsSigningDate {
    val date = "${year.fourDigits()}${(month.ordinal + MONTH_NUMBER_OFFSET).twoDigits()}${dayOfMonth.twoDigits()}"
    val dateTime = "$date" +
            "T${hours.twoDigits()}${minutes.twoDigits()}${seconds.twoDigits()}Z"
    return AwsSigningDate(date, dateTime)
}

private fun Int.twoDigits(): String = toString().padStart(TWO_DIGITS, '0')

private fun Int.fourDigits(): String = toString().padStart(FOUR_DIGITS, '0')

private const val TWO_DIGITS = 2
private const val FOUR_DIGITS = 4
private const val MONTH_NUMBER_OFFSET = 1
