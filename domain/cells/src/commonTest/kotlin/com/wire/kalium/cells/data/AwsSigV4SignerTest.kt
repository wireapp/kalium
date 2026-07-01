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
import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertEquals

class AwsSigV4SignerTest {

    @Test
    fun givenAwsSigV4Example_whenSigning_thenCanonicalRequestAndSignatureMatch() {
        val signer = AwsSigV4Signer(region = "us-east-1", service = "iam")
        val url = S3Url(
            url = "https://iam.amazonaws.com/?Action=ListUsers&Version=2010-05-08",
            canonicalUri = "/",
            canonicalQueryString = "Action=ListUsers&Version=2010-05-08",
            hostHeader = "iam.amazonaws.com",
            queryParameters = listOf(
                S3QueryParameter("Action", "ListUsers"),
                S3QueryParameter("Version", "2010-05-08"),
            ),
        )

        val result = signer.sign(
            method = "GET",
            url = url,
            headers = mapOf(
                HttpHeaders.Host to "iam.amazonaws.com",
                "x-amz-date" to "20150830T123600Z",
            ),
            payloadHash = EMPTY_SHA256,
            credentials = S3Credentials(
                accessKeyId = "AKIDEXAMPLE",
                secretAccessKey = "wJalrXUtnFEMI/K7MDENG+bPxRfiCYEXAMPLEKEY",
            ),
            signingDate = AwsSigningDate(date = "20150830", dateTime = "20150830T123600Z"),
        )

        assertEquals(
            "GET\n" +
                    "/\n" +
                    "Action=ListUsers&Version=2010-05-08\n" +
                    "host:iam.amazonaws.com\n" +
                    "x-amz-date:20150830T123600Z\n" +
                    "\n" +
                    "host;x-amz-date\n" +
                    EMPTY_SHA256,
            result.canonicalRequest,
        )
        assertEquals(
            "AWS4-HMAC-SHA256\n" +
                    "20150830T123600Z\n" +
                    "20150830/us-east-1/iam/aws4_request\n" +
                    "5599feeca6d065c7c80025038896f3f7f008849eacf307aa7d0cf8be7116cea6",
            result.stringToSign,
        )
        assertEquals(
            "b2e4af44cfad96d9ffa3c5653674a927b9b0995c33de22e1f843745ce37c1d5e",
            result.signature,
        )
        assertContains(
            result.headers.getValue(HttpHeaders.Authorization),
            "SignedHeaders=host;x-amz-date",
        )
    }

    private companion object {
        const val EMPTY_SHA256 = "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
    }
}
