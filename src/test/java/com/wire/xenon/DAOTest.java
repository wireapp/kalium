package com.wire.xenon;

import com.wire.xenon.crypto.storage.IdentitiesDAO;
import com.wire.xenon.state.StatesDAO;
import org.junit.jupiter.api.Test;

import java.util.UUID;

public class DAOTest extends DatabaseTestBase {
    @Test
    public void testIdentitiesDAO() {
        final IdentitiesDAO identitiesDAO = jdbi.onDemand(IdentitiesDAO.class);
        final String id = UUID.randomUUID().toString();

        final int insert = identitiesDAO.insert(id, id.getBytes());
        final byte[] bytes = identitiesDAO.get(id).data;
        final int delete = identitiesDAO.delete(id);
    }

    @Test
    public void testStatesDAO() {
        final StatesDAO statesDAO = jdbi.onDemand(StatesDAO.class);
        final UUID id = UUID.randomUUID();
        final String text = "{\"some\" : \"text\"}";

        final int insert = statesDAO.insert(id, text);
        final String dbText = statesDAO.get(id);
        final int delete = statesDAO.delete(id);
    }
}
