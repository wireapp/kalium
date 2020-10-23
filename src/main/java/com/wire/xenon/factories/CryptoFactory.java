package com.wire.xenon.factories;

import com.wire.bots.cryptobox.CryptoException;
import com.wire.xenon.crypto.Crypto;

import java.util.UUID;

public interface CryptoFactory {
    Crypto create(UUID botId) throws CryptoException;
}
