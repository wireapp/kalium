package com.wire.kalium.logic.data.id

import com.wire.kalium.logic.configuration.server.ServerConfigRepository
import com.wire.kalium.logic.data.session.SessionRepository
import com.wire.kalium.logic.data.user.UserId
import com.wire.kalium.logic.functional.Either
import com.wire.kalium.logic.functional.fold
import com.wire.kalium.logic.kaliumLogger

interface FederatedIdMapper {
    suspend fun parseToFederatedId(qualifiedID: QualifiedID): String
    suspend fun parseToFederatedId(qualifiedStringID: String): String
}

/**
 * Mapper that enables parsing [QualifiedID] into a [String] having in consideration federation.
 *
 * In detail, if [isFederationEnabled] is [true] then the full qualified form will be used
 * otherwise the plain value will be used
 */
class FederatedIdMapperImpl internal constructor(
    private val selfUserId: UserId,
    private val qualifiedIdMapper: QualifiedIdMapper,
    private val sessionRepository: SessionRepository,
    private val serverConfigRepository: ServerConfigRepository,
) : FederatedIdMapper {

    private fun isFederationEnabled() = when (val session = sessionRepository.userSession(selfUserId)) {
        is Either.Left -> false
        is Either.Right -> {
            serverConfigRepository.configByLinks(session.value.serverLinks).fold({ false }, { config ->
                config.metaData.federation
            })
        }
    }

    private fun getCurrentDomain() = selfUserId.domain

    override suspend fun parseToFederatedId(qualifiedID: QualifiedID): String {
        kaliumLogger.v("Parsing stringId: $qualifiedID | FederationEnabled? ${isFederationEnabled()} | Domain? ${getCurrentDomain()}")
        return if (isFederationEnabled() && qualifiedID.domain.isNotEmpty()) {
            qualifiedID.toString()
        } else {
            qualifiedID.value
        }
    }

    override suspend fun parseToFederatedId(qualifiedStringID: String): String {
        val parsedQualifiedID = qualifiedIdMapper.fromStringToQualifiedID(qualifiedStringID)
        kaliumLogger.v("Parsing stringId: $parsedQualifiedID | FederationEnabled? ${isFederationEnabled()} | Domain? ${getCurrentDomain()}")
        return if (isFederationEnabled() && parsedQualifiedID.domain.isNotEmpty()) {
            parsedQualifiedID.toString()
        } else {
            parsedQualifiedID.value
        }
    }
}
