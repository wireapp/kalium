/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
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
package com.wire.kalium.logic.feature.e2ei

import com.wire.kalium.cryptography.CryptoCertificateStatus
import io.mockative.Mock
import io.mockative.any
import io.mockative.classOf
import io.mockative.given
import io.mockative.mock
import io.mockative.once
import io.mockative.verify
import org.junit.Test
import java.security.cert.CertificateException
import kotlin.test.assertEquals

class PemCertificateDecoderTest {

    @Test
    fun givenAValidCertificate_whenDecodingIt_thenReturnCertificateObject() {
        val expectedCertificate = E2eiCertificate(
            issuer = "CN=wire Intermediate CA,O=wire",
            status = CertificateStatus.VALID,
            serialNumber = "60:88:F6:3E:97:4F:2E:AB:50:5C:C9:B1:D1:39:97:BA",
            certificateDetail = validPemCertificateString
        )
        val (arrangement, pemCertificateDecoder) = Arrangement()
            .withCertificate(validPemCertificateString)
            .withValidStatus()
            .arrange()

        val result = pemCertificateDecoder.decode(validPemCertificateString, CryptoCertificateStatus.VALID)

        verify(arrangement.x509CertificateGeneratorMock)
            .function(arrangement.x509CertificateGeneratorMock::generate)
            .with(any())
            .wasInvoked(once)

        assertEquals(expectedCertificate, result)
    }

    @Test(expected = CertificateException::class)
    fun givenAnInValidCertificate_whenDecodingIt_thenThrowCertificateException() {
        val (_, pemCertificateDecoder) = Arrangement()
            .withCertificate(invalidPemCertificateString)
            .withValidStatus()
            .arrange()

        pemCertificateDecoder.decode(invalidPemCertificateString, CryptoCertificateStatus.VALID)
    }

    class Arrangement {

        @Mock
        val x509CertificateGeneratorMock = mock(classOf<X509CertificateGenerator>())

        @Mock
        val certificateStatusChecker = mock(classOf<CertificateStatusChecker>())

        fun arrange() = this to PemCertificateDecoderImpl(
            x509CertificateGeneratorMock,
            certificateStatusChecker
        )

        fun withCertificate(certificateString: String) = apply {
            val platformCertificate = createPlatformX509Certificate(certificateString)
            given(x509CertificateGeneratorMock)
                .function(x509CertificateGeneratorMock::generate)
                .whenInvokedWith(any())
                .thenReturn(platformCertificate)
        }

        fun withValidStatus() = apply {
            given(certificateStatusChecker)
                .function(certificateStatusChecker::status)
                .whenInvokedWith(any())
                .thenReturn(CertificateStatus.VALID)
        }

        private fun createPlatformX509Certificate(certificateString: String): PlatformX509Certificate {
            val x509CertificateGenerator = X509CertificateGeneratorImpl()

            return x509CertificateGenerator.generate(certificateString.toByteArray())
        }
    }

    companion object {
        const val validPemCertificateString = "-----BEGIN CERTIFICATE-----\n" +
                "MIICNDCCAdqgAwIBAgIQYIj2PpdPLqtQXMmx0TmXujAKBggqhkjOPQQDAjAuMQ0w\n" +
                "CwYDVQQKEwR3aXJlMR0wGwYDVQQDExR3aXJlIEludGVybWVkaWF0ZSBDQTAeFw0y\n" +
                "MzEwMDIxNTIyMjJaFw0yMzEyMzExNTIyMjJaMDMxFzAVBgNVBAoTDmVsbmEud2ly\n" +
                "ZS5saW5rMRgwFgYDVQQDEw9Nb2p0YWJhIENoZW5hbmkwKjAFBgMrZXADIQAonK3u\n" +
                "cLIUnWP+8iG2GdabCWmzfiHTgXMncNx/r064LKOCAQIwgf8wDgYDVR0PAQH/BAQD\n" +
                "AgeAMB0GA1UdJQQWMBQGCCsGAQUFBwMBBggrBgEFBQcDAjAdBgNVHQ4EFgQUhVb7\n" +
                "GEXzaKxm3yiNEV0DOd78LBcwHwYDVR0jBBgwFoAUADMwoCFunlLkYO2SQbOAUOIL\n" +
                "VCowZQYDVR0RBF4wXIZBaW06d2lyZWFwcD1JRzlZdnp1V1FJS1VhUmsxMkY1Q0lR\n" +
                "Lzk1MzIxOGU2OGE2MzY0MWZAZWxuYS53aXJlLmxpbmuGF2ltOndpcmVhcHA9bW9q\n" +
                "dGFiYV93aXJlMCcGDCsGAQQBgqRkxihAAQQXMBUCAQYEDmdvb2dsZS1hbmRyb2lk\n" +
                "BAAwCgYIKoZIzj0EAwIDSAAwRQIhAJORy8WUjP8spjxlCCNOCAQrPIUbl6BTQGtv\n" +
                "FhJqP3UrAiAC4mbuQ6BlVmiovCzqP1YbiaGimvBEm/XTwtWJE6wM0A==\n" +
                "-----END CERTIFICATE-----\n" +
                "-----BEGIN CERTIFICATE-----\n" +
                "MIIBuDCCAV6gAwIBAgIQUJ8AHZqe79OeFVEPkdtQrDAKBggqhkjOPQQDAjAmMQ0w\n" +
                "CwYDVQQKEwR3aXJlMRUwEwYDVQQDEwx3aXJlIFJvb3QgQ0EwHhcNMjMwNDE3MDkw\n" +
                "ODQxWhcNMzMwNDE0MDkwODQxWjAuMQ0wCwYDVQQKEwR3aXJlMR0wGwYDVQQDExR3\n" +
                "aXJlIEludGVybWVkaWF0ZSBDQTBZMBMGByqGSM49AgEGCCqGSM49AwEHA0IABB9p\n" +
                "iYVv5ik10pwkOGdwVI6F6a8YKk9Ro/CqahPcTfefhOhL/M5RxzWmi2oW75mW6WKr\n" +
                "tG94D45Ur6yfNclLspmjZjBkMA4GA1UdDwEB/wQEAwIBBjASBgNVHRMBAf8ECDAG\n" +
                "AQH/AgEAMB0GA1UdDgQWBBQAMzCgIW6eUuRg7ZJBs4BQ4gtUKjAfBgNVHSMEGDAW\n" +
                "gBR40ZJlSIKIjEI/4ZMwgV3X5CB7tDAKBggqhkjOPQQDAgNIADBFAiEA5VT2B38E\n" +
                "9EunvJiLRCG9baeeMq4Yn1LwOT10cXdUIIICIEnDUrd2XW69YnUIPF3bEHln3oKt\n" +
                "wje0yUIA61GMpqNz\n" +
                "-----END CERTIFICATE-----"

        const val invalidPemCertificateString = "dsverlkerkvekvkadxjwencwejjk"
    }
}
