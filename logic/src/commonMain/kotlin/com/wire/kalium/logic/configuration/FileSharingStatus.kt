package com.wire.kalium.logic.configuration

import kotlinx.serialization.Serializable

@Serializable
data class FileSharingStatus(val isFileSharingEnabled: Boolean?, val isStatusChanged: Boolean?)
