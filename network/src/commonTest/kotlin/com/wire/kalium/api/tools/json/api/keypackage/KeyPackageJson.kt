package com.wire.kalium.api.tools.json.api.keypackage

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.keypackage.ClaimedKeyPackageList
import com.wire.kalium.network.api.keypackage.KeyPackageDTO
import com.wire.kalium.network.api.prekey.PreKeyDTO

object KeyPackageJson {

    val valid = ValidJsonProvider(
        ClaimedKeyPackageList(listOf(
            KeyPackageDTO("defkrr8e7grgsoufhg8",
            "wire.com",
            "keyPackage",
            "keyPackageRef",
            "fdf23116-42a5-472c-8316-e10655f5d11e")
        ))
    ) {
        """
        |{
        |  "key_packages": [
        |     {
        |        "user": "${it.keyPackages[0].userId}",
        |        "client": "${it.keyPackages[0].clientID}",
        |        "domain": "${it.keyPackages[0].domain}",
        |        "key_package": "${it.keyPackages[0].keyPackage}",
        |        "key_package_ref": "${it.keyPackages[0].keyPackageRef}"
        |     }
        |  ]
        |}
        """.trimMargin()
    }
}
