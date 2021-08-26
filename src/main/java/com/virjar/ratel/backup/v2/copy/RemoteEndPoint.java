package com.virjar.ratel.backup.v2.copy;

import com.virjar.ratel.backup.v2.SSHHelper;
import com.virjar.ratel.backup.v2.devices.Device;
import org.apache.sshd.client.session.ClientSession;
import org.apache.sshd.sftp.client.SftpClientFactory;
import org.apache.sshd.sftp.client.fs.SftpFileSystem;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public class RemoteEndPoint extends EndPoint {
    public RemoteEndPoint(Device device, String rootDir) {
        super(device, rootDir);
    }

    @Override
    List<String> executeCmd(String cmd) throws IOException {
        return SSHHelper.execute(device, cmd);
    }

    @Override
    Path resolvePath(String dir) throws IOException {
        ClientSession clientSession = device.createClientSession();
        SftpFileSystem fs = SftpClientFactory.instance().createSftpFileSystem(clientSession);
        //        Files.createDirectories(remotePath);
        return fs.getDefaultDir().resolve(dir);
    }
}
