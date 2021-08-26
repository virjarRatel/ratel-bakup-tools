package com.virjar.ratel.backup.v2.devices;

import com.alibaba.fastjson.JSONObject;
import com.virjar.ratel.backup.v2.CmdExecutor;
import com.virjar.ratel.backup.v2.copy.CopyTask;
import com.virjar.ratel.backup.v2.ui.UIComponent;

import javax.swing.*;
import java.io.IOException;
import java.util.*;

public class DevicesManager {
    private static List<Device> allDevices = new ArrayList<>();

    public static Device chooseDevice = null;

    public static boolean isChoosed(Device device) {
        return chooseDevice == device;
    }

    public static JSONObject deviceSnapshot() {
        JSONObject allDeviceSnapshot = new JSONObject();
        for (Device device : allDevices) {
            if (!device.getTaskStatus().isDoing()) {
                continue;
            }
            CopyTask nowCopyTask = device.getNowCopyTask();
            if (null == nowCopyTask) {
                continue;
            }
            JSONObject snapshotJson = nowCopyTask.deviceSnapshot();
            if (null == snapshotJson) {
                continue;
            }
            allDeviceSnapshot.put(snapshotJson.getString("serial"), snapshotJson);
        }
        return allDeviceSnapshot;
    }

    public static void choose(Device device) {
        boolean isNew = device != DevicesManager.chooseDevice;
        DevicesManager.chooseDevice = device;
        UIComponent.DevicePanel.render();
        if (isNew) {
            UIComponent.LogPanel.output.setText(device.logContent());
        }
    }

    private static void startAdbForward() {
        List<Device> allDevices = DevicesManager.allDevices;
        for (Device device : allDevices) {
            device.startForwardService();
        }
    }

    public static void removeDevice(Device device) {
        if (device.isOnline()) {
            return;
        }
        SwingUtilities.invokeLater(() -> {
            allDevices.remove(device);
            device.getOrCreateUIComponent().remove();
        });
    }

    private static void appendDeviceOnUiPanel() {
        Box deviceContainer = UIComponent.DevicePanel.deviceListContainer;
        if (deviceContainer == null) {
            return;
        }
        List<Device> allDevices = DevicesManager.allDevices;
        for (Device device : allDevices) {
            Device.UI uiComponent = device.getOrCreateUIComponent();
            uiComponent.attachToMainPanel(deviceContainer);
        }
    }

    public static void reloadDeviceList() {
        try {
            CmdExecutor.runCommand("adb devices");
        } catch (IOException e) {
            e.printStackTrace();
            allDevices = new ArrayList<>();
            return;
        }
        List<String> connectedDeviceList = new LinkedList<>();
        for (String str : CmdExecutor.getStdOutputList()) {
            str = str.trim();
            if (str.toLowerCase().startsWith("list of devices attached")) {
                continue;
            }
            if (str.isEmpty()) {
                continue;
            }
            String serial = str.split("\t")[0];
            connectedDeviceList.add(serial);
        }

        Set<String> offlineSerial = new HashSet<>();

        TreeMap<String, Device> deviceTreeMap = new TreeMap<>();
        for (Device device : allDevices) {
            deviceTreeMap.put(device.getSerial(), device);
            offlineSerial.add(device.getSerial());

        }
        for (String serial : connectedDeviceList) {
            if (deviceTreeMap.containsKey(serial)) {
                offlineSerial.remove(serial);
                deviceTreeMap.get(serial).markOffline(false);
            } else {
                deviceTreeMap.put(serial, new Device(serial));
            }
        }

        List<Device> newAllDeviceList = new ArrayList<>(deviceTreeMap.values());
        Collections.sort(newAllDeviceList);
        allDevices = newAllDeviceList;
        appendDeviceOnUiPanel();

        startAdbForward();

        for (String offline : offlineSerial) {
            deviceTreeMap.get(offline).markOffline(true);
        }
    }
}
