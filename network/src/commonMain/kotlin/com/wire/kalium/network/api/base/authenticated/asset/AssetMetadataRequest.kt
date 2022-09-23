package com.wire.kalium.network.api.base.authenticated.asset

import com.wire.kalium.network.api.base.model.AssetRetentionType

class AssetMetadataRequest(
    val mimeType: String,
    val public: Boolean,
    val retentionType: AssetRetentionType,
    val md5: String
)
