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

package com.wire.kalium.logic.data.conversation

import com.wire.kalium.network.api.base.model.LegalHoldStatusResponse

interface LegalHoldStatusMapper {
    fun fromApiModel(legalHoldStatusResponse: LegalHoldStatusResponse): LegalHoldStatus
}

class LegalHoldStatusMapperImp : LegalHoldStatusMapper {
    override fun fromApiModel(legalHoldStatusResponse: LegalHoldStatusResponse): LegalHoldStatus =
        when (legalHoldStatusResponse) {
            LegalHoldStatusResponse.ENABLED -> LegalHoldStatus.ENABLED
            LegalHoldStatusResponse.PENDING -> LegalHoldStatus.PENDING
            LegalHoldStatusResponse.DISABLED -> LegalHoldStatus.DISABLED
            LegalHoldStatusResponse.NO_CONSENT -> LegalHoldStatus.NO_CONSENT
        }
}
