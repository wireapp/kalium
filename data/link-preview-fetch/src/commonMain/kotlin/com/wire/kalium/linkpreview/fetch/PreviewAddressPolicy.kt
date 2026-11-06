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
@file:Suppress("MagicNumber", "ReturnCount")

package com.wire.kalium.linkpreview.fetch

/**
 * Classifies numeric addresses without resolving hostnames. Parsing bytes first makes the policy
 * independent of IPv6 compression, case, and the representation returned by the platform resolver.
 *
 * Special-use ranges follow the IANA IPv4 and IPv6 Special-Purpose Address Registries:
 * https://www.iana.org/assignments/iana-ipv4-special-registry/
 * https://www.iana.org/assignments/iana-ipv6-special-registry/
 */
internal fun isPublicPreviewAddress(address: String): Boolean {
    val ipv4 = parsePreviewIpv4(address)
    if (ipv4 != null) return isPublicPreviewIpv4(ipv4)

    val ipv6 = parsePreviewIpv6(address) ?: return false
    val isIpv4Mapped = ipv6.take(10).all { it == 0 } && ipv6[10] == 0xff && ipv6[11] == 0xff
    val isWellKnownNat64 = ipv6.take(12) == listOf(0, 0x64, 0xff, 0x9b, 0, 0, 0, 0, 0, 0, 0, 0)

    // Standard DNS64 networks can reach public IPv4 websites through 64:ff9b::/96. Check the
    // embedded destination rather than treating every translated address as public or private.
    return if (isIpv4Mapped || isWellKnownNat64) {
        isPublicPreviewIpv4(ipv6.copyOfRange(12, 16))
    } else {
        isPublicPreviewIpv6(ipv6)
    }
}

private fun parsePreviewIpv4(address: String): IntArray? {
    val parts = address.split('.')
    if (parts.size != 4) return null

    return IntArray(4) { index ->
        val part = parts[index]
        if (part.isEmpty() || part.length > 3 || part.any { it !in '0'..'9' }) return null
        // Reject ambiguous octal-looking input instead of relying on platform-specific parsing.
        if (part.length > 1 && part[0] == '0') return null
        part.toInt().takeIf { it in 0..255 } ?: return null
    }
}

private fun parsePreviewIpv6(address: String): IntArray? {
    if (address.length !in 2..45 || ':' !in address || '%' in address) return null

    val hexadecimal = expandPreviewIpv4Suffix(address) ?: return null

    val compression = hexadecimal.indexOf("::")
    if (compression != hexadecimal.lastIndexOf("::")) return null
    val halves = hexadecimal.split("::")
    val before = parsePreviewIpv6Groups(halves[0]) ?: return null
    val after = if (halves.size == 2) parsePreviewIpv6Groups(halves[1]) ?: return null else emptyList()
    val omittedGroups = 8 - before.size - after.size
    val invalidUncompressed = compression < 0 && omittedGroups != 0
    val invalidCompressed = compression >= 0 && omittedGroups < 1
    if (invalidUncompressed || invalidCompressed) return null

    val groups = before + List(omittedGroups) { 0 } + after
    return IntArray(16) { index ->
        val group = groups[index / 2]
        if (index % 2 == 0) group ushr 8 else group and 0xff
    }
}

private fun expandPreviewIpv4Suffix(address: String): String? {
    if ('.' !in address) return address
    val separator = address.lastIndexOf(':')
    val ipv4 = parsePreviewIpv4(address.substring(separator + 1)) ?: return null
    val high = (ipv4[0] shl 8) or ipv4[1]
    val low = (ipv4[2] shl 8) or ipv4[3]
    return address.substring(0, separator + 1) + high.toString(16) + ":" + low.toString(16)
}

private fun parsePreviewIpv6Groups(part: String): List<Int>? {
    if (part.isEmpty()) return emptyList()

    return part.split(':').map { group ->
        if (group.length !in 1..4 || group.any { it !in '0'..'9' && it.lowercaseChar() !in 'a'..'f' }) return null
        group.toInt(16)
    }
}

// Keep the explicit special-use ranges together so the policy is easy to audit against IANA.
@Suppress("CyclomaticComplexMethod")
private fun isPublicPreviewIpv4(bytes: IntArray): Boolean {
    val first = bytes[0]
    val second = bytes[1]
    val third = bytes[2]

    return when (first) {
        0, 10, 127 -> false
        100 if second in 64..127 -> false
        169 if second == 254 -> false
        172 if second in 16..31 -> false
        192 if second == 0 && third in listOf(0, 2) -> false
        192 if second == 88 && third == 99 -> false
        192 if second == 168 -> false
        198 if second in 18..19 -> false
        198 if second == 51 && third == 100 -> false
        203 if second == 0 && third == 113 -> false
        in 224..255 -> false
        else -> true
    }
}

private fun isPublicPreviewIpv6(bytes: IntArray): Boolean {
    // Only ordinary global unicast (2000::/3) is eligible. This excludes loopback, unspecified,
    // compatible IPv4, private/site-local, link-local, multicast, discard, and local-use NAT64.
    if (bytes[0] !in 0x20..0x3f) return false

    val firstGroup = (bytes[0] shl 8) or bytes[1]
    val secondGroup = (bytes[2] shl 8) or bytes[3]
    return when {
        // IETF protocol assignments, including Teredo, benchmarking, and ORCHID identifiers.
        firstGroup == 0x2001 && secondGroup < 0x0200 -> false
        firstGroup == 0x2001 && secondGroup == 0x0db8 -> false // Documentation.
        firstGroup == 0x2002 -> false // Deprecated 6to4 can embed a private IPv4 destination.
        firstGroup == 0x3fff && secondGroup < 0x1000 -> false // Documentation (3fff::/20).
        else -> true
    }
}
