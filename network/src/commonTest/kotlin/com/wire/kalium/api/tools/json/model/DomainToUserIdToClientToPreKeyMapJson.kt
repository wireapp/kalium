package com.wire.kalium.api.tools.json.model

import com.wire.kalium.api.tools.json.ValidJsonProvider
import com.wire.kalium.network.api.prekey.DomainToUserIdToClientsToPreykeyMap
import com.wire.kalium.network.api.prekey.PreKey

object DomainToUserIdToClientToPreKeyMapJson {

    private const val DOMAIN_1 = "domain1.example.com"
    private const val DOMAIN_2 = "domain2.example.com"

    private const val USER_1 = "user10d0-000b-9c1a-000d-a4130002c221"
    private const val USER_1_CLIENT = "60f85e4b15ad3786"
    private val USER_1_CLIENT_PREYKEY = PreKey(key = "preKey1CoQBYIOjl7hw0D8YRNq", id = 1)

    private const val USER_2 = "user200d0-000b-9c1a-000d-a4130002c221"
    private const val USER_2_CLIENT = "32233lj33j3dfh7u"
    private val USER_2_CLIENT_PREYKEY = PreKey(key = "preKey2ANWARqEvoQI6l9hw0D", id = 2)

    private val jsonProvider = { _: DomainToUserIdToClientsToPreykeyMap ->
            """
            |{
            |  $DOMAIN_1: {
            |    $USER_1: {
            |      $USER_1_CLIENT: {
            |        "key": ${USER_1_CLIENT_PREYKEY.key},
            |        "id": ${USER_1_CLIENT_PREYKEY.id}
            |      }
            |    }
            |  },
            |  $DOMAIN_2: {
            |    $USER_2: {
            |      $USER_2_CLIENT: {
            |        "key": ${USER_2_CLIENT_PREYKEY.key},
            |        "id": ${USER_2_CLIENT_PREYKEY.id}
            |      }
            |    }
            |  }
            |}
            """.trimMargin()
    }

    val valid = ValidJsonProvider(
        mapOf(
            DOMAIN_1 to
                    mapOf(
                        USER_1 to
                                mapOf(USER_1_CLIENT to USER_1_CLIENT_PREYKEY)
                    ),
            DOMAIN_2 to
                    mapOf(
                        USER_2 to
                                mapOf(USER_2_CLIENT to USER_2_CLIENT_PREYKEY)
                    )
        ),
        jsonProvider
    )
}
