package com.wire.kalium.logic.util

import com.wire.kalium.logic.data.asset.AssetType
import com.wire.kalium.logic.data.asset.FileAsset
import com.wire.kalium.logic.data.asset.ImageAsset
import kotlin.contracts.ExperimentalContracts
import kotlin.contracts.contract

internal fun Boolean.toInt() = if (this) 1 else 0

fun String.fileExtension(): String {
    val splitElements = split(".")
    val extension: String = when {
        splitElements.size <= 1 -> this
        splitElements.size == 2 -> splitElements[1]
        else -> splitElements.subList(1, splitElements.size).joinToString(".")
    }
    return extension
}

fun String.fileExtensionToAssetType(): AssetType = when (this) {
    ImageAsset.JPEG.mimeType -> ImageAsset.JPEG
    ImageAsset.JPG.mimeType -> ImageAsset.JPG
    ImageAsset.PNG.mimeType -> ImageAsset.PNG
    else -> FileAsset("file/$this")
}

@OptIn(ExperimentalContracts::class)
fun Int?.isGreaterThan(other: Int?): Boolean {
    contract {
        returns(true) implies (this@isGreaterThan != null)
        returns(true) implies (other != null)
    }

    return this is Int && other is Int && this > other
}
