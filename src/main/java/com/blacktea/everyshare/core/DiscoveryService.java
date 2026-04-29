package com.blacktea.everyshare.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.*;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

public class DiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryService.class);
    private static final String DEVICE_ID = UUID.randomUUID().toString().substring(0, 8);
    private static final int PORT = 8888;

    // 调用者的监听器, 一旦监听到则调用里面的onDeviceFound方法, 把Device交给调用者处理
    private final DeviceListener listener;

    // 构造必须传入listener
    public DiscoveryService(DeviceListener listener) {
        this.listener = listener;
    }

    public void startReceiver() {
        new Thread(()-> { // try-with-resources, 自动调用socket.close
            log.info("设备ID {}", DEVICE_ID);
            try (DatagramSocket socket = new DatagramSocket(null)) {
                socket.setReuseAddress(true);
                socket.bind(new InetSocketAddress(PORT));
                log.info("接收端启动, 监听端口: {}", PORT);

                byte[] buffer = new byte[1024];
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

                while (true) {
                    socket.receive(packet);
                    String message = new String(packet.getData(), 0, packet.getLength());
                    String senderIp = packet.getAddress().getHostAddress();

                    // 解析字符串格式(EveryShare:DEVICE_ID)
                    if (message.startsWith("EveryShare:") && !message.contains(DEVICE_ID)) {
                        String deviceId = message.split(":")[1];

                        log.debug("收到来自 {} 的广播: {}", senderIp, deviceId);

                        // 把Device交给调用者处理
                        if (listener != null) {
                            Device foundDevice = new Device(senderIp, deviceId);
                            listener.onDeviceFound(foundDevice);
                        }
                    }
                }
            } catch (Exception e) {
                log.error("监听异常", e);
            }
        }).start();
    }

    public void startBroadcaster() {
        new Thread(() -> {
            try (DatagramSocket socket = new DatagramSocket()) {
                socket.setBroadcast(true);

                log.info("广播端启动");
                while (true) {
                    String message = "EveryShare:" + DEVICE_ID;
                    byte[] buffer = message.getBytes();

                    // 获取每个网卡, 依次广播
                    List<NetworkInterface> list = Collections.list(NetworkInterface.getNetworkInterfaces());

                    for (NetworkInterface ni : list) {
                        // 跳过未启用, 回环广播
                        if (!ni.isUp() || ni.isLoopback()) {
                            continue;
                        }

                        // InetAddress里只有IP和主机名
                        // InterfaceAddress有InetAddress(该网卡的IP), Broadcast地址, 子网掩码长度
                        // 一个网卡会返回ipv4和ipv6地址, 也可以有多ipv4地址
                        for (InterfaceAddress ia : ni.getInterfaceAddresses()) {
                            InetAddress broadcast = ia.getBroadcast();

                            // ipv6地址没有广播
                            if (broadcast == null) {
                                continue;
                            }

                            DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcast, PORT);
                            socket.send(packet);

                            log.debug("向网卡 {} 的广播地址 {} 发送广播", ni.getDisplayName(), broadcast.getHostAddress());
                        }
                    }
                    log.debug("广播周期结束, 3s后继续");
                    Thread.sleep(3000);
                }
            } catch (Exception e) {
                log.error("广播异常", e);
            }
        }).start();
    }
}
