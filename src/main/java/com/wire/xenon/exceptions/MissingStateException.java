package com.wire.xenon.exceptions;

import java.io.IOException;
import java.util.UUID;

public class MissingStateException extends IOException {
    public MissingStateException(UUID botId) {
        super("Unknown botId: " + botId);
    }
}
