package com.wire.kalium.api.tools.json

import com.wire.kalium.network.SupportedApiVersions
import kotlin.test.Test
import kotlin.test.fail

class SupportedApiVersionTest {


    @Test
    fun givenEmptySupportedApiVersionList_thenFail() {
        if (SupportedApiVersions.isNullOrEmpty()) fail()
    }
}
