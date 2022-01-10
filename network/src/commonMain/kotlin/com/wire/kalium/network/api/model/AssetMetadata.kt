package com.wire.kalium.network.api.model

class AssetMetadata(
    val mimeType: String,
    val public: Boolean,
    val retentionType: AssetRetentionType,
    val md5: String
)
