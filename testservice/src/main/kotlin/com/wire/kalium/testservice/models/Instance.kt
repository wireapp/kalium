package com.wire.kalium.testservice.models

import com.wire.kalium.logic.CoreLogic

data class Instance(
    val backend: String,
    val clientId: String,
    val instanceId: String,
    val name: String?,
    val coreLogic: CoreLogic?,
    val instancePath: String?
)
