package com.wire.xenon.state;

import com.wire.xenon.backend.models.NewBot;

import java.io.IOException;

public interface State {

    boolean saveState(NewBot newBot) throws IOException;

    NewBot getState() throws IOException;

    boolean removeState() throws IOException;

}
