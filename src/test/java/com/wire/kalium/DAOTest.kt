package com.wire.kalium

import java.util.UUID
import com.wire.kalium.state.StatesDAO
import com.wire.kalium.crypto.storage.IdentitiesDAO
import org.junit.jupiter.api.Test

class DAOTest : DatabaseTestBase() {
    @Test
    fun testIdentitiesDAO() {
        val identitiesDAO: IdentitiesDAO = DatabaseTestBase.Companion.jdbi.onDemand<IdentitiesDAO?>(IdentitiesDAO::class.java)
        val id = UUID.randomUUID().toString()
        val insert = identitiesDAO.insert(id, id.toByteArray())
        val bytes = identitiesDAO[id].data
        val delete = identitiesDAO.delete(id)
    }

    @Test
    fun testStatesDAO() {
        val statesDAO: StatesDAO = DatabaseTestBase.Companion.jdbi.onDemand<StatesDAO?>(StatesDAO::class.java)
        val id = UUID.randomUUID()
        val text = "{\"some\" : \"text\"}"
        val insert = statesDAO.insert(id, text)
        val dbText = statesDAO[id]
        val delete = statesDAO.delete(id)
    }
}
