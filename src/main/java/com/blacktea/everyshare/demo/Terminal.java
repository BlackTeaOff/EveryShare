package com.blacktea.everyshare.demo;

import com.blacktea.everyshare.core.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Scanner;

public class Terminal {
    private static final Logger log = LoggerFactory.getLogger(Terminal.class);

    private static final List<Device> deviceList = new ArrayList<>();

    public static void main(String[] args) {
        System.out.println("==========EveryShare CLI==========");

        String defaultSavePath = System.getProperty("user.dir") + File.separator + "downloads" + File.separator;

        log.info("默认保存目录: {}", defaultSavePath);

        // 开启接收端服务(只需要开一次)
        FileReceiver receiver = new FileReceiver(defaultSavePath);
        receiver.start();

        // 寻找附近设备, 去重放到deviceList
        // 函数式接口, 实际上实现了DeviceListener接口, 里面只有一个需要实现的抽象方法, 参数是device
        DiscoveryService discovery = new DiscoveryService(device -> {
            // stream把List变成流, 每一个元素都会执行anyMatch, anyMatch接受Predicate接口, 需要实现一个抽象方法boolean test
            boolean exists = deviceList.stream().anyMatch(d -> d.getIp().equals(device.getIp()));
            if (!exists) {
                deviceList.add(device);
                System.out.println("\n 发现新设备! 序号: ["+ (deviceList.size() - 1) +"] 设备IP: "+ device.getIp() +" 设备ID: " + device.getDeviceId());
            }
        });
        discovery.startReceiver();
        discovery.startBroadcaster();

        startUserInputLoop();
    }

    private static void startUserInputLoop() {
        Scanner scanner = new Scanner(System.in);
        FileSender sender = new FileSender();

        while (true) {
            System.out.println("\n可用指令: [list] 查看设备 | [send] 发送文件 | [exit] 退出");
            System.out.println("请输入指令: ");
            // command
            // nextLine读取直到遇到换行符, trim去掉空白字符
            String cmd = scanner.nextLine().trim();

            // 忽略大小写
            if ("exit".equalsIgnoreCase(cmd)) {
                System.exit(0);
            } else if ("list".equalsIgnoreCase(cmd)) {
                System.out.println("---附近设备列表---");
                if (deviceList.isEmpty()) {
                    System.out.println("暂无发现的设备...");
                } else {
                    for (int i = 0; i < deviceList.size(); i++) {
                        System.out.println("["+ i +"]" + deviceList.get(i).getIp() + "(" + deviceList.get(i).getDeviceId() + ")");
                    }
                }
            } else if ("send".equalsIgnoreCase(cmd)) {
                System.out.println("请输入目标设备的序号: ");
                int index;
                try {
                    // 从scanner读一行, 交给Integer解析int, scanner的nextInt不处理换行符
                    index = Integer.parseInt(scanner.nextLine());
                    if (index < 0 || index >= deviceList.size()) {
                        throw new Exception();
                    }
                } catch (Exception e) {
                    System.out.println("序号输入错误!");
                    continue;
                }

                System.out.println("请输入要发送文件路径: ");
                // 去掉引号
                String filePath = scanner.nextLine().replace("\"", "");

                String targetIp = deviceList.get(index).getIp();
                log.info("准备向 {} 发送文件: {}", targetIp, filePath);

                sender.sendFile(targetIp, filePath, new TransferListener() {
                    @Override
                    public void onStart(String fileName, long totalSize) {

                    }

                    @Override
                    public void onProgress(long totalSent, long totalSize, double speedMbps) {
                        double percent = (totalSent * 100.0) / totalSize;

                        // /r只回车不换行, 覆盖当前行, %5.1f代表占5位, 保留1位
                        System.out.printf("\r[发送中] 进度: %5.1f%% | 速度: %5.2f MB/s | 已传: %d / %d Bytes", percent, speedMbps, totalSent, totalSize);
                    }

                    @Override
                    public void onFinish(String fileName, double totalTimeSeconds, double avgSpeedMbps) {
                        System.out.println();
                        log.info("传输完成! [{}] 总耗时: {} 秒, 平均速度: {} MB/s", fileName, String.format("%.2f", totalTimeSeconds), String.format("%.2f", avgSpeedMbps));
                    }

                    @Override
                    public void onError(Throwable e) {

                    }
                });
            }
        }
    }
}
