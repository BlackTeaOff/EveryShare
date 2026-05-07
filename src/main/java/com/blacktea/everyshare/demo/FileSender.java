package com.blacktea.everyshare.demo;

import com.blacktea.everyshare.core.TransferListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.Socket;

public class FileSender {
    private static final Logger log = LoggerFactory.getLogger(FileSender.class);
    private static final int TCP_PORT = 9999;

    // 目标IP和要发送的文件路径
    // FileSender不应该和某次具体的发送任务绑定, 某一次发送任务的参数不应该放在FileSender里
    // 应该作为函数的参数, 每次发送调用一次
    // listener接收传输信息
    public void sendFile(String targetIp, String filePath, TransferListener listener) {
        File file = new File(filePath);
        if (!file.exists()) {
            log.error("发送文件不存在: {}", filePath);
            if (listener != null) {
                listener.onError(new Exception("文件不存在: " + filePath));
            }
            return;
        }

        new Thread(() -> {
            log.info("正在连接目标设备: {}", targetIp);
            try (Socket socket = new Socket(targetIp, TCP_PORT);
                 // 向另一端Output的Stream
                 // 装饰器模式, 把最基础的OutputStream给高级的DataOutputStream, 由它调用OutputStream
                 DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
                 // 从文件Input的Stream
                 FileInputStream fis = new FileInputStream(file);
            ) { // try-with-resources, 自动close socket和stream
                // 协议, 先发文件名, 文件大小
                dos.writeUTF(file.getName()); // 以UTF-8编码发送字符串
                long totalSize = file.length();
                dos.writeLong(totalSize); // 发送Long类型文件, DataOutputStream特有

                // 将缓冲区的数据立即写入到底层输入流, 而不是等缓冲区满才写出
                dos.flush();

                log.info("连接成功, 开始传输文件");

                byte[] buffer = new byte[8192];
                int readBytes;
                long totalSent = 0;

                // 速度计算相关变量
                long startTime = System.currentTimeMillis();
                long lastUpdateTime = startTime; // 上次刷新UI时间
                long lastTotalSent = 0; // 上次刷新UI时已发送的字节数

                while ((readBytes = fis.read(buffer)) != -1) {
                    dos.write(buffer, 0, readBytes);
                    totalSent += readBytes;

                    long currentTime = System.currentTimeMillis();
                    long timeDiff = currentTime - lastUpdateTime;

                    // 每隔1s更新进度
                    if (timeDiff >= 1000) {
                        // 计算这1s内发送的字节数
                        long bytesSentInInterval = totalSent - lastTotalSent;

                        // 计算速度(MillionBytesPerSecond MB/s, 1048576是1024×1024)
                        double speedMbps = (bytesSentInInterval / 1048576.0) / (timeDiff / 1000.0);

                        // 传输文件的信息传给调用者
                        if (listener != null) {
                            listener.onProgress(totalSent, totalSize, speedMbps);
                        }

                        lastUpdateTime = System.currentTimeMillis();
                        lastTotalSent = totalSent;
                    }
                }

                dos.flush();
                log.info("文件[{}]发送完毕! 总大小: {}", file.getName(), totalSent);

                long endTime = System.currentTimeMillis();
                double totalTimeSeconds = (endTime - startTime) / 1000.0;
                double avgSpeedMBps = (totalSize / 1048576.0) / (totalTimeSeconds == 0 ? 0.001 : totalTimeSeconds);

                if (listener != null) {
                    listener.onFinish(file.getName(), totalTimeSeconds, avgSpeedMBps);
                }

            } catch (Exception e) {
                log.error("发送文件到 {} 失败", targetIp);
            }
        }).start();
    }
}
