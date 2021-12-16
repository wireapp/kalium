package com.wire.kalium.api.tools.json.model

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.prekey.DomainToUserIdToClientsMap

object DomainToUserIdToClientsMapJson {

    private const val DOMAIN_1 = "domain1.example.com"

    private const val USER_1 = "user10d0-000b-9c1a-000d-a4130002c221"
    private const val USER_1_client_1 = "60f85e4b15ad3786"
    private const val USER_1_client_2 = "6e323ab31554353b"

    private const val USER_2 = "user200d0-000b-9c1a-000d-a4130002c221"
    private const val USER_2_client_1 = "32233lj33j3dfh7u"
    private const val USER_2_client_2 = "duf3eif09324wq5j"

    private val jsonProvider = { _ : DomainToUserIdToClientsMap ->
        """
        |{
        |  "$DOMAIN_1": {
        |    "$USER_1": [
        |      "$USER_1_client_1",
        |      "$USER_1_client_2"
        |    ],
        |    "$USER_2": [
        |      "$USER_2_client_1",
        |      "$USER_2_client_2"
        |    ]
        |  }
        |}
        """.trimMargin()
    }

    val valid = ValidJsonProvider(
        mapOf(
            DOMAIN_1 to
                    mapOf(
                        USER_1 to listOf(USER_1_client_1, USER_1_client_2),
                        USER_2 to listOf(USER_2_client_1, USER_2_client_2)
                    )
        ),
        jsonProvider
    )
}
