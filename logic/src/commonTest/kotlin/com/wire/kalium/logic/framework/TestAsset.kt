package com.wire.kalium.logic.framework

import com.wire.kalium.logic.data.asset.UploadedAssetId
import io.ktor.utils.io.core.toByteArray

object TestAsset {

    fun mockedLongAssetData(): ByteArray =
        ("some VERY long long long long long long long long long long long long long long long long long long long long long long" +
                " long long long long long long long long long long long long long long long long long asset").toByteArray()

    val dummyUploadedAssetId = UploadedAssetId("some-asset-id", "some-domain", "some-asset-token")
}
