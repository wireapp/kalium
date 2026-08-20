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

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class CellsS3XmlTest {

    @Test
    fun givenLeadingBomBeforeXmlDeclaration_whenReadingMultipartUploadId_thenParsesResponse() {
        val xml = "\uFEFF<?xml version=\"1.0\"?>" +
                "<InitiateMultipartUploadResult><UploadId>upload-id</UploadId></InitiateMultipartUploadResult>"

        assertEquals("upload-id", xml.multipartUploadId())
    }

    @Test
    fun givenLeadingBom_whenCheckingCompletionResult_thenParsesResponse() {
        val xml = "\uFEFF<s3:CompleteMultipartUploadResult xmlns:s3=\"urn:s3\"/>"

        assertTrue(xml.containsCompleteMultipartUploadResult())
    }

    @Test
    fun givenLeadingBom_whenReadingRootError_thenParsesCode() {
        val xml = "\uFEFF<s3:Error xmlns:s3=\"urn:s3\"><s3:Code>SlowDown</s3:Code></s3:Error>"

        assertEquals(S3Error("SlowDown"), xml.embeddedS3Error())
    }

    @Test
    fun givenBomOutsideLegalDocumentStart_whenReadingXml_thenRejectsResponse() {
        val validResult = "<CompleteMultipartUploadResult/>"
        val invalidDocuments = listOf(
            "\uFEFF\uFEFF$validResult",
            " \uFEFF$validResult",
            "$validResult\uFEFF",
        )

        invalidDocuments.forEach { xml ->
            assertFalse(xml.containsCompleteMultipartUploadResult())
        }
    }

    @Test
    fun givenGreaterThanInQuotedAttributes_whenReadingTagValue_thenParsesTheCompleteStartElement() {
        val xml = """
            <?xml version="1.0"?>
            <s3:InitiateMultipartUploadResult
                xmlns:s3="http://s3.amazonaws.com/doc/2006-03-01/"
                condition="size > 0"
            >
                <s3:UploadId description='value > threshold'><![CDATA[upload&raw-id]]></s3:UploadId>
            </s3:InitiateMultipartUploadResult>
        """.trimIndent()

        assertEquals("upload&raw-id", xml.multipartUploadId())
    }

    @Test
    fun givenCommentedAndNestedErrors_whenReadingEmbeddedError_thenRequiresErrorAtDocumentRoot() {
        val xml = """
            <CompleteMultipartUploadResult>
                <!-- <Error><Code>SlowDown</Code></Error> -->
                <Metadata><Error><Code>SlowDown</Code></Error></Metadata>
            </CompleteMultipartUploadResult>
        """.trimIndent()

        assertNull(xml.embeddedS3Error())
        assertTrue(xml.containsCompleteMultipartUploadResult())
    }

    @Test
    fun givenCDataCodeInsideRootError_whenReadingEmbeddedError_thenReturnsDecodedCode() {
        val xml = """
            <s3:Error xmlns:s3="http://s3.amazonaws.com/doc/2006-03-01/">
                <Metadata><Code>SlowDown</Code></Metadata>
                <s3:Code><![CDATA[InvalidPart]]></s3:Code>
            </s3:Error>
        """.trimIndent()

        assertEquals(S3Error("InvalidPart"), xml.embeddedS3Error())
    }

    @Test
    fun givenMismatchedNamespacePrefixes_whenReadingXml_thenRejectsTheDocument() {
        val xml = """
            <s3:InitiateMultipartUploadResult>
                <s3:UploadId>upload-id</other:UploadId>
            </s3:InitiateMultipartUploadResult>
        """.trimIndent()

        assertNull(xml.multipartUploadId())
        assertNull(xml.embeddedS3Error())
    }

    @Test
    fun givenMalformedQuotedAttribute_whenReadingXml_thenRejectsTheDocument() {
        val xml = """
            <InitiateMultipartUploadResult>
                <UploadId description="unterminated>upload-id</UploadId>
            </InitiateMultipartUploadResult>
        """.trimIndent()

        assertNull(xml.multipartUploadId())
        assertFalse(xml.containsCompleteMultipartUploadResult())
    }

    @Test
    fun givenValidSupplementaryNumericReference_whenReadingTagValue_thenDecodesSurrogatePair() {
        val xml = """
            <InitiateMultipartUploadResult>
                <UploadId>upload-&#x1F600;-id</UploadId>
            </InitiateMultipartUploadResult>
        """.trimIndent()

        assertEquals("upload-\uD83D\uDE00-id", xml.multipartUploadId())
    }

    @Test
    fun givenInvalidXmlNumericReferences_whenReadingTagValue_thenRejectsTheDocument() {
        val invalidReferences = listOf("&#0;", "&#xD800;", "&#xFFFE;", "&#x110000;")

        invalidReferences.forEach { reference ->
            val xml = "<InitiateMultipartUploadResult><UploadId>$reference</UploadId></InitiateMultipartUploadResult>"
            assertNull(xml.multipartUploadId())
        }
    }

    @Test
    fun givenUploadIdOutsideExpectedRootOrNested_whenReadingMultipartUploadId_thenRejectsIt() {
        val unrelatedRoot = "<Response><UploadId>upload-id</UploadId></Response>"
        val nestedUploadId = """
            <InitiateMultipartUploadResult>
                <Metadata><UploadId>upload-id</UploadId></Metadata>
            </InitiateMultipartUploadResult>
        """.trimIndent()

        assertNull(unrelatedRoot.multipartUploadId())
        assertNull(nestedUploadId.multipartUploadId())
    }

    @Test
    fun givenInvalidLiteralXmlCharacters_whenReadingMultipartUploadId_thenRejectsTheDocument() {
        val invalidDocuments = listOf(
            "<InitiateMultipartUploadResult><UploadId>\u0001</UploadId></InitiateMultipartUploadResult>",
            "<InitiateMultipartUploadResult><UploadId><![CDATA[\uD800]]></UploadId></InitiateMultipartUploadResult>",
            "<InitiateMultipartUploadResult marker=\"\uFFFE\"><UploadId>id</UploadId></InitiateMultipartUploadResult>",
        )

        invalidDocuments.forEach { xml ->
            assertNull(xml.multipartUploadId())
        }
    }

    @Test
    fun givenSignedNumericReferences_whenReadingMultipartUploadId_thenRejectsTheDocument() {
        val signedReferences = listOf("&#+65;", "&#x+41;")

        signedReferences.forEach { reference ->
            val xml = "<InitiateMultipartUploadResult><UploadId>$reference</UploadId></InitiateMultipartUploadResult>"
            assertNull(xml.multipartUploadId())
        }
    }

    @Test
    fun givenCDataTerminatorInRegularText_whenReadingMultipartUploadId_thenRejectsTheDocument() {
        val xml = """
            <InitiateMultipartUploadResult>
                <UploadId>upload]]>id</UploadId>
            </InitiateMultipartUploadResult>
        """.trimIndent()

        assertNull(xml.multipartUploadId())
    }

    @Test
    fun givenResultMarkupOnlyInComment_whenCheckingCompletionResult_thenDoesNotDetectIt() {
        val xml = """
            <Response>
                <!-- <CompleteMultipartUploadResult status="complete"/> -->
            </Response>
        """.trimIndent()

        assertFalse(xml.containsCompleteMultipartUploadResult())
    }
}
