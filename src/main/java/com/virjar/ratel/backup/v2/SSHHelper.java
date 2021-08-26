package com.virjar.ratel.backup.v2;

import com.virjar.ratel.backup.v2.devices.Device;
import org.apache.sshd.client.SshClient;
import org.apache.sshd.client.channel.ChannelShell;
import org.apache.sshd.client.channel.ClientChannelEvent;
import org.apache.sshd.client.future.AuthFuture;
import org.apache.sshd.client.future.ConnectFuture;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.common.keyprovider.ClassLoadableResourceKeyPairProvider;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.apache.sshd.common.util.security.bouncycastle.BouncyCastleKeyPairResourceParser;
import org.bouncycastle.jce.provider.BouncyCastleProvider;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.Security;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SSHHelper {
    private static final SshClient client;

    static {
        SecurityUtils.setKeyPairResourceParser(BouncyCastleKeyPairResourceParser.INSTANCE);
        Security.addProvider(new BouncyCastleProvider());
        client = SshClient.setUpDefaultClient();
        client.start();
    }

    public static ClientSession createClientSession(Device device) throws IOException {
        ConnectFuture connectFuture = client.connect("ratel", "127.0.0.1", device.getSshPort());
        connectFuture.await();
        ClientSession session = connectFuture.getSession();
        session.addPasswordIdentity("ratel");
        session.addPublicKeyIdentity(loadPrivateKeyPair());

        AuthFuture authFuture = session.auth();
        authFuture.await();
        if (!authFuture.isSuccess())
            throw new IOException("auth failed");
        return session;
    }

    public static List<String> execute(Device device, String cmd) throws IOException {
        ClientSession clientSession = device.createClientSession();
        ChannelShell ec = clientSession.createShellChannel();
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        ec.setOut(byteArrayOutputStream);

        ByteArrayInputStream byteArrayInputStream = new ByteArrayInputStream(cmd.getBytes(StandardCharsets.UTF_8));

        ec.setIn(byteArrayInputStream);
        ec.open();

        ec.waitFor(Collections.singletonList(ClientChannelEvent.CLOSED), 0);
        ec.close();

        List<String> ret = new ArrayList<>();
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(
                new ByteArrayInputStream(byteArrayOutputStream.toByteArray())
        ));
        String line;
        while ((line = bufferedReader.readLine()) != null) {
            ret.add(line);
        }
        return ret;
    }

    private static KeyPair loadPrivateKeyPair() {
        ClassLoadableResourceKeyPairProvider classLoadableResourceKeyPairProvider = new ClassLoadableResourceKeyPairProvider(SSHHelper.class.getClassLoader(), "sshdroid.pem");
        Iterable<KeyPair> keyPairs = classLoadableResourceKeyPairProvider.loadKeys(null);
        return keyPairs.iterator().next();
    }
}
