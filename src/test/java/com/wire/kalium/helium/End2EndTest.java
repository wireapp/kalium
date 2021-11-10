package com.wire.helium;

import com.google.protobuf.InvalidProtocolBufferException;
import com.waz.model.Messages;
import com.wire.bots.cryptobox.IStorage;
import com.wire.helium.helpers.DummyAPI;
import com.wire.helium.helpers.MemStorage;
import com.wire.helium.helpers.Util;
import com.wire.xenon.assets.MessageText;
import com.wire.xenon.backend.models.NewBot;
import com.wire.xenon.crypto.CryptoDatabase;
import com.wire.xenon.crypto.storage.JdbiStorage;
import com.wire.xenon.models.otr.OtrMessage;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Base64;
import java.util.UUID;

public class End2EndTest extends DatabaseTestBase {

    private IStorage storage;
    private String rootFolder;

    @BeforeEach
    public void beforeEach() {
        rootFolder = "helium-unit-test-" + UUID.randomUUID();
        storage = new JdbiStorage(jdbi);
        flyway.migrate();
    }

    @AfterEach
    public void afterEach() throws IOException {
        flyway.clean();
        Util.deleteDir(rootFolder);
    }

    @Test
    public void testAliceToAlice() throws Exception {
        UUID aliceId = UUID.randomUUID();
        String client1 = "alice1_" + UUID.randomUUID();

        NewBot state = new NewBot();
        state.id = aliceId;
        state.client = aliceId.toString();

        CryptoDatabase aliceCrypto = new CryptoDatabase(aliceId, storage, rootFolder + "/testAliceToAlice/1");
        CryptoDatabase aliceCrypto1 = new CryptoDatabase(aliceId, storage, rootFolder + "/testAliceToAlice/2");

        DummyAPI api = new DummyAPI();
        api.addDevice(aliceId, client1, aliceCrypto1.box().newLastPreKey());

        WireClientImp aliceClient = new WireClientImp(api, aliceCrypto, state, null);

        for (int i = 0; i < 10; i++) {
            String text = "Hello Alice, This is Alice!";
            aliceClient.send(new MessageText(text));

            OtrMessage msg = api.getMsg();

            String cipher1 = msg.get(aliceId, client1);
            String decrypt = aliceCrypto1.decrypt(aliceId, msg.getSender(), cipher1);
            String s1 = getText(decrypt);
            assert text.equals(s1);
        }
    }

    @Test
    public void testAliceToBob() throws Exception {
        UUID bobId = UUID.randomUUID();
        UUID aliceId = UUID.randomUUID();
        String client1 = "bob1";

        MemStorage storage = new MemStorage();

        CryptoDatabase aliceCrypto = new CryptoDatabase(aliceId, storage, rootFolder + "/testAliceToBob");
        CryptoDatabase bobCrypto = new CryptoDatabase(bobId, storage, rootFolder + "/testAliceToBob");

        DummyAPI api = new DummyAPI();
        api.addDevice(bobId, client1, bobCrypto.box().newLastPreKey());

        NewBot state = new NewBot();
        state.id = aliceId;
        state.client = "alice1";
        WireClientImp aliceClient = new WireClientImp(api, aliceCrypto, state, null);

        for (int i = 0; i < 10; i++) {
            String text = "Hello Bob, This is Alice!";
            aliceClient.send(new MessageText(text));

            OtrMessage msg = api.getMsg();

            String cipher1 = msg.get(bobId, client1);
            String decrypt = bobCrypto.decrypt(aliceId, msg.getSender(), cipher1);
            String s1 = getText(decrypt);
            assert text.equals(s1);
        }
    }

    @Test
    public void testMultiDevicePostgres() throws Exception {
        UUID bobId = UUID.randomUUID();
        UUID aliceId = UUID.randomUUID();
        String client1 = "bob1_" + UUID.randomUUID();
        String client2 = "bob2_" + UUID.randomUUID();
        String client3 = "alice3_" + UUID.randomUUID();
        String aliceCl = "alice_" + UUID.randomUUID();

        CryptoDatabase aliceCrypto1 = new CryptoDatabase(aliceId, storage, rootFolder + "/testMultiDevicePostgres/alice/1");
        CryptoDatabase bobCrypto1 = new CryptoDatabase(bobId, storage, rootFolder + "/testMultiDevicePostgres/bob/1");
        CryptoDatabase bobCrypto2 = new CryptoDatabase(bobId, storage, rootFolder + "/testMultiDevicePostgres/bob/2");

        DummyAPI api = new DummyAPI();
        api.addDevice(bobId, client1, bobCrypto1.box().newLastPreKey());
        api.addDevice(bobId, client2, bobCrypto2.box().newLastPreKey());
        api.addDevice(aliceId, client3, aliceCrypto1.box().newLastPreKey());

        CryptoDatabase aliceCrypto = new CryptoDatabase(aliceId, storage, rootFolder + "/testMultiDevicePostgres/alice");

        NewBot state = new NewBot();
        state.id = aliceId;
        state.client = aliceCl;
        WireClientImp aliceClient = new WireClientImp(api, aliceCrypto, state, null);

        for (int i = 0; i < 10; i++) {
            String text = "Hello Bob, This is Alice!";
            aliceClient.send(new MessageText(text));

            OtrMessage msg = api.getMsg();
            String sender = msg.getSender();

            String cipher1 = msg.get(bobId, client1);
            String decrypt = bobCrypto1.decrypt(aliceId, sender, cipher1);
            String s1 = getText(decrypt);
            assert text.equals(s1);

            String cipher2 = msg.get(bobId, client2);
            String decrypt2 = bobCrypto2.decrypt(aliceId, sender, cipher2);
            String s2 = getText(decrypt2);
            assert text.equals(s2);

            String cipher3 = msg.get(aliceId, client3);
            String decrypt3 = aliceCrypto1.decrypt(aliceId, sender, cipher3);
            String s3 = getText(decrypt3);
            assert text.equals(s3);
        }
    }

    private String getText(String decrypt) throws InvalidProtocolBufferException {
        byte[] decoded = Base64.getDecoder().decode(decrypt);
        Messages.GenericMessage genericMessage = Messages.GenericMessage.parseFrom(decoded);
        return genericMessage.getText().getContent();
    }
}
