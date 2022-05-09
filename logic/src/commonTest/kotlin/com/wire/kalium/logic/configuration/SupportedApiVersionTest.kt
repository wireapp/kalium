package com.wire.kalium.logic.configuration

import com.wire.kalium.logic.util.SupportedApiVersions
import kotlin.test.Test
import kotlin.test.fail

class SupportedApiVersionTest {


    @Test
    fun givenEmptySupportedApiVersionList_thenFail() {
        if (SupportedApiVersions.isNullOrEmpty()) fail()
    }
}
