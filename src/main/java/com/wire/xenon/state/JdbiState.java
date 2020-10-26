package com.wire.xenon.state;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.wire.xenon.backend.models.NewBot;
import com.wire.xenon.exceptions.MissingStateException;
import org.jdbi.v3.core.Jdbi;

import java.io.IOException;
import java.util.UUID;

public class JdbiState implements State {
    private final static ObjectMapper mapper = new ObjectMapper();

    private final UUID botId;
    private final StatesDAO statesDAO;

    public JdbiState(UUID botId, Jdbi jdbi) {
        this.botId = botId;
        this.statesDAO = jdbi.onDemand(StatesDAO.class);
    }

    @Override
    public boolean saveState(NewBot newBot) throws IOException {
        String str = mapper.writeValueAsString(newBot);
        return 1 == statesDAO.insert(botId, str);
    }

    @Override
    public NewBot getState() throws IOException {
        String str = statesDAO.get(botId);
        if (str == null)
            throw new MissingStateException(botId);
        return mapper.readValue(str, NewBot.class);
    }

    @Override
    public boolean removeState() {
        return 1 == statesDAO.delete(botId);
    }
}
