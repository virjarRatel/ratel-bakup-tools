package com.virjar.ratel.backup.v2;

import com.alibaba.fastjson.JSONObject;
import org.apache.commons.io.FileUtils;
import org.apache.commons.lang3.StringUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Properties;

public class Configs {
    private static final File configPropertiesPath = resolveConfigPath();
    private static final Properties configProperties = loadConfigProperties();

    public static void setOpApp(String targetPackage) {
        configProperties.setProperty("op_package",targetPackage);
        saveConfig();
    }

    public static String getOpApp() {
        return configProperties.getProperty("op_package");
    }

    public static File resolveBackupDir() {
        String backupDir = configProperties.getProperty("backup_dir");
        if (StringUtils.isNotBlank(backupDir)) {
            return makeDir(new File(backupDir));
        }
        File file = new File(configPropertiesPath.getParent(), "ratel_backup");
        configProperties.setProperty("backup_dir", file.getAbsolutePath());
        saveConfig();
        return makeDir(file);
    }

    public static File makeDir(File file) {
        try {
            FileUtils.forceMkdir(file);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return file;
    }

    private static void saveConfig() {
        try {
            configProperties.store(new FileWriter(configPropertiesPath), "ratel backup toolkit");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static Properties loadConfigProperties() {
        Properties properties = new Properties();
        try {
            if (!configPropertiesPath.exists()) {
                if (!configPropertiesPath.createNewFile()) {
                    throw new IllegalStateException("can not create file: " + configPropertiesPath);
                }
            }
            properties.load(new FileInputStream(configPropertiesPath));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return properties;
    }

    private static File resolveConfigPath() {
        File homeFile = null;
        String homePath = System.getProperty("user.home");
        if (StringUtils.isNotBlank(homePath)) {
            homeFile = new File(homePath);
        }
        if (homeFile == null || !homeFile.exists()) {
            homeFile = new File(".");
        }
        return new File(homeFile, ".ratelBackupConfig");
    }

    public static JSONObject taskProcessJson() {
        File file = new File(configPropertiesPath.getParent(), "taskProcess.json");
        if (!file.exists()) {
            return new JSONObject();
        }
        try {
            return JSONObject.parseObject(FileUtils.readFileToString(file, StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new IllegalStateException("can not read task process filed", e);
        }
    }

    public static void updateProcessJson(JSONObject jsonObject) {
        File file = new File(configPropertiesPath.getParent(), "taskProcess.json");
        makeDir(file.getParentFile());
        try {
            FileUtils.write(file, jsonObject.toJSONString(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new IllegalStateException("can not write task process filed", e);
        }
    }
}
