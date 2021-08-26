package com.virjar.ratel.backup.v2.devices;


import com.virjar.ratel.backup.v2.CmdExecutor;
import com.virjar.ratel.backup.v2.Configs;
import com.virjar.ratel.backup.v2.SSHHelper;
import com.virjar.ratel.backup.v2.copy.CopyTask;
import com.virjar.ratel.backup.v2.copy.EndPoint;
import com.virjar.ratel.backup.v2.copy.LocalEndPoint;
import com.virjar.ratel.backup.v2.copy.RemoteEndPoint;
import com.virjar.ratel.backup.v2.ui.UIComponent;
import lombok.Getter;
import lombok.Setter;
import org.apache.commons.lang3.StringUtils;
import org.apache.sshd.client.session.ClientSession;

import javax.swing.*;
import java.awt.*;
import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;

public class Device implements Comparable<Device> {
    public Device(String serial) {
        this.serial = serial;
    }

    /**
     * 序列号
     */
    @Getter
    private final String serial;

    /**
     * ssh服务端口
     */
    @Getter
    private int sshPort;

    /**
     * 是否在线，依靠adb devices
     */
    @Getter
    private boolean online = true;

    private ClientSession clientSession;


    @Getter
    @Setter
    private TaskStatus taskStatus = TaskStatus.INIT;


    @Getter
    private CopyTask nowCopyTask;

    public ClientSession createClientSession() throws IOException {
        if (clientSession != null && clientSession.isOpen()) {
            return clientSession;
        }
        clientSession = SSHHelper.createClientSession(this);
        return clientSession;
    }

    public void destroySession() throws IOException {
        ClientSession clientSession = this.clientSession;
        if (clientSession != null && clientSession.isOpen()) {
            clientSession.close();
        }
    }


    private static int servicePort = 3478;
    private static final ConcurrentHashMap<Integer, Integer> mappedPort = new ConcurrentHashMap<>();


    public void pause() {
        if (!taskStatus.isDoing()) {
            return;
        }
        nowCopyTask.pause();
    }

    public void doCopy(TaskStatus task) {
        if (taskStatus.isDoing()) {
            return;
        }
        if (task != TaskStatus.BACKUP && task != TaskStatus.STORE) {
            return;
        }
        String appPackage = Configs.getOpApp();
        if (StringUtils.isBlank(appPackage)) {
            //todo
            return;
        }
        taskStatus = TaskStatus.BACKUP;
        RemoteEndPoint remoteEndPoint = new RemoteEndPoint(this, "/data/data/" + appPackage);
        LocalEndPoint localEndPoint = new LocalEndPoint(this,
                Configs.makeDir(new File(Configs.resolveBackupDir(), appPackage + "/" + serial)).getAbsolutePath()
        );
        EndPoint from, to;
        if (task == TaskStatus.BACKUP) {
            from = remoteEndPoint;
            to = localEndPoint;
        } else {
            from = localEndPoint;
            to = remoteEndPoint;
        }
        try {
            nowCopyTask = new CopyTask(this, from, to);
            nowCopyTask.executeCopyTask();
        } catch (IOException e) {
            e.printStackTrace();
            onLog("error:" + e.getMessage());
            taskStatus = TaskStatus.FAILED;
        }
    }

    void startForwardService() {
        try {
            if (!isOnline()) {
                return;
            }
            int port = getAdbForwardPort();
            if (port < 0) {
                // do forward
                startForward();
                port = getAdbForwardPort();
                if (port < 0) {
                    startForward();
                    port = getAdbForwardPort();
                }
            }
            if (port < 0) {
                System.err.println("adb Forward error");
                return;
            }
            this.sshPort = port;
        } catch (IOException e) {
            e.printStackTrace();
            return;
        }
        render();
    }

    private synchronized void startForward() throws IOException {
        while (mappedPort.containsKey(servicePort)) {
            servicePort++;
        }
        CmdExecutor.runCommand("adb -s " + serial + " forward tcp:" + servicePort + " tcp:3478");
    }

    private int getAdbForwardPort() throws IOException {
        CmdExecutor.runCommand("adb forward --list");
        for (String str : CmdExecutor.getStdOutputList()) {
            if (StringUtils.isBlank(str)) {
                continue;
            }
            //bedmy9ugz5fayhv8 tcp:3456 tcp:3456
            String localMappingPort = str.trim().split(" ")[1];
            int port = Integer.parseInt(localMappingPort.substring("tcp:".length()));
            mappedPort.put(port, port);
            if (!str.contains(serial)) {
                continue;
            }
            return port;
        }
        return -1;
    }


    private UI ui = null;

    public UI getOrCreateUIComponent() {
        if (ui != null) {
            return ui;
        }
        ui = new UI();
        return ui;
    }

    public Device markOffline(boolean offline) {
        if (offline != this.online) {
            return this;
        }
        this.online = !offline;
        render();
        return this;
    }


    public void render() {
        SwingUtilities.invokeLater(() -> {
            if (ui == null) {
                return;
            }
            ui.serialLabel.setText(serial);
            ui.onlineLabel.setText(online ? "在线" : "离线");
            ui.sshPortLabel.setText("ssh:" + sshPort);
        });

    }

    @Override
    public int compareTo(Device o) {
        return serial.compareTo(o.serial);
    }


    public class UI {
        public JPanel rootContainer = new JPanel();
        private Box parent = null;

        public JLabel serialLabel = new JLabel();

        public JLabel sshPortLabel = new JLabel();
        public JLabel onlineLabel = new JLabel();

        public JButton opBtn = new JButton("操作");
        public JProgressBar jProgressBar = new JProgressBar();


        public UI() {

            rootContainer.setMaximumSize(new Dimension(1000, 60));
            rootContainer.add(serialLabel);
            rootContainer.add(sshPortLabel);
            rootContainer.add(onlineLabel);
            rootContainer.add(opBtn);
            rootContainer.add(jProgressBar);

            opBtn.addActionListener(e -> DevicesManager.choose(Device.this));

        }

        public void attachToMainPanel(Box parent) {
            SwingUtilities.invokeLater(() -> {
                if (UI.this.parent != null) {
                    return;
                }
                UI.this.parent = parent;
                UI.this.parent.add(rootContainer, null, parent.getComponentCount() - 1);
                render();
            });
        }

        public void remove() {
            UI.this.parent.remove(rootContainer);
        }
    }

    private final StringBuilder logText = new StringBuilder();

    public void onLog(String message) {
        System.out.println(message);
        logText.append(message);
        logText.append("\n");
        JTextArea output = UIComponent.LogPanel.output;
        if (DevicesManager.isChoosed(this)) {
            output.append(message);
            output.append("\n");
        }
    }

    public String logContent() {
        return logText.toString();
    }

}
