package com.virjar.ratel.backup.v2.copy;

import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.virjar.ratel.backup.v2.Configs;
import com.virjar.ratel.backup.v2.copy.hystrix.HystrixRollingNumber;
import com.virjar.ratel.backup.v2.copy.hystrix.HystrixRollingNumberEvent;
import com.virjar.ratel.backup.v2.devices.Device;
import com.virjar.ratel.backup.v2.devices.TaskStatus;
import com.virjar.ratel.backup.v2.ui.UIComponent;
import lombok.Getter;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.stream.Collectors;


public class CopyTask {
    private final Device device;
    private final EndPoint from;
    private final EndPoint to;
    @Getter
    boolean paused = false;

    private final HystrixRollingNumber hystrixRollingNumber;

    public CopyTask(Device device, EndPoint from, EndPoint to) {
        this.device = device;
        this.from = from;
        this.to = to;
        this.hystrixRollingNumber = new HystrixRollingNumber(1000 * 30, 30);
    }

    public long getRate() {
        return hystrixRollingNumber.getRollingSum(HystrixRollingNumberEvent.SPEED) / 30;
    }

    private boolean doSplitCopyTask(SplitTaskHolder splitTaskHolder) throws IOException {
        Path fromPath = from.resolvePath(new File(from.getRootDir(), splitTaskHolder.filePath).getAbsolutePath());
        if (!Files.exists(fromPath)) {
            // 可能是copy过程中，发现数据不存在，此时忽略当前任务
            return true;
        }
//        if (fromPath.toString().contains("ratel_container_origin_apk")) {
//            return true;
//        }
        Path toPath = to.resolvePath(new File(to.getRootDir(), splitTaskHolder.filePath).getAbsolutePath());
        if (Files.isDirectory(fromPath) && !Files.exists(toPath)) {
            Files.createDirectories(toPath);
        }
        device.onLog("copy file: " + splitTaskHolder.filePath);
        if (Files.exists(toPath)) {
            // 如果文件存在，那么如果是文件夹，则不需要干预
            // 如果是文件，则覆盖
            // 因为如果是文件夹，sftp将会删除文件夹，但是当文件夹有内容的时候又不让删除成功
            if (!Files.isDirectory(toPath)) {
                Files.copy(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
            }
        } else {
            Path parent = toPath.getParent();
            if (!Files.exists(parent)) {
                Files.createDirectories(parent);
            }
            Files.copy(fromPath, toPath, StandardCopyOption.REPLACE_EXISTING);
        }

        if (Files.isDirectory(fromPath)) {
            Files.walkFileTree(fromPath, new SimpleFileVisitor<Path>() {
                @Override
                public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                    if (fromPath.equals(dir)) {
                        return FileVisitResult.CONTINUE;
                    }
                    Path relative = fromPath.relativize(dir);
                    Path toDir = toPath.resolve(relative.toString());
                    if (!Files.exists(toDir)) {
                        Files.createDirectory(toDir);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    if (attrs.isSymbolicLink()) {
                        // 连接内容不copy
                        return FileVisitResult.CONTINUE;
                    }
                    device.onLog("copy file: " + file.toString());
                    hystrixRollingNumber.add(HystrixRollingNumberEvent.SPEED, Files.size(file));
                    Path relative = fromPath.relativize(file);
                    Path toFile = toPath.resolve(relative.toString());
                    Files.copy(file, toFile, StandardCopyOption.REPLACE_EXISTING);
                    return FileVisitResult.CONTINUE;
                }

            });
        }

        return true;
    }

    private void restoreSplitTasks(JSONObject serial) {
        int totalDevice = serial.getIntValue("total");
        JSONArray taskHolders = serial.getJSONArray("taskHolders");
        LinkedList<SplitTaskHolder> splitTaskHolders = new LinkedList<>();
        for (int i = 0; i < taskHolders.size(); i++) {
            JSONObject jsonObject = taskHolders.getJSONObject(i);
            SplitTaskHolder splitTaskHolder = new SplitTaskHolder(jsonObject.getString("filePath"));
            splitTaskHolder.retryCount = 0;
            splitTaskHolders.add(splitTaskHolder);
        }
        this.taskHolders = splitTaskHolders;
        this.total = totalDevice;
    }


    public void executeCopyTask() throws IOException {
        JSONObject taskStatusJson = Configs.taskProcessJson();
        JSONObject deviceJson = Configs.taskProcessJson().getJSONObject(device.getSerial());
        if (deviceJson != null) {
            restoreSplitTasks(deviceJson);
            taskStatusJson.remove(device.getSerial());
            Configs.updateProcessJson(taskStatusJson);
        } else {
            generateSplitTasks();
        }

        device.onLog("total task size:" + total);
        copyThread = new Thread("copy-task-" + device.getSerial()) {
            @Override
            public void run() {
                doCopyTaskInNewThread();
            }
        };
        copyThread.start();
    }

    private void generateSplitTasks() throws IOException {
        //按照两层目录级别对任务进行拆分
        List<String> fileList = from.executeCmd("find " + from.getRootDir() + " -maxdepth 2");
        List<String> needRemove = new ArrayList<>();
        // 删除短文件夹，他们是存在子文件夹的父任务
        for (String file : fileList) {
            for (String testFile : fileList) {
                if (testFile.equals(file)) {
                    continue;
                }
                if (testFile.startsWith(file)) {
                    needRemove.add(file);
                    break;
                }
            }
        }
        fileList.removeAll(needRemove);
        // 解析为相对路径
        fileList = fileList.stream().map(s -> s.substring(from.getRootDir().length())).collect(Collectors.toList());

        this.taskHolders
                = fileList.stream().map(SplitTaskHolder::new).collect(Collectors.toCollection(LinkedList::new));
        this.total = this.taskHolders.size();
    }

    private LinkedList<SplitTaskHolder> taskHolders;
    private int total;
    private Thread copyThread = null;

    public synchronized void pause() {
        device.onLog("call parse ,now status:" + paused);
        if (paused) {
            paused = false;
            copyThread = new Thread("copy-task-" + device.getSerial()) {
                @Override
                public void run() {
                    doCopyTaskInNewThread();
                }
            };
            copyThread.start();
        } else {
            paused = true;
            if (copyThread != null) {
                copyThread.interrupt();
            }
        }
        UIComponent.DevicePanel.render();
    }

    private void doCopyTaskInNewThread() {
        int failedCount = 0;
        while (!taskHolders.isEmpty() && !paused && !Thread.currentThread().isInterrupted()) {
            SplitTaskHolder splitTaskHolder = taskHolders.pollFirst();
            // 更新任务进度
            int progress = ((total - taskHolders.size()) * 100) / total;
            device.getOrCreateUIComponent().jProgressBar.setValue(progress);

            boolean success = false;
            try {
                success = doSplitCopyTask(splitTaskHolder);
                failedCount = 0;
            } catch (Exception e) {
                ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                PrintStream printStream = new PrintStream(byteArrayOutputStream);
                e.printStackTrace(printStream);
                device.onLog(byteArrayOutputStream.toString());
                try {
                    device.destroySession();
                } catch (IOException ioException) {
                    ioException.printStackTrace();
                }
            }

            if (!success) {
                failedCount++;
                splitTaskHolder.retryCount++;
                if (failedCount >= 3) {
                    // 连续三次失败，修改暂停状态
                    device.onLog("连续三次失败，修改暂停状态");
                    if (!paused) {
                        pause();
                    }
                }
            } else {
                continue;
            }
            if (splitTaskHolder.retryCount > 3) {
                device.onLog("the task failed finally");
                device.setTaskStatus(TaskStatus.FAILED);
                break;
            } else {
                taskHolders.addLast(splitTaskHolder);
                try {
                    Thread.sleep(10000);
                } catch (InterruptedException e) {
                    // not happen
                    e.printStackTrace();
                }
            }

        }
        if (device.getTaskStatus().isDoing()
                && taskHolders.isEmpty()) {
            device.onLog("执行完成");
            device.getOrCreateUIComponent().jProgressBar.setValue(100);
            device.setTaskStatus(TaskStatus.SUCCESS);
        } else {
            device.onLog("now status: " + device.getTaskStatus() + " pausedStatus: " + paused);
        }
    }


    private static class SplitTaskHolder {
        private int retryCount = 0;
        private final String filePath;

        public SplitTaskHolder(String filePath) {
            this.filePath = filePath;
        }
    }

    public JSONObject deviceSnapshot() {
        if (taskHolders.isEmpty()) {
            return null;
        }
        JSONObject snapshotJson = new JSONObject();
        snapshotJson.put("serial", device.getSerial());
        snapshotJson.put("total", total);
        JSONArray jsonArray = new JSONArray();
        snapshotJson.put("taskHolders", jsonArray);
        for (SplitTaskHolder st : taskHolders) {
            JSONObject taskHolderJson = new JSONObject();
            taskHolderJson.put("filePath", st.filePath);
            jsonArray.add(taskHolderJson);
        }
        return snapshotJson;
    }
}
