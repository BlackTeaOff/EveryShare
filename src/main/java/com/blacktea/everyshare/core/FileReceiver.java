package com.blacktea.everyshare.core;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.net.ServerSocket;
import java.net.Socket;

public class FileReceiver {
    private static final Logger log = LoggerFactory.getLogger(FileReceiver.class);

    // UDP用来发现设备, 拿到IP
    // TCP用来文件传输, 能保证文件数据不丢失, 按顺序到达(点对点)
    private static final int TCP_PORT = 9999;

    // 不同平台路径不同, FileReceiver只负责传文件, 路径需要调用者传入
    private final String saveDir;

    public FileReceiver(String saveDir) {
        this.saveDir = saveDir;
    }

    // 设计协议
    // A->B发文件, B需要先知道: 文件名 文件大小

    public void start() {
        File dir = new File(saveDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                log.error("无法创建目录");
            }
        }

        // 先准备好环境(主线程), 之后再开线程监听传文件的请求
        new Thread(() -> { // try-with-resources, 会自动close
            // ServiceSocket只负责接收请求, 接收后交给Socket处理
            try (ServerSocket serverSocket = new ServerSocket(TCP_PORT)) {
                log.info("TCP文件接收端启动, 监听端口: {}", TCP_PORT);

                // 不断监听是否有发送文件的请求(支持多用户同时传文件)
                while (true) {
                    Socket socket = serverSocket.accept();
                    String senderIp = socket.getInetAddress().getHostAddress();
                    log.info("设备已连接! IP: {}", senderIp);
                }

            } catch (Exception e) {
                log.error("TCP接收端异常", e);
            }
        }).start();
    }
}
