package com.blacktea.everyshare.demo;

import com.blacktea.everyshare.core.Device;
import com.blacktea.everyshare.core.DiscoveryService;
import com.blacktea.everyshare.core.HttpFileSender;
import com.blacktea.everyshare.core.HttpShareServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.util.List;
import java.util.Scanner;
import java.util.concurrent.CopyOnWriteArrayList;

public class HttpTerminal {
    private static final Logger log = LoggerFactory.getLogger(HttpTerminal.class);
    static String defaultSavePath = System.getProperty("user.dir") + File.separator + "downloads" + File.separator;
    private static final int PORT = 9999;

    // 线程安全列表(一边读一边写)
    private static final List<Device> deviceList = new CopyOnWriteArrayList<>();

    public static void main(String[] args) {
        System.out.println("====================EveryShare====================");
        log.info("默认保存目录: {}", defaultSavePath);

        HttpShareServer receiver = new HttpShareServer(PORT, defaultSavePath);
        receiver.start();

        DiscoveryService discovery = new DiscoveryService(device -> {
            // anyMatch 接收 Predicate<T>, 函数式接口
            // 里面仅有一个方法叫做 test(T t)
            // test 告诉anyMatch过滤的规则
            // 参数d是anyMatch调用test传入的
            // anyMatch遍历stream, 把每个device给test
            // device 被复制进匿名函数了
            boolean exists = deviceList.stream().anyMatch(d -> d.getIp().equals(device.getIp()));
            if (!exists) {
                deviceList.add(device);
                log.info("[发现新设备] 序号: [{}], 名称: {}, IP: {}",
                        deviceList.size() - 1, device.getDeviceId(), device.getIp());
                System.out.println("请输入指令 (list / send / exit): ");
            }
        });
        discovery.startReceiver();
        discovery.startBroadcaster();

        HttpFileSender sender = new HttpFileSender();

        // 主线程
        try (Scanner scanner = new Scanner(System.in)) {
            while (true) {
                System.out.print("\n请输入指令 (list / send / exit): ");
                String command = scanner.nextLine().trim().toLowerCase();

                if ("exit".equals(command)) {
                    System.exit(0);
                } else if ("list".equals(command)) {
                    showDeviceList();
                } else if ("send".equals(command)) {
                    handleSend(scanner, sender);
                } else {
                    System.out.println("指令错误");
                }
            }
        } catch (Exception e) {
            log.error("控制台异常",e);
        }
    }

    private static void showDeviceList() {
        if (deviceList.isEmpty()) {
            System.out.println("暂无设备...");
            return;
        }
        System.out.println("---------- 在线设备 ----------");
        for (int i = 0; i < deviceList.size(); i++) {
            Device d = deviceList.get(i);
            // %n - 跨平台换行符
            System.out.printf("[%d] 设备ID: %s | IP地址: %s%n", i, d.getDeviceId(), d.getIp());
        }
        System.out.println("-----------------------------");
    }

    private static void handleSend(Scanner scanner, HttpFileSender sender) {
        if (deviceList.isEmpty()) {
            System.out.println("当前没有发现任何设备");
            return;
        }

        showDeviceList();
        System.out.print("请输入目标设备序号: ");

        int index;
        try {
            index = Integer.parseInt(scanner.nextLine().trim());
                if (index < 0 || index >= deviceList.size()) {
                    throw new IllegalArgumentException();
                }
            } catch (Exception e) {
            System.out.println("序号输入错误");
            return;
        }

        Device targetDevice = deviceList.get(index);

        System.out.print("请输入要发送的文件绝对路径: ");
        String filePath = scanner.nextLine().trim();
        File file = new File(filePath);

        if (!file.exists() || !file.isFile()) {
            System.out.println("找不到文件");
            return;
        }

        sender.sendFile(targetDevice.getIp(), file);
    }
}
