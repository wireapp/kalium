package com.wire.kalium.testservice.models

import com.wire.kalium.logic.CoreLogic
import com.wire.kalium.logic.data.id.QualifiedID

data class Instance(
    val backend: String,
    val clientId: String?,
    val instanceId: String,
    val name: String?,
    val coreLogic: CoreLogic?,
    val instancePath: String?,
    val password: String,
    val startupTime: Long?
)
