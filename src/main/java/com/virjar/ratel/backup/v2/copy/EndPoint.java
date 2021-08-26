package com.virjar.ratel.backup.v2.copy;

import com.virjar.ratel.backup.v2.devices.Device;
import lombok.Getter;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

public abstract class EndPoint {
    protected Device device;
    @Getter
    private final String rootDir;

    public EndPoint(Device device, String rootDir) {
        this.device = device;
        this.rootDir = rootDir;
    }

    abstract List<String> executeCmd(String cmd) throws IOException;

    abstract Path resolvePath(String dir) throws IOException;
}
