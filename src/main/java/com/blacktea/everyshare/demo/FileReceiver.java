package com.blacktea.everyshare.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

// 全局常驻服务, 只创建一次
public class FileReceiver {
    private static final Logger log = LoggerFactory.getLogger(FileReceiver.class);

    // UDP用来发现设备, 拿到IP
    // TCP用来文件传输, 能保证文件数据不丢失, 按顺序到达(点对点)
    private static final int TCP_PORT = 9999;

    // 不同平台路径不同, FileReceiver只负责传文件, 路径需要调用者传入
    // 修改saveDir只能重启FileReceiver服务(final)
    private final String SAVE_DIR;

    public FileReceiver(String saveDir) {
        this.SAVE_DIR = saveDir;
    }

    public void start() {
        File dir = new File(SAVE_DIR);
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

                    // 开启新线程传输, 同时继续监听请求
                    new Thread(() -> handleFileReceive(socket)).start();
                }

            } catch (Exception e) {
                log.error("TCP接收端异常", e);
            }
        }).start();
    }

    // 对于每一个传输任务, 开启一个新线程处理, 各传输任务互不影响
    private void handleFileReceive(Socket socket) {
        // DataInputStream操作InputStream, 方便读取各种基本数据类型(int, long, String)
        try (DataInputStream dis = new DataInputStream(socket.getInputStream())) {
            // 根据协议, 先传文件名, 之后传文件大小, 最后传文件
            String fileName = dis.readUTF();

            long fileLength = dis.readLong();

            log.info("准备接收文件: [{}], 大小: {} Bytes", fileName, fileLength);

            File saveFile = new File(SAVE_DIR + fileName);

            // 从socket输出流, 写入文件输出流
            try (FileOutputStream fos = new FileOutputStream(saveFile)) {
                byte[] buffer = new byte[8192];
                int readBytes;
                long totalRead = 0;

                while ((readBytes = dis.read(buffer)) != -1) {
                    fos.write(buffer, 0, readBytes);
                    totalRead += readBytes;
                }
                log.info("文件接收成功, 保存至: {}, 总大小: {}", saveFile.getAbsolutePath(), totalRead);
            } catch (Exception e) {
                log.error("文件接收失败", e);
            }
        } catch (Exception e) {
            log.error("文件接收失败", e);
        }
    }
}
