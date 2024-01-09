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

package com.wire.kalium.api.json.model

import com.wire.kalium.network.api.base.model.QualifiedID
import com.wire.kalium.network.api.base.model.SessionDTO

val testCredentials = SessionDTO(
    userId = QualifiedID(value = "d0d92ba6-ab9e-4db2-b94f-475951ef219a", domain = "domain.de"),
    tokenType = "Bearer",
    accessToken = "eyJhbGciOiJIUzI1AnwarInR5cCI6IkpXVCJ9.eyJsb2dnZWRJbkFzIjoiYWRtaW4iLCJpYXQiO" +
            "jE0MjI3Nzk2Mz69.gzSraSYS8EXBxLN_oWnFSRgCzcmJmMjLiuyu5CSpyHI",
    refreshToken = "a123bGciOiJIUzI1NiIsInR5cCI6IkpX2fr9.eyJzdWIiOiIxMjM0NTY3ODkwIiwibmFtZSI6I" +
            "k420G4gRG9lIiwiYWRtaW4iOnRydWV9.TJVA95OrM7E2cBab30RMHrHDcEfxjoYZgeFONFh7HgQ",
    cookieLabel = "cookieLabel"
)
