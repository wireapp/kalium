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

class S3UrlBuilderTest {

    @Test
    fun givenObjectKeyAndQueryParameters_whenBuildingUrl_thenAwsEncodingAndSortingAreApplied() {
        val result = S3UrlBuilder.build(
            endpoint = "https://cells.example.test/base/",
            bucket = "io",
            objectKey = "folder/a file/ü.txt",
            queryParameters = listOf(
                S3QueryParameter("uploadId", "upload/id value"),
                S3QueryParameter("partNumber", "1"),
                S3QueryParameter("uploads", ""),
            ),
        )

        assertEquals("/base/io/folder/a%20file/%C3%BC.txt", result.canonicalUri)
        assertEquals("partNumber=1&uploadId=upload%2Fid%20value&uploads=", result.canonicalQueryString)
        assertEquals(
            "https://cells.example.test/base/io/folder/a%20file/%C3%BC.txt" +
                    "?partNumber=1&uploadId=upload%2Fid%20value&uploads=",
            result.url,
        )
        assertEquals("cells.example.test", result.hostHeader)
    }
}
