package com.wire.kalium.logic.configuration

import kotlinx.serialization.Serializable

@Serializable
data class ClassifiedDomainsStatus(val isClassifiedDomainsEnabled: Boolean, val trustedDomains: List<String>)
