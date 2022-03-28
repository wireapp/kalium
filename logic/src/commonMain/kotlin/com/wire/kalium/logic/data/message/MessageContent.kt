package com.wire.kalium.logic.data.message

sealed class MessageContent {

    data class Text(val value: String) : MessageContent()

    data class Calling(val value: String) : MessageContent()

    sealed class AssetContent : MessageContent() {
        data class ImageAsset(val value: AssetProtoContent) : AssetContent()
        data class FileAsset(val value: AssetProtoContent) : AssetContent()
    }

    object Unknown : MessageContent()
}
