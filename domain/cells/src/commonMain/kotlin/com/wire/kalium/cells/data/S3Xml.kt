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
@file:Suppress("MatchingDeclarationName")

package com.wire.kalium.cells.data

internal data class S3Error(
    val code: String?,
)

internal fun String.multipartUploadId(): String? {
    val result = parseXmlDocument()?.takeIf { it.localName == "InitiateMultipartUploadResult" } ?: return null
    return result.children.firstOrNull { it.localName == "UploadId" }?.textValue()
}

internal fun String.embeddedS3Error(): S3Error? {
    val error = parseXmlDocument()?.takeIf { it.localName == "Error" } ?: return null
    return S3Error(code = error.children.firstOrNull { it.localName == "Code" }?.textValue())
}

internal fun String.containsCompleteMultipartUploadResult(): Boolean =
    parseXmlDocument()?.localName == "CompleteMultipartUploadResult"

private fun String.parseXmlDocument(): XmlElement? = XmlDocumentParser(this).parse()

private class XmlDocumentParser(
    private val xml: String,
) {
    private var offset = 0
    private var root: XmlElement? = null
    private val openElements = mutableListOf<XmlElement>()

    fun parse(): XmlElement? {
        if (xml.startsWith(XML_BYTE_ORDER_MARK)) offset++
        while (offset < xml.length) {
            val parsed = when {
                xml[offset] != '<' -> parseText()
                xml.startsWith(XML_COMMENT_START, offset) -> skipDelimited(XML_COMMENT_END)
                xml.startsWith(XML_PROCESSING_INSTRUCTION_START, offset) ->
                    skipDelimited(XML_PROCESSING_INSTRUCTION_END)
                xml.startsWith(XML_CDATA_START, offset) -> parseCData()
                xml.startsWith("</", offset) -> parseEndElement()
                xml.startsWith("<!", offset) -> false
                else -> parseStartElement()
            }
            if (!parsed) return null
        }
        return root?.takeIf { openElements.isEmpty() }
    }

    @Suppress("ReturnCount") // Each early return rejects one distinct malformed XML state.
    private fun parseText(): Boolean {
        val end = xml.indexOf('<', offset).takeIf { it >= 0 } ?: xml.length
        val rawText = xml.substring(offset, end)
        if (XML_CDATA_END in rawText) return false
        val decoded = rawText.decodeXmlText() ?: return false
        if (openElements.isEmpty()) {
            if (decoded.isNotBlank()) return false
        } else {
            openElements.last().text.append(decoded)
        }
        offset = end
        return true
    }

    @Suppress("ReturnCount") // Each early return rejects one distinct malformed XML state.
    private fun parseCData(): Boolean {
        val contentStart = offset + XML_CDATA_START.length
        val end = xml.indexOf(XML_CDATA_END, contentStart)
        if (end < 0 || openElements.isEmpty()) return false
        val content = xml.substring(contentStart, end)
        if (!content.hasOnlyValidXmlCharacters()) return false
        openElements.last().text.append(content)
        offset = end + XML_CDATA_END.length
        return true
    }

    private fun skipDelimited(endDelimiter: String): Boolean {
        val end = xml.indexOf(endDelimiter, offset)
        if (end < 0) return false
        offset = end + endDelimiter.length
        return true
    }

    @Suppress("ReturnCount") // Each early return rejects one distinct malformed XML state.
    private fun parseStartElement(): Boolean {
        val end = findStartElementEnd() ?: return false
        var markup = xml.substring(offset + 1, end).trimEnd()
        val selfClosing = markup.endsWith('/')
        if (selfClosing) markup = markup.dropLast(1).trimEnd()
        val nameEnd = markup.indexOfFirst { it.isWhitespace() }.takeIf { it >= 0 } ?: markup.length
        val qualifiedName = markup.substring(0, nameEnd)
        if (!XML_QUALIFIED_NAME.matches(qualifiedName) || !hasValidAttributes(markup, nameEnd)) return false

        val element = XmlElement(qualifiedName)
        if (openElements.isEmpty()) {
            if (root != null) return false
            root = element
        } else {
            openElements.last().children += element
        }
        if (!selfClosing) openElements += element
        offset = end + 1
        return true
    }

    private fun findStartElementEnd(): Int? {
        var quote: Char? = null
        for (index in offset + 1 until xml.length) {
            val character = xml[index]
            when {
                quote != null && character == quote -> quote = null
                quote == null && (character == '"' || character == '\'') -> quote = character
                quote == null && character == '>' -> return index
            }
        }
        return null
    }

    @Suppress(
        "ReturnCount",
        "CyclomaticComplexMethod",
        // Attribute parsing is a validation state machine; early rejection keeps invalid states from leaking forward.
    )
    private fun hasValidAttributes(markup: String, nameEnd: Int): Boolean {
        var index = nameEnd
        while (index < markup.length) {
            if (!markup[index].isWhitespace()) return false
            while (index < markup.length && markup[index].isWhitespace()) index++
            if (index == markup.length) return true

            val attributeStart = index
            while (index < markup.length && !markup[index].isWhitespace() && markup[index] != '=') index++
            val attributeName = markup.substring(attributeStart, index)
            if (!XML_QUALIFIED_NAME.matches(attributeName)) return false
            while (index < markup.length && markup[index].isWhitespace()) index++
            if (index == markup.length || markup[index] != '=') return false
            index++
            while (index < markup.length && markup[index].isWhitespace()) index++
            if (index == markup.length || (markup[index] != '"' && markup[index] != '\'')) return false

            val quote = markup[index++]
            val valueStart = index
            while (index < markup.length && markup[index] != quote) index++
            if (index == markup.length || '<' in markup.substring(valueStart, index)) return false
            if (markup.substring(valueStart, index).decodeXmlText() == null) return false
            index++
        }
        return true
    }

    @Suppress("ReturnCount") // Each early return rejects one distinct malformed XML state.
    private fun parseEndElement(): Boolean {
        val end = xml.indexOf('>', offset + 2)
        if (end < 0) return false
        val qualifiedName = xml.substring(offset + 2, end).trim()
        if (!XML_QUALIFIED_NAME.matches(qualifiedName) || openElements.lastOrNull()?.qualifiedName != qualifiedName) {
            return false
        }
        openElements.removeAt(openElements.lastIndex)
        offset = end + 1
        return true
    }
}

private data class XmlElement(
    val qualifiedName: String,
    val text: StringBuilder = StringBuilder(),
    val children: MutableList<XmlElement> = mutableListOf(),
) {
    val localName: String
        get() = qualifiedName.substringAfter(':')

    fun textValue(): String = text.toString().trim()
}

@Suppress("ReturnCount") // Each early return rejects one distinct malformed XML entity/text state.
private fun String.decodeXmlText(): String? {
    if (!hasOnlyValidXmlCharacters() || XML_CDATA_END in this) return null
    val decoded = StringBuilder(length)
    var start = 0
    while (start < length) {
        val entityStart = indexOf('&', start)
        if (entityStart < 0) {
            decoded.append(substring(start))
            break
        }
        decoded.append(substring(start, entityStart))
        val entityEnd = indexOf(';', entityStart + 1)
        if (entityEnd < 0) return null
        val entity = substring(entityStart + 1, entityEnd)
        decoded.append(entity.decodeXmlEntity() ?: return null)
        start = entityEnd + 1
    }
    return decoded.toString()
}

private fun String.decodeXmlEntity(): String? = when (this) {
    "amp" -> "&"
    "lt" -> "<"
    "gt" -> ">"
    "quot" -> "\""
    "apos" -> "'"
    else -> {
        val codePoint = when {
            startsWith("#x") -> substring(2).takeIf { digits ->
                digits.isNotEmpty() && digits.all { it.isAsciiHexDigit() }
            }?.toIntOrNull(XML_HEXADECIMAL_RADIX)
            startsWith("#") -> substring(1).takeIf { digits ->
                digits.isNotEmpty() && digits.all { it in '0'..'9' }
            }?.toIntOrNull()
            else -> null
        }
        codePoint?.toXmlCodePointString()
    }
}

private fun Char.isAsciiHexDigit(): Boolean =
    this in '0'..'9' || this in 'a'..'f' || this in 'A'..'F'

@Suppress("ReturnCount") // Invalid UTF-16 or XML code points terminate validation immediately.
private fun String.hasOnlyValidXmlCharacters(): Boolean {
    var index = 0
    while (index < length) {
        val character = this[index]
        when {
            character in '\uD800'..'\uDBFF' -> {
                if (index + 1 >= length || this[index + 1] !in '\uDC00'..'\uDFFF') return false
                index += 2
            }
            character in '\uDC00'..'\uDFFF' || !character.code.isValidXmlCodePoint() -> return false
            else -> index++
        }
    }
    return true
}

private fun Int.toXmlCodePointString(): String? = when {
    this.isValidXmlCodePoint() && this <= XML_MAX_VALID_BMP_CODE_POINT -> toChar().toString()
    this in XML_MIN_SUPPLEMENTARY_CODE_POINT..XML_MAX_CODE_POINT -> {
        val supplementary = this - XML_MIN_SUPPLEMENTARY_CODE_POINT
        val highSurrogate = (UTF16_HIGH_SURROGATE_BASE + (supplementary shr UTF16_SURROGATE_SHIFT)).toChar()
        val lowSurrogate = (UTF16_LOW_SURROGATE_BASE + (supplementary and UTF16_LOW_SURROGATE_MASK)).toChar()
        "$highSurrogate$lowSurrogate"
    }
    else -> null
}

private fun Int.isValidXmlCodePoint(): Boolean =
    this == XML_HORIZONTAL_TAB_CODE_POINT ||
            this == XML_LINE_FEED_CODE_POINT ||
            this == XML_CARRIAGE_RETURN_CODE_POINT ||
            this in XML_MIN_CONTENT_CODE_POINT..XML_PRE_SURROGATE_END_CODE_POINT ||
            this in XML_POST_SURROGATE_START_CODE_POINT..XML_MAX_VALID_BMP_CODE_POINT ||
            this in XML_MIN_SUPPLEMENTARY_CODE_POINT..XML_MAX_CODE_POINT

private const val XML_COMMENT_START = "<!--"
private const val XML_COMMENT_END = "-->"
private const val XML_PROCESSING_INSTRUCTION_START = "<?"
private const val XML_PROCESSING_INSTRUCTION_END = "?>"
private const val XML_CDATA_START = "<![CDATA["
private const val XML_CDATA_END = "]]>"
private const val XML_BYTE_ORDER_MARK = '\uFEFF'
private const val XML_HEXADECIMAL_RADIX = 16
private const val XML_HORIZONTAL_TAB_CODE_POINT = 0x9
private const val XML_LINE_FEED_CODE_POINT = 0xA
private const val XML_CARRIAGE_RETURN_CODE_POINT = 0xD
private const val XML_MIN_CONTENT_CODE_POINT = 0x20
private const val XML_PRE_SURROGATE_END_CODE_POINT = 0xD7FF
private const val XML_POST_SURROGATE_START_CODE_POINT = 0xE000
private const val XML_MAX_VALID_BMP_CODE_POINT = 0xFFFD
private const val XML_MIN_SUPPLEMENTARY_CODE_POINT = 0x10000
private const val XML_MAX_CODE_POINT = 0x10FFFF
private const val UTF16_HIGH_SURROGATE_BASE = 0xD800
private const val UTF16_LOW_SURROGATE_BASE = 0xDC00
private const val UTF16_SURROGATE_SHIFT = 10
private const val UTF16_LOW_SURROGATE_MASK = 0x3FF
private val XML_QUALIFIED_NAME = Regex("""[A-Za-z_][\w.-]*(?::[A-Za-z_][\w.-]*)?""")
