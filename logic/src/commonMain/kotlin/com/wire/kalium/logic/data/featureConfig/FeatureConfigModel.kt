/*
 * Wire
 * Copyright (C) 2023 Wire Swiss GmbH
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

import com.wire.kalium.logic.data.id.PlainId

data class FeatureConfigModel(
    val appLockModel: AppLockModel,
    val classifiedDomainsModel: ClassifiedDomainsModel,
    val conferenceCallingModel: ConferenceCallingModel,
    val conversationGuestLinksModel: ConfigsStatusModel,
    val digitalSignaturesModel: ConfigsStatusModel,
    val fileSharingModel: ConfigsStatusModel,
    val legalHoldModel: ConfigsStatusModel,
    val searchVisibilityModel: ConfigsStatusModel,
    val selfDeletingMessagesModel: SelfDeletingMessagesModel,
    val sndFactorPasswordChallengeModel: ConfigsStatusModel,
    val ssoModel: ConfigsStatusModel,
    val validateSAMLEmailsModel: ConfigsStatusModel,
    val mlsModel: MLSModel
)

enum class Status {
    ENABLED,
    DISABLED
}

data class AppLockModel(
    val config: AppLockConfigModel,
    val status: Status
)

data class AppLockConfigModel(
    val enforceAppLock: Boolean,
    val inactivityTimeoutSecs: Int
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
    val enforcedTimeoutSeconds: Int
)

data class MLSModel(
    val allowedUsers: List<PlainId>,
    val status: Status
)

data class ConferenceCallingModel(
    val status: Status
)
