package com.blacktea.everyshare.core;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class HttpFileSender {
    private static final Logger log = LoggerFactory.getLogger(HttpFileSender.class);
    // 向HttpShareServer请求的端口号
    private static final int TCP_PORT = 9999;

    // 客户端
    private final HttpClient httpClient;
    // Jackson类里的, 用来解析服务端返回的json
    private final ObjectMapper objectMapper;

    public HttpFileSender() {
        // 链式调用build HttpClient, 前面都是返回Builder, build返回HttpClient
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).build();
        this.objectMapper = new ObjectMapper();
    }

    public void sendFile(String targetIp, File file) {
        if (file == null || !file.exists() || !file.isFile()) {
            log.error("发送失败: 文件不存在或路径无效");
            return;
        }

        // 开一个独立的线程来进行网络传输, 避免卡死控制台
        // 执行完下面的new Thread, sendFile退出
        // 下面独立一个线程执行, 不影响主线程
        new Thread(() -> {
            try {
                log.info("准备向 [{}] 发起传输申请...", targetIp);

                String fileName = file.getName();
                long fileSize = file.length();

                // name作为参数放入链接中, 可能为中文或空格, 必须进行 URL 编码
                String encodedName = URLEncoder.encode(fileName, StandardCharsets.UTF_8);
                String prepareUrl = String.format("http://%s:%d/api/prepare?name=%s&size=%d",
                        targetIp, TCP_PORT, encodedName, fileSize);

                // 构建一个指向 prepareUrl 地址的
                // 不携带任何提交数据的 POST 请求
                // BodyPublishers是Body的生成器
                HttpRequest prepareRequest = HttpRequest.newBuilder()
                        .uri(URI.create(prepareUrl))
                        .POST(HttpRequest.BodyPublishers.noBody())
                        .build();

                // 泛型表示响应体被解析后的最终数据类型
                // BodyHandlers决定如何把网络字节流转换为<T>类型的转换器
                // 它把字节流转换为<T>类型放在Response里
                HttpResponse<String> prepareResponse = httpClient.send(
                        prepareRequest,
                        HttpResponse.BodyHandlers.ofString()
                );

                // JsonNode 是 Jackson 里用来表示 JSON 树状结构的类
                // 可以理解成一个特殊的Map
                // 不需要给json里的键与值单独写一个类(DTO)
                JsonNode jsonNode = objectMapper.readTree(prepareResponse.body());
                boolean accepted = jsonNode.get("accepted").asBoolean();

                if (!accepted) {
                    log.info("传输被拒绝: 接收端 [{}] 拒绝接收文件: {}", targetIp, fileName);
                    return;
                }

                // 提取 sessionId
                String sessionId = jsonNode.get("sessionId").asText();
                log.info("接收端接收传输, sessionId: {}, 准备上传数据", sessionId);

                // 准备上传数据
                String uploadUrl = String.format("http://%s:%d/api/upload?sessionId=%s",
                        targetIp, TCP_PORT, sessionId);

                // 定义ProgressListener, ProgressInputStream隔一段时间调用回报进度信息
                // 里面具体定义打印的逻辑
                ProgressListener progressListener = (name, read, total, speed) -> {
                    double percent = (double) read / total;
                    int progress = (int) (percent * 100);

                    // 裁剪文件名
                    String displayName = name;
                    if (name.length() > 25) {
                        displayName = name.substring(0, 10) + "..." + name.substring(name.length() - 10);
                    }

                    // 字符进度条
                    int barLength = 20;
                    int filledLength = (int) (percent * barLength);
                    StringBuilder bar = new StringBuilder();
                    for (int i = 0; i < barLength; i++) {
                        bar.append(i < filledLength ? "█" : "░");
                    }

                    // 单行刷新(\r: 返回当前行开头)
                    System.out.printf("\r发送 [%s] [%s] %d%% (%.2f MB/s)", displayName, bar.toString(), progress, speed);
                    if (read >= total) {
                        System.out.println();
                    }
                };

                try (FileInputStream fis = new FileInputStream(file);
                    ProgressInputStream progressStream = new ProgressInputStream(fis, fileName,
                            fileSize, progressListener);) {

                    HttpRequest uploadRequest = HttpRequest.newBuilder()
                            .uri(URI.create(uploadUrl))
                            .header("Content-Type", "application/octet-stream")
                            // 没有参数的lambda匿名函数, progressStream就是函数体
                            .POST(HttpRequest.BodyPublishers.ofInputStream(() -> progressStream))
                            .build();

                    HttpResponse<String> uploadResponse = httpClient.send(
                            uploadRequest,
                            HttpResponse.BodyHandlers.ofString()
                    );

                    if (uploadResponse.statusCode() == 200 && "success".equals(uploadResponse.body())) {
                        log.info("文件 [{}] 发送成功", fileName);
                    } else {
                        log.error("文件发送失败, 错误码: {}, 错误信息: {}", uploadResponse.statusCode(), uploadResponse.body());
                    }
                } catch (Exception e) {
                    log.error("发送文件过程中出现异常: {}", file.getName(), e);
                }
            } catch(Exception e) {
                log.error("发送文件过程中出现异常: {}", file.getName(), e);
            }
        }).start();
    }
}
