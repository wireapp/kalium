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
package com.wire.kalium.logic.data.message.linkpreview

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class PreviewAddressPolicyTest {
    private val blockedIpv4 = listOf(
        "0.0.0.0", "0.255.255.255", "10.0.0.0", "10.255.255.255",
        "100.64.0.0", "100.127.255.255", "127.0.0.1", "127.255.255.255",
        "169.254.0.0", "169.254.255.255", "172.16.0.0", "172.31.255.255",
        "192.0.0.0", "192.0.0.255", "192.0.2.0", "192.0.2.255",
        "192.88.99.0", "192.88.99.255", "192.168.0.0", "192.168.255.255",
        "198.18.0.0", "198.19.255.255", "198.51.100.0", "198.51.100.255",
        "203.0.113.0", "203.0.113.255", "224.0.0.0", "239.255.255.255",
        "240.0.0.0", "255.255.255.255"
    )

    @Test
    fun givenPublicIpv4_whenClassifying_thenAllowsAddress() {
        listOf(
            "1.1.1.1", "8.8.8.8", "93.184.216.34", "100.63.255.255", "100.128.0.0",
            "169.253.255.255", "169.255.0.0", "172.15.255.255", "172.32.0.0", "192.0.1.1",
            "192.88.98.255", "192.88.100.0", "192.167.255.255", "192.169.0.0", "198.17.255.255",
            "198.20.0.0", "198.51.99.255", "198.51.101.0", "203.0.112.255", "203.0.114.0", "223.255.255.255"
        ).forEach { assertTrue(isPublicPreviewAddress(it), it) }
    }

    @Test
    fun givenSpecialUseIpv4_whenClassifying_thenRejectsAddress() {
        blockedIpv4.forEach { assertFalse(isPublicPreviewAddress(it), it) }
    }

    @Test
    fun givenPublicIpv6_whenClassifying_thenAllowsCompressedExpandedAndUppercaseForms() {
        listOf(
            "2001:4860:4860::8888", "2001:4860:4860:0:0:0:0:8888",
            "2606:4700:4700::1111", "2A00:1450:4001:0830:0000:0000:0000:200E",
            "2001:200::1", "3ffe::1", "3fff:1000::1", "2001:4860::8.8.8.8"
        ).forEach { assertTrue(isPublicPreviewAddress(it), it) }
    }

    @Test
    fun givenSpecialUseIpv6_whenClassifying_thenRejectsEveryRepresentation() {
        listOf(
            "::", "0:0:0:0:0:0:0:0", "0000:0000:0000:0000:0000:0000:0000:0000",
            "::1", "0:0:0:0:0:0:0:1", "0000:0000:0000:0000:0000:0000:0000:0001",
            "::127.0.0.1", "::8.8.8.8", "fc00::1", "fdff:ffff::1", "fe80::1", "febf::1",
            "fec0::1", "feff::1", "ff02::1", "ff0e::1", "100::1", "100:0:0:1::1",
            "2001::1", "2001:2::1", "2001:10::1", "2001:20::1", "2001:1ff::1",
            "2001:db8::1", "2001:0DB8:0000:0000:0000:0000:0000:0001", "2002:7f00:1::1",
            "3fff::1", "3fff:fff:ffff::1", "5f00::1", "64:ff9b:1::808:808"
        ).forEach { assertFalse(isPublicPreviewAddress(it), it) }
    }

    @Test
    fun givenPublicIpv4MappedIpv6_whenClassifying_thenChecksEmbeddedAddress() {
        listOf(
            "::ffff:8.8.8.8", "::FFFF:0808:0808", "0:0:0:0:0:ffff:808:808",
            "0000:0000:0000:0000:0000:FFFF:0808:0808"
        ).forEach { assertTrue(isPublicPreviewAddress(it), it) }
    }

    @Test
    fun givenBlockedIpv4MappedIpv6_whenClassifying_thenRejectsDottedAndHexadecimalForms() {
        blockedIpv4.forEach { ipv4 ->
            val octets = ipv4.split('.').map(String::toInt)
            val high = ((octets[0] shl 8) or octets[1]).toString(16)
            val low = ((octets[2] shl 8) or octets[3]).toString(16)
            listOf("::ffff:$ipv4", "::ffff:$high:$low", "0:0:0:0:0:ffff:$high:$low").forEach {
                assertFalse(isPublicPreviewAddress(it), it)
            }
        }
    }

    @Test
    fun givenStandardNat64Address_whenClassifying_thenAllowsOnlyPublicEmbeddedDestinations() {
        listOf("64:ff9b::8.8.8.8", "64:ff9b::808:808", "0064:ff9b:0:0:0:0:0808:0808").forEach {
            assertTrue(isPublicPreviewAddress(it), it)
        }
        blockedIpv4.forEach { ipv4 ->
            val address = "64:ff9b::$ipv4"
            assertFalse(isPublicPreviewAddress(address), address)
        }
    }

    @Test
    fun givenMalformedOrAmbiguousAddress_whenClassifying_thenRejectsWithoutDnsLookup() {
        listOf(
            "", "localhost", "example.com", "8.8.8", "8.8.8.8.8", "256.1.1.1", "-1.1.1.1", "+8.8.8.8",
            "08.8.8.8", "8.008.8.8", "0x7f000001", "2130706433", "127.1", "8.8.8.8 ", " 8.8.8.8",
            "8.8.8.8%en0", "[::1]", "[2001:4860::8888]", "2001:4860::8888%en0", "fe80::1%en0",
            ":", "1:", ":1", "1:2:3:4:5:6:7", "1:2:3:4:5:6:7:8:9", "1:2:3:4:5:6:7:8::",
            "2001:::1", "2001::1::1", "2001:gggg::1", "2001:12345::1", "2001::8.8.8.999",
            "2001::8.08.8.8", "2001:8.8.8.8::", "::ffff:192.168.1", "2001:4860::8888/64"
        ).forEach { assertFalse(isPublicPreviewAddress(it), it) }
    }
}
