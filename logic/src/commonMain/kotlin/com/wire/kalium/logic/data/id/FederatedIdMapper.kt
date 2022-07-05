package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.configuration.server.CURRENT_DOMAIN
import com.wire.kalium.logic.configuration.server.FEDERATION_ENABLED
import com.wire.kalium.logic.kaliumLogger
import com.wire.kalium.persistence.kmm_settings.KaliumPreferences

/**
 * Mapper that enables parsing [QualifiedID] into a [String] having in consideration federation.
 *
 * In detail, if [isFederationEnabled] is [true] then the full qualified form will be used
 * otherwise the plain value will be used
 */
class FederatedIdMapper(private val kaliumPreferences: KaliumPreferences) {

    private fun isFederationEnabled() = kaliumPreferences.getBoolean(FEDERATION_ENABLED, false)
    private fun getCurrentDomain() = kaliumPreferences.getString(CURRENT_DOMAIN) ?: "wire.com"

    fun parseToFederatedId(qualifiedID: QualifiedID): String {
        kaliumLogger.d(
            "Parsing stringId: $qualifiedID, is federationEnabled? ${isFederationEnabled()} and with domain? ${getCurrentDomain()}"
        )
        return if (isFederationEnabled() && qualifiedID.domain.isNotEmpty()) {
            qualifiedID.toString()
        } else {
            qualifiedID.value
        }
    }

    fun parseToFederatedId(qualifiedStringID: String): String {
        val parsedQualifiedID = qualifiedStringID.parseIntoQualifiedID()
        kaliumLogger.d(
            "Parsing stringId: $parsedQualifiedID, is federationEnabled? ${isFederationEnabled()} and with domain? ${getCurrentDomain()}"
        )
        return if (isFederationEnabled() && parsedQualifiedID.domain.isNotEmpty()) {
            parsedQualifiedID.toString()
        } else {
            parsedQualifiedID.value
        }
    }
}
