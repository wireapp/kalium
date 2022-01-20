package com.wire.kalium.logic.data.asset

abstract class Asset

data class PublicAsset(val key: String) : Asset()
