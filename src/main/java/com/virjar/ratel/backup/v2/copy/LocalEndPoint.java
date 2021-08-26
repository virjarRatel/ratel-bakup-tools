package com.virjar.ratel.backup.v2.copy;

import com.virjar.ratel.backup.v2.CmdExecutor;
import com.virjar.ratel.backup.v2.devices.Device;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;

public class LocalEndPoint extends EndPoint {
    public LocalEndPoint(Device device, String rootDir) {
        super(device, rootDir);
    }

    @Override
    List<String> executeCmd(String cmd) throws IOException {
        CmdExecutor.runCommand(cmd);
        return CmdExecutor.getStdOutputList();
    }

    @Override
    Path resolvePath(String dir) {
        return Paths.get(dir);
    }
}
