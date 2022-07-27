package com.wire.kalium.logic.data.id

import com.wire.kalium.persistence.kmm_settings.KaliumPreferences
import com.wire.kalium.logic.configuration.server.CURRENT_DOMAIN
import com.wire.kalium.logic.configuration.server.FEDERATION_ENABLED
import com.wire.kalium.logic.data.user.UserRepository
import com.wire.kalium.logic.kaliumLogger

interface FederatedIdMapper {
    fun parseToFederatedId(qualifiedID: QualifiedID): String
    fun parseToFederatedId(qualifiedStringID: String): String
}

/**
 * Mapper that enables parsing [QualifiedID] into a [String] having in consideration federation.
 *
 * In detail, if [isFederationEnabled] is [true] then the full qualified form will be used
 * otherwise the plain value will be used
 */
class FederatedIdMapperImpl(
    private val userRepository: UserRepository,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val kaliumPreferences: KaliumPreferences,
    ) : FederatedIdMapper {

    private fun isFederationEnabled() = kaliumPreferences.getBoolean(FEDERATION_ENABLED, false)
    private fun getCurrentDomain() = kaliumPreferences.getString(CURRENT_DOMAIN) ?: userRepository.getSelfUserId()?.domain

    override fun parseToFederatedId(qualifiedID: QualifiedID): String {
        kaliumLogger.v(
            "Parsing stringId: $qualifiedID, is federationEnabled? ${isFederationEnabled()} and with domain? ${getCurrentDomain()}"
        )
        return if (isFederationEnabled() && qualifiedID.domain.isNotEmpty()) {
            qualifiedID.toString()
        } else {
            qualifiedID.value
        }
    }

    override fun parseToFederatedId(qualifiedStringID: String): String {
        val parsedQualifiedID = qualifiedIdMapper.fromStringToQualifiedID(qualifiedStringID)
        kaliumLogger.v(
            "Parsing stringId: $parsedQualifiedID, is federationEnabled? ${isFederationEnabled()} and with domain? ${getCurrentDomain()}"
        )
        return if (isFederationEnabled()!! && parsedQualifiedID.domain.isNotEmpty()) {
            parsedQualifiedID.toString()
        } else {
            parsedQualifiedID.value
        }
    }
}
