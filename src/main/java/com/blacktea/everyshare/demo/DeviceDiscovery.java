package com.blacktea.everyshare.demo;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.util.UUID;

public class DeviceDiscovery {

    // 局域网通信端口
    private static final int PORT = 8888;

    // UUID(Universally Unique Identifier)通用唯一识别码
    // UDP Broadcast中, 自己向局域网发送消息, 自己也会收到这条消息, 对比DEVICE_ID能判断消息是自己发的还是别人发的
    // randomUUID返回UUID对象, 转换为字符串并截取8位即可(原128位)
    private static final String DEVICE_ID = UUID.randomUUID().toString().substring(0, 8);

    public static void main(String[] args) {
        System.out.println("我的设备ID: " + DEVICE_ID);

        // Thread对应操作系统的原生线程
        // () -> lambda表达式, 用于函数式接口(接受函数为参数的), ()里面是参数, 后面是方法体
        // Thread构造函数接收的是Runnable, Runnable是一个函数式接口
        // 函数式接口是一个接口里只有一个抽象方法, 使用lambda传入的函数可以直接对应(实现)这个抽象方法
        // Java不支持直接把函数当作参数传入其他函数, Runnable就是专门存这个函数的类, 它可以作为参数传入
        // Thread receiverThread = new Thread(() -> startReceiver());

        // 方法引用, 和lambda表达式功能相同, 更简介, lambda有方法体(匿名函数)里就不能用了, 只有调用现有方法才可以用
        Thread receiverThread = new Thread(DeviceDiscovery::startReceiver);
        receiverThread.start();

        Thread senderThread = new Thread(DeviceDiscovery::startBroadcaster);
        senderThread.start();
    }

    // 监听线程
    private static void startReceiver() {
        try {
            // 负责UDP接收和发送数据包(参数是端口号, 传入null先不绑定端口, 绑定后很多底层的设置就不能修改)
            DatagramSocket socket = new DatagramSocket(null);
            // 开两个程序测试, 需要端口复用, 多个进程可以绑定到同一个UDP端口上, 广播包发来时, 系统会给每个绑定的程序发一份
            socket.setReuseAddress(true);

            // 设置好了, 绑定端口
            socket.bind(new InetSocketAddress(PORT));

            // 接收用的字节数组
            byte[] buffer = new byte[1024];
            // 收到包会把内容放进buffer
            DatagramPacket packet = new DatagramPacket(buffer, buffer.length);

            System.out.println("开始监听");
            while (true) {
                // 阻塞等待接收, 运行到这一行时, 会停下来
                // 收到包放到packet里
                socket.receive(packet);

                // 字节流还原为字符串
                String message = new String(packet.getData(), 0, packet.getLength());
                // getAddress返回InetAddress对象, 包含了IP地址的所有信息, 但不是字符串
                // getHostAddress是InetAddress类的一个方法, 把IP提取出来变成字符串
                String senderIp = packet.getAddress().getHostAddress();

                if (!message.contains(DEVICE_ID)) {
                    System.out.println("发现新设备! IP: " + senderIp + " | 消息: " + message);
                } else {
                    // System.out.println("监听到自身广播");
                }
            }
        } catch (Exception e){
            e.printStackTrace();
        }
    }

    private static void startBroadcaster() {
        try {
            // 发送方不需要本地绑定端口(不需要监听)
            DatagramSocket socket = new DatagramSocket();
            // 广播需要权限(会影响局域网里所有设备)
            socket.setBroadcast(true);

            // InetAddress只包含IP地址, 不包含端口信息
            // InetSocketAddress包含端口信息
            // 255.255.255.255代表当前局域网内所有设备
            InetAddress broadcastAddress = InetAddress.getByName("255.255.255.255");

            while (true) {
                String message = "EveryShare:" + DEVICE_ID;
                byte[] buffer = message.getBytes();

                // 把信息装入数据包
                DatagramPacket packet = new DatagramPacket(buffer, buffer.length, broadcastAddress, PORT);

                // socket会读取packet里的IP和PORT进行发送

                System.out.println("广播ing");

                socket.send(packet);

                Thread.sleep(3000);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
