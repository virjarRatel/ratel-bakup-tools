package com.virjar.ratel.backup.v2.ui;

import com.alibaba.fastjson.JSONObject;
import com.virjar.ratel.backup.v2.Configs;
import com.virjar.ratel.backup.v2.Threads;
import com.virjar.ratel.backup.v2.devices.DevicesManager;
import org.apache.commons.lang3.StringUtils;

import javax.swing.*;
import javax.swing.border.LineBorder;
import java.awt.*;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;

public class ToolMain {

    private static JLabel createTopPanel() {
        return new JLabel("平头哥备份工具");
    }

    private static void createAndShowGUI() {
        JFrame.setDefaultLookAndFeelDecorated(true);

        JFrame frame = new JFrame("平头哥备份工具");
        frame.setMinimumSize(new Dimension(1000, 800));
        frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent e) {
                int option = JOptionPane.showConfirmDialog(null, "是否退出程序？", "确认框", JOptionPane.YES_NO_OPTION,
                        JOptionPane.QUESTION_MESSAGE);
                if (option == JOptionPane.YES_OPTION) {
                    JSONObject taskStatus = Configs.taskProcessJson();
                    taskStatus.putAll(DevicesManager.deviceSnapshot());
                    Configs.updateProcessJson(taskStatus);
                    System.exit(0);
                }
            }
        });

        frame.setLocationRelativeTo(null);
        BorderLayout borderLayout = new BorderLayout();
        frame.setLayout(borderLayout);
        frame.add(BorderLayout.NORTH, createTopPanel());
        frame.add(BorderLayout.SOUTH, createStatusBar());
        frame.add(BorderLayout.WEST, createDevicePanel());
        frame.add(BorderLayout.CENTER, createLogPanel());
        frame.pack();
        frame.setVisible(true);
    }

    private static Component createLogPanel() {
        JPanel jPanel = new JPanel();
        jPanel.setLayout(new GridLayout());
        jPanel.setPreferredSize(new Dimension(400, 800));
        jPanel.setBorder(LineBorder.createGrayLineBorder());
        JTextArea output = new JTextArea();
        //output.setLineWrap(true);
        output.setEditable(false);
        jPanel.add(new JScrollPane(output));

        UIComponent.LogPanel.output = output;
        return jPanel;
    }

    private static Component createDevicePanel() {
        JPanel jPanel = new JPanel(new GridLayout(2, 1));

        Box verticalBox = Box.createVerticalBox();
        verticalBox.setBorder(LineBorder.createGrayLineBorder());
        JLabel jLabel = new JLabel("设备列表");
        verticalBox.add(jLabel);
        verticalBox.add(Box.createVerticalGlue());
        UIComponent.DevicePanel.deviceListContainer = verticalBox;
        jPanel.add(verticalBox);

        Box opPanelBox = Box.createVerticalBox();
        opPanelBox.setBorder(LineBorder.createGrayLineBorder());
        jPanel.add(opPanelBox);

        JPanel title = new JPanel();
        title.setMaximumSize(new Dimension(200, 30));
        title.add(new JLabel("操作面板"));
        JLabel deviceSerial = new JLabel("未选择");
        title.add(deviceSerial);
        opPanelBox.add(title);

        JPanel btns = new JPanel();
        btns.setMaximumSize(new Dimension(2000, 30));
        opPanelBox.add(btns);

        JButton backupBtn = new JButton("备份");
        JButton storeBtn = new JButton("还原");
        JButton deleteBtn = new JButton("删除");
        JButton pauseBtn = new JButton("暂停");
        btns.add(backupBtn);
        btns.add(storeBtn);
        btns.add(deleteBtn);
        btns.add(pauseBtn);

        JPanel opStatusPanel = new JPanel();
        opPanelBox.add(opStatusPanel);
        opStatusPanel.setMaximumSize(new Dimension(200, 30));
        opStatusPanel.add(new JLabel("当前状态:"));
        JLabel opStatusLabel = new JLabel();
        opStatusPanel.add(opStatusLabel);

        JPanel bitRatePanel = new JPanel();
        opPanelBox.add(bitRatePanel);
        bitRatePanel.setMaximumSize(new Dimension(200, 30));
        bitRatePanel.add(new JLabel("传输速度:"));
        JLabel bitRatePanelLabel = new JLabel();
        bitRatePanel.add(bitRatePanelLabel);

        JPanel processPanel = new JPanel();
        opPanelBox.add(processPanel);
        processPanel.setMaximumSize(new Dimension(200, 30));
        processPanel.add(new JLabel("当前进度:"));
        JLabel processPanelLabel = new JLabel();
        processPanel.add(processPanelLabel);

        opPanelBox.add(Box.createVerticalGlue());

        UIComponent.DevicePanel.deviceSerial = deviceSerial;
        UIComponent.DevicePanel.backupBtn = backupBtn;
        UIComponent.DevicePanel.storeBtn = storeBtn;
        UIComponent.DevicePanel.deleteBtn = deleteBtn;
        UIComponent.DevicePanel.pauseBtn = pauseBtn;

        UIComponent.DevicePanel.opStatusLabel = opStatusLabel;
        UIComponent.DevicePanel.bitRatePanelLabel = bitRatePanelLabel;
        UIComponent.DevicePanel.processPanelLabel = processPanelLabel;

        UIComponent.DevicePanel.initEventListener();

        return jPanel;
    }

    private static Component createStatusBar() {
        JPanel jPanel = new JPanel();
        jPanel.setBorder(LineBorder.createGrayLineBorder());
        jPanel.setLayout(new GridLayout(1, 4, 2, 3));

        JLabel statusLabel = new JLabel("adb状态:未知");
        jPanel.add(statusLabel);

        JLabel diskLabel = new JLabel("磁盘剩余:未知");
        jPanel.add(diskLabel);

        JLabel dataPathLabel = new JLabel("备份地址:未知");
        jPanel.add(dataPathLabel);

        JLabel targetPackageLabel = new JLabel("目标应用:未知");
        targetPackageLabel.addMouseListener(new MouseListener() {
            @Override
            public void mouseClicked(MouseEvent e) {
                String targetPackageName = JOptionPane.showInputDialog("输入目标应用包名");
                System.out.println("目标应用名称：" + targetPackageName);
                if (StringUtils.isNotBlank(targetPackageName)) {
                    Configs.setOpApp(targetPackageName);
                }
            }

            @Override
            public void mousePressed(MouseEvent e) {

            }

            @Override
            public void mouseReleased(MouseEvent e) {

            }

            @Override
            public void mouseEntered(MouseEvent e) {

            }

            @Override
            public void mouseExited(MouseEvent e) {

            }
        });
        jPanel.add(targetPackageLabel);

        UIComponent.StatusBar.adbStatusTextLabel = statusLabel;
        UIComponent.StatusBar.diskTextLabel = diskLabel;
        UIComponent.StatusBar.dataPathTextLabel = dataPathLabel;
        UIComponent.StatusBar.targetPackageTextLabel = targetPackageLabel;
        return jPanel;
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(ToolMain::createAndShowGUI);
        SwingUtilities.invokeLater(UIComponent.StatusBar::refresh);
        Threads.setup();
    }
}