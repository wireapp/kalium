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
package com.wire.kalium.logic.data.e2ei

import com.wire.kalium.logic.E2EIFailure
import com.wire.kalium.logic.data.conversation.ClientId
import com.wire.kalium.logic.functional.Either

interface E2eiCertificateRepository {
    fun getE2eiCertificate(clientId: ClientId): Either<E2EIFailure, String>
}

class E2eiCertificateRepositoryImpl : E2eiCertificateRepository {
    override fun getE2eiCertificate(clientId: ClientId): Either<E2EIFailure, String> {
        // TODO get certificate from CoreCrypto
        return Either.Left(E2EIFailure(Exception()))
    }
}
