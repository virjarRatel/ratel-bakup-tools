package com.virjar.ratel.backup.v2;

import com.virjar.ratel.backup.v2.ui.UIComponent;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class Threads {
    public static final ExecutorService uiSecondThread = Executors.newSingleThreadExecutor();
    public static final ScheduledExecutorService schedulerThread = Executors.newScheduledThreadPool(1);

    public static void setup() {
        // 20s探测下adb状态
        schedulerThread.scheduleAtFixedRate(UIComponent.StatusBar::refresh, 1, 20, TimeUnit.SECONDS);

        // 5s刷新一次磁盘状态等面板
        schedulerThread.scheduleAtFixedRate(UIComponent.DevicePanel::render, 1, 5, TimeUnit.SECONDS);

    }
}
