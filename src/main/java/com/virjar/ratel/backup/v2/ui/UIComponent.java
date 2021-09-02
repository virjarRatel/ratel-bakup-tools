package com.virjar.ratel.backup.v2.ui;

import com.virjar.ratel.backup.v2.CmdExecutor;
import com.virjar.ratel.backup.v2.Configs;
import com.virjar.ratel.backup.v2.Threads;
import com.virjar.ratel.backup.v2.copy.CopyTask;
import com.virjar.ratel.backup.v2.devices.Device;
import com.virjar.ratel.backup.v2.devices.DevicesManager;
import com.virjar.ratel.backup.v2.devices.TaskStatus;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import java.io.File;
import java.io.IOException;

public class UIComponent {

    public static class LogPanel {
        public static JTextArea output = null;
    }

    public static class DevicePanel {
        public static Box deviceListContainer = null;
        public static JLabel deviceSerial = null;
        public static JButton backupBtn = null;
        public static JButton storeBtn = null;
        public static JButton deleteBtn = null;
        public static JButton pauseBtn = null;

        public static JLabel opStatusLabel = null;
        public static JLabel bitRatePanelLabel = null;
        public static JLabel processPanelLabel = null;

        private interface WhenChooseDevice {
            void call(Device device);
        }

        public static void callWhenChooseDevice(WhenChooseDevice runnable) {
            if (DevicesManager.chooseDevice != null) {
                runnable.call(DevicesManager.chooseDevice);
            }
        }

        public static void initEventListener() {
            backupBtn.addActionListener(e -> callWhenChooseDevice(device -> device.doCopy(TaskStatus.BACKUP)));
            storeBtn.addActionListener(e -> callWhenChooseDevice(device -> device.doCopy(TaskStatus.STORE)));
            deleteBtn.addActionListener(e -> callWhenChooseDevice(DevicesManager::removeDevice));
            pauseBtn.addActionListener(e -> callWhenChooseDevice(Device::pause));
        }

        public static void render() {
            Device chooseDevice = DevicesManager.chooseDevice;
            if (chooseDevice == null) {
                deviceSerial.setText("未知");
                opStatusLabel.setText("");
                processPanelLabel.setText("");
                bitRatePanelLabel.setText("0K/S");
            } else {
                CopyTask nowCopyTask = chooseDevice.getNowCopyTask();
                boolean parsed = false;
                if (nowCopyTask != null) {
                    long rate = nowCopyTask.getRate();
                    bitRatePanelLabel.setText((rate / 1024) + "K/S");
                    parsed = nowCopyTask.isPaused();
                    DevicePanel.pauseBtn.setText(parsed ? "启动" : "暂停");
                } else {
                    bitRatePanelLabel.setText("0K/S");
                }
                opStatusLabel.setText(String.valueOf(chooseDevice.getTaskStatus()));
                if (parsed) {
                    opStatusLabel.setText(opStatusLabel.getText() + "(暂停中)");
                }
                deviceSerial.setText(chooseDevice.getSerial());
                processPanelLabel.setText(chooseDevice.getOrCreateUIComponent().jProgressBar.getValue() + "%");
            }


        }
    }

    public static class StatusBar {
        public static JLabel adbStatusTextLabel = null;
        public static JLabel diskTextLabel = null;
        public static JLabel dataPathTextLabel;
        public static JLabel targetPackageTextLabel;

        public static void refresh() {
            Threads.uiSecondThread.execute(() -> {
                File backupDir = Configs.resolveBackupDir();

                SwingUtilities.invokeLater(() -> dataPathTextLabel.setText("备份地址:" + backupDir.getAbsolutePath()));

                String targetPackage = Configs.getOpApp();
                SwingUtilities.invokeLater(() -> targetPackageTextLabel.setText("目标应用:" + (StringUtils.isBlank(targetPackage) ? "点击输入包名" : targetPackage)));

                int spaceFree = (int) ((backupDir.getFreeSpace() * 100) / backupDir.getTotalSpace());
                SwingUtilities.invokeLater(() -> diskTextLabel.setText("磁盘剩余:" + spaceFree + "%"));

                boolean adbStatus = false;
                try {
                    CmdExecutor.runCommand("adb devices");
                    adbStatus = !CmdExecutor.getStdOutput().contains("List of devices attached");
                } catch (IOException e) {
                    e.printStackTrace();
                }
                boolean finalAdbStatus = adbStatus;
                SwingUtilities.invokeLater(() -> adbStatusTextLabel.setText("adb状态:" + (finalAdbStatus ? "正常" : "异常")));

                DevicesManager.reloadDeviceList();
            });
        }
    }
}
