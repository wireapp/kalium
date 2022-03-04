package com.wire.kalium.network.api.asset

import com.wire.kalium.network.api.model.AssetRetentionType

class AssetMetadataRequest(
    val mimeType: String,
    val public: Boolean,
    val retentionType: AssetRetentionType,
    val md5: String
)
