package com.wire.xenon.factories;

import com.wire.xenon.state.State;

import java.io.IOException;
import java.util.UUID;

public interface StorageFactory {
    State create(UUID botId) throws IOException;
}
