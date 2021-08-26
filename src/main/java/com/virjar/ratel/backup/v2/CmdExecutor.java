package com.virjar.ratel.backup.v2;

import org.apache.commons.io.IOUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.TimeUnit;


public class CmdExecutor {

    private static final ThreadLocal<String> errorOutput = new ThreadLocal<>();
    private static final ThreadLocal<String> stdOutput = new ThreadLocal<>();
    private static final ThreadLocal<List<String>> stdOutputList = new ThreadLocal<>();

    public static String getErrorOutput() {
        return errorOutput.get();
    }

    public static String getStdOutput() {
        return stdOutput.get();
    }

    public static List<String> getStdOutputList() {
        return stdOutputList.get();
    }

    public static String runCommand(String cmd) throws IOException {
        return runCommand(cmd, 2);
    }

    static String runCommand(String cmd, int timeout) throws IOException {
        System.out.println("execute cmd: " + cmd);

        // 直接执行没有环境变量，这里把环境变量导入一下，否则有一些自己安装的服务无法成功执行
        Map<String, String> newEnvironment = new HashMap<>(System.getenv());
        int i = 0;
        String[] environment = new String[newEnvironment.size()];
        for (Map.Entry<String, String> entry : newEnvironment.entrySet()) {
            environment[i] = entry.getKey() + "=" + entry.getValue();
            i++;
        }
        List<String> additionCmd = new ArrayList<>();
        if (cmd.contains("\n")) {
            String[] split = cmd.split("\n");
            cmd = split[0];
            for (int j = 1; j < split.length; j++) {
                additionCmd.add(split[j].trim());
            }
        }

        try {
            Process process = Runtime.getRuntime().exec(cmd, environment);

            if (!additionCmd.isEmpty()) {
                OutputStream outputStream = process.getOutputStream();
                for (String addition : additionCmd) {
                    if (addition.length() > 0) {
                        outputStream.write(addition.getBytes(StandardCharsets.UTF_8));
                    }
                    outputStream.write("\n".getBytes(StandardCharsets.UTF_8));
                }
                outputStream.flush();
                outputStream.close();
            }

            StreamReadTask streamReadTaskInfo = new StreamReadTask(process.getInputStream());
            StreamReadTask streamReadTaskError = new StreamReadTask(process.getErrorStream());
            streamReadTaskInfo.start();
            streamReadTaskError.start();

            boolean success = process.waitFor(timeout, TimeUnit.MINUTES);
            if (!success) {
                throw new IllegalStateException("cmd : {" + cmd + "} execute timeout");
            }
            // 休眠1s，因为读取线程可能还没有把数据读完整
            Thread.sleep(1000);
            String stdOut = streamReadTaskInfo.finalOut().trim();
            String errOut = streamReadTaskError.finalOut().trim();
            errorOutput.set(errOut);
            stdOutput.set(errOut);

            stdOutputList.set(streamReadTaskInfo.finalOutputList());


            String out = stdOut + errOut;
            System.out.println("cmd execute result:\n" + out);
            return out;
        } catch (Throwable throwable) {
            System.out.println("cmd execute error");
            throwable.printStackTrace();
            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            throwable.printStackTrace(new PrintWriter(new OutputStreamWriter(byteArrayOutputStream)));
            return byteArrayOutputStream.toString("utf8");
        }
    }

    private static class StreamReadTask extends Thread {
        private final InputStream inputStream;
        private final StringBuilder output = new StringBuilder();

        private final List<String> outputList = new LinkedList<>();


        StreamReadTask(InputStream inputStream) {
            this.inputStream = inputStream;
        }

        // 命令执行和线程切换，可能存在延时，所以这里依靠加锁来同步状态
        // 由于切换非常快，2s时间就够了
        public String finalOut() {
            return output.toString();
        }

        public List<String> finalOutputList() {
            return outputList;
        }

        @Override
        public void run() {
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String line;
            try {

                while ((line = bufferedReader.readLine()) != null) {
                    System.out.println(line);
                    outputList.add(line);
                    output.append(line);
                    output.append("\n");
                }
            } catch (IOException e) {
                output.append(e)
                        .append(":")
                        .append(e.getMessage());
            } finally {
                IOUtils.closeQuietly(inputStream);
            }
        }
    }
}
