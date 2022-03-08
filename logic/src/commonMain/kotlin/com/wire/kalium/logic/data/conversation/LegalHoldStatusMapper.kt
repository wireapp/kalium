package com.wire.kalium.logic.data.conversation

import com.wire.kalium.network.api.user.LegalHoldStatusResponse

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
