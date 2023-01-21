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
