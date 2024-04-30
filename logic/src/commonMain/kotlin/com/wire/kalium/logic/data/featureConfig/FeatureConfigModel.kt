/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

package com.wire.kalium.logic.data.featureConfig

import com.wire.kalium.logic.data.mls.SupportedCipherSuite
import com.wire.kalium.logic.data.user.SupportedProtocol
import com.wire.kalium.util.time.Second
import kotlinx.datetime.Instant

data class FeatureConfigModel(
    val appLockModel: AppLockModel,
    val classifiedDomainsModel: ClassifiedDomainsModel,
    val conferenceCallingModel: ConferenceCallingModel,
    val conversationGuestLinksModel: ConfigsStatusModel,
    val digitalSignaturesModel: ConfigsStatusModel,
    val fileSharingModel: ConfigsStatusModel,
    val guestRoomLinkModel: ConfigsStatusModel,
    val legalHoldModel: ConfigsStatusModel,
    val searchVisibilityModel: ConfigsStatusModel,
    val selfDeletingMessagesModel: SelfDeletingMessagesModel,
    val secondFactorPasswordChallengeModel: ConfigsStatusModel,
    val ssoModel: ConfigsStatusModel,
    val validateSAMLEmailsModel: ConfigsStatusModel,
    val mlsModel: MLSModel,
    val e2EIModel: E2EIModel,
    val mlsMigrationModel: MLSMigrationModel?
)

enum class Status {
    ENABLED,
    DISABLED;

    fun toBoolean(): Boolean = this == ENABLED
}

data class AppLockModel(
    val status: Status,
    val inactivityTimeoutSecs: Second
)

data class ClassifiedDomainsModel(
    val config: ClassifiedDomainsConfigModel,
    val status: Status
)

data class ClassifiedDomainsConfigModel(
    val domains: List<String>
)

data class ConfigsStatusModel(
    val status: Status
)

data class SelfDeletingMessagesModel(
    val config: SelfDeletingMessagesConfigModel,
    val status: Status
)

data class SelfDeletingMessagesConfigModel(
    val enforcedTimeoutSeconds: Long?
)

data class MLSModel(
    val defaultProtocol: SupportedProtocol,
    val supportedProtocols: Set<SupportedProtocol>,
    val supportedCipherSuite: SupportedCipherSuite?,
    val status: Status
)

data class MLSMigrationModel(
    val startTime: Instant?,
    val endTime: Instant?,
    val status: Status
)

data class ConferenceCallingModel(
    val status: Status
)

data class E2EIModel(
    val config: E2EIConfigModel,
    val status: Status
)

data class E2EIConfigModel(
    val discoverUrl: String?,
    val verificationExpirationSeconds: Long
)
