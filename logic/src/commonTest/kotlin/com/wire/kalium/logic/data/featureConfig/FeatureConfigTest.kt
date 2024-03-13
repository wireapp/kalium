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

import com.wire.kalium.logic.data.user.SupportedProtocol
import kotlinx.datetime.Instant

object FeatureConfigTest {

    @Suppress("LongParameterList")
    fun newModel(
        appLockModel: AppLockModel = AppLockModel(Status.ENABLED, 10),
        classifiedDomainsModel: ClassifiedDomainsModel = ClassifiedDomainsModel(
            ClassifiedDomainsConfigModel(listOf()),
            Status.ENABLED
        ),
        conferenceCallingModel: ConferenceCallingModel = ConferenceCallingModel(Status.ENABLED),
        conversationGuestLinksModel: ConfigsStatusModel = ConfigsStatusModel(Status.ENABLED),
        digitalSignaturesModel: ConfigsStatusModel = ConfigsStatusModel(Status.ENABLED),
        fileSharingModel: ConfigsStatusModel = ConfigsStatusModel(Status.ENABLED),
        guestRoomLink: ConfigsStatusModel = ConfigsStatusModel(Status.ENABLED),
        legalHoldModel: ConfigsStatusModel = ConfigsStatusModel(Status.ENABLED),
        searchVisibilityModel: ConfigsStatusModel = ConfigsStatusModel(Status.ENABLED),
        selfDeletingMessagesModel: SelfDeletingMessagesModel = SelfDeletingMessagesModel(
            SelfDeletingMessagesConfigModel(0),
            Status.ENABLED
        ),
        secondFactorPasswordChallengeModel: ConfigsStatusModel = ConfigsStatusModel(Status.ENABLED),
        ssoModel: ConfigsStatusModel = ConfigsStatusModel(Status.ENABLED),
        validateSAMLEmailsModel: ConfigsStatusModel = ConfigsStatusModel(Status.ENABLED),
        mlsModel: MLSModel = MLSModel(SupportedProtocol.PROTEUS, setOf(SupportedProtocol.PROTEUS), Status.ENABLED),
        e2EIModel: E2EIModel = E2EIModel(E2EIConfigModel("url", 10000L), Status.ENABLED),
        mlsMigrationModel: MLSMigrationModel? = MLSMigrationModel(
            Instant.DISTANT_FUTURE,
            Instant.DISTANT_FUTURE,
            Status.ENABLED
        )
    ): FeatureConfigModel = FeatureConfigModel(
        appLockModel,
        classifiedDomainsModel,
        conferenceCallingModel,
        conversationGuestLinksModel,
        digitalSignaturesModel,
        fileSharingModel,
        guestRoomLink,
        legalHoldModel,
        searchVisibilityModel,
        selfDeletingMessagesModel,
        secondFactorPasswordChallengeModel,
        ssoModel,
        validateSAMLEmailsModel,
        mlsModel,
        e2EIModel,
        mlsMigrationModel
    )
}
