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
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull

class OpenGraphScannerTest {

    private val originalUrl = "https://example.com/page"

    @Test
    fun givenStandardOgPageWithAllFields_whenScanning_thenReturnsCompleteData() {
        val html = """
            <head>
                <title>Page Title</title>
                <meta property="og:title" content="Open Graph Title" />
                <meta property="og:type" content="article" />
                <meta property="og:url" content="https://example.com/og-url" />
                <meta property="og:description" content="This is the description" />
                <meta property="og:site_name" content="Example Site" />
                <meta property="og:image" content="https://example.com/image1.jpg" />
                <meta property="og:image" content="https://example.com/image2.jpg" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("Open Graph Title", result.title)
        assertEquals("article", result.type)
        assertEquals("https://example.com/og-url", result.url)
        assertEquals("This is the description", result.description)
        assertEquals("Example Site", result.siteName)
        assertEquals(listOf("https://example.com/image1.jpg"), result.imageUrls)
    }

    @Test
    fun givenMetaTagsWithReversedAttributeOrder_whenScanning_thenParseSuccessfully() {
        val html = """
            <head>
                <meta content="Open Graph Title" property="og:title" />
                <meta content="Article description" property="og:description" />
                <meta content="https://example.com/url" property="og:url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("Open Graph Title", result.title)
        assertEquals("Article description", result.description)
        assertEquals("https://example.com/url", result.url)
    }

    @Test
    fun givenMetaTagsWithExtraAttributes_whenScanning_thenIgnoreExtraAttributesAndParse() {
        val html = """
            <head>
                <meta property="og:title" content="Title" id="test" data-value="extra" />
                <meta property="og:url" content="https://example.com/url" class="meta-tag" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("Title", result.title)
        assertEquals("https://example.com/url", result.url)
    }

    @Test
    fun givenSelfClosingAndMalformedHeadTags_whenScanning_thenParseSuccessfully() {
        val html = """
            <head>
                <meta property="og:title" content="Title" />
                <meta property="og:url" content="https://example.com/url">
                <meta property="og:description" content="Description" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("Title", result.title)
    }

    @Test
    fun givenMissingOgTitleWithTitleTag_whenScanning_thenFallbackToTitle() {
        val html = """
            <head>
                <title>Page Title</title>
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("Page Title", result.title)
        assertEquals("https://example.com/url", result.url)
    }

    @Test
    fun givenMissingOgDescriptionWithMetaDescription_whenScanning_thenFallbackToMetaDescription() {
        val html = """
            <head>
                <title>Title</title>
                <meta name="description" content="Meta description" />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("Meta description", result.description)
    }

    @Test
    fun givenOgDescriptionPresent_whenScanningWithMetaDescription_thenPreferOgOverMeta() {
        val html = """
            <head>
                <title>Title</title>
                <meta name="description" content="Meta description" />
                <meta property="og:description" content="OG description" />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("OG description", result.description)
    }

    @Test
    fun givenMissingOgUrl_whenScanning_thenFallbackToOriginalUrl() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:description" content="Description" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals(originalUrl, result.url)
    }

    @Test
    fun givenMultipleOgImageTags_whenScanning_thenUsesFirstImageOnly() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:image" content="https://example.com/img1.jpg" />
                <meta property="og:image" content="https://example.com/img2.jpg" />
                <meta property="og:image" content="https://example.com/img3.jpg" />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals(1, result.imageUrls.size)
        assertEquals(
            listOf("https://example.com/img1.jpg"),
            result.imageUrls
        )
    }

    @Test
    fun givenHtmlEntitiesInContent_whenScanning_thenDecodeEntitiesCorrectly() {
        val html = """
            <head>
                <meta property="og:title" content="Title with &quot;quotes&quot; &amp; ampersand" />
                <meta property="og:description" content="Description with &lt;tag&gt; and &apos;apostrophe&apos;" />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("""Title with "quotes" & ampersand""", result.title)
        assertEquals("""Description with <tag> and 'apostrophe'""", result.description)
    }

    @Test
    fun givenDefaultTypeWhenMissing_whenScanning_thenReturnsWebsiteType() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("website", result.type)
    }

    @Test
    fun givenExplicitType_whenScanning_thenReturnsThatType() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:type" content="article" />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("article", result.type)
    }

    @Test
    fun givenMissingRequiredTitle_whenScanning_thenReturnsNull() {
        val html = """
            <head>
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNull(result)
    }

    @Test
    fun givenMissingUrl_whenScanning_thenFallbackToOriginalUrl() {
        val html = """
            <head>
                <title>Title</title>
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals(originalUrl, result.url)
    }

    @Test
    fun givenBlankUrl_whenScanning_thenFallbackToOriginalUrl() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:url" content="" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals(originalUrl, result.url)
    }

    @Test
    fun givenBlankTitle_whenScanning_thenReturnsNull() {
        val html = """
            <head>
                <meta property="og:title" content="   " />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNull(result)
    }

    @Test
    fun givenPartialHtml_whenScanning_thenParseSuccessfully() {
        val html = """
            <meta property="og:title" content="Title" />
            <meta property="og:url" content="https://example.com/url" />
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("Title", result.title)
    }

    @Test
    fun givenInvalidHtml_whenScanning_thenReturnsNull() {
        val html = "<malformed html content without proper tags"

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNull(result)
    }

    @Test
    fun givenMetaNameDescriptionWithDifferentCase_whenScanning_thenParseSuccessfully() {
        val html = """
            <head>
                <title>Title</title>
                <meta name="Description" content="Meta description" />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("Meta description", result.description)
    }

    @Test
    fun givenEmptyContent_whenScanning_thenIgnoresEmptyValues() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:image" content="" />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals(0, result.imageUrls.size)
    }

    @Test
    fun givenWhitespaceInContent_whenScanning_thenTrimsAndProcesses() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:url" content="  https://example.com/url  " />
                <meta property="og:description" content="  Description with spaces  " />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("https://example.com/url", result.url)
        assertEquals("Description with spaces", result.description)
    }

    @Test
    fun givenMultilineContent_whenScanning_thenParsesSuccessfully() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:description" content="Multi-line description content" />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("Multi-line description content", result.description)
    }

    @Test
    fun givenSiteNamePresent_whenScanning_thenIncludesSiteName() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:site_name" content="My Website" />
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("My Website", result.siteName)
    }

    @Test
    fun givenNoSiteName_whenScanning_thenSiteNameIsNull() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:url" content="https://example.com/url" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertNull(result.siteName)
    }

    @Test
    fun givenMixedOgAndNonOgMetaTags_whenScanning_thenExtractsOnlyOgAndDescription() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:title" content="OG Title" />
                <meta property="og:url" content="https://example.com/url" />
                <meta property="viewport" content="width=device-width" />
                <meta name="keywords" content="some keywords" />
                <meta name="author" content="John Doe" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("OG Title", result.title)
        assertEquals("https://example.com/url", result.url)
        assertNull(result.description)
    }

    @Test
    fun givenComplexHeadWithScriptsAndStyles_whenScanning_thenIgnoresNonMetaTags() {
        val html = """
            <head>
                <title>Title</title>
                <meta property="og:title" content="OG Title" />
                <meta property="og:url" content="https://example.com/url" />
                <script>console.log('test');</script>
                <style>body { color: red; }</style>
                <link rel="stylesheet" href="style.css" />
            </head>
        """.trimIndent()

        val result = OpenGraphScanner.scan(html, originalUrl)

        assertNotNull(result)
        assertEquals("OG Title", result.title)
    }
}
