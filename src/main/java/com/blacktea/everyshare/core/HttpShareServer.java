package com.blacktea.everyshare.core;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class HttpShareServer {
    private static final Logger log = LoggerFactory.getLogger(HttpShareServer.class);
    // final变量必须初始化
    private final int port;
    private final String saveDir;
    private Javalin app;

    private File fileToShare = null;

    // ConcurrentHashMap 确保高并发线程安全
    // Key - SessionId, Value - FileName
    // Client 发送POST到/api/prepare时
    // HttpShareServer询问是否接收文件
    // 如果接收就分配一个SessionId, 对应这个文件存到Map里, 返回SessionId给Client
    // 当Client真正发送文件的时候, 发送POST请求到/api/upload, 用地址传SessionId
    // HttpShareServer检验SessionId是否在Map里
    private final Map<String, String> activeSessions = new ConcurrentHashMap<>();

    public HttpShareServer(int port, String saveDir) {
        this.port = port;
        this.saveDir = saveDir;
    }

    public void setFileToShare(String filePath) {
        this.fileToShare = new File(filePath);
    }

    public void start() {
        File dir = new File(saveDir);
        if (!dir.exists()) {
            if (!dir.mkdirs()) {
                log.error("无法创建下载目录");
            }
        }

        // 函数式接口
        // public static Javalin create(Consumer<JavalinConfig> config)
        // lambda表达式实现了Consumer<T>里的accept(T t)抽象方法
        // config作为参数t
        // Javalin把默认config传入accept里, 被下面的函数修改
        app = Javalin.create(config -> {
            config.showJavalinBanner = false;
        }).start(port);

        log.info("HTTP服务端已启动, 监听端口: {}", port);

        // app.get("路径", handler)
        // handler 是一个函数式接口
        // public interface Handler {
        // void handle(Context ctx) throws Exception;
        // }
        // ctx 作为参数(get函数提供的)
        // 用户访问 api 时, 执行handler里的代码
        // 注册一个 GET 请求路由, 用户访问 http://ip:port/api/download 时触发
        app.get("/api/download", ctx -> {
            if (fileToShare == null || !fileToShare.exists()) {
                // Context里面的方法返回自身, 所以可以链式调用
                // ctx可以写Response, 也可以读Request
                // 下面是写Response
                ctx.status(404).result("当前设备没有分享任何文件")
                        .header("Content-Type", "text/plain; charset=utf-8");
                return;
            }

            // result 用来设置 HTTP 响应体(Response Body)
            // 不支持分片请求与断点续传
            // ctx.result(new FileInputStream(fileToShare));
            // writeSeekableStream支持 HTTP Range请求
            ctx.writeSeekableStream(new FileInputStream(fileToShare),
                    "application/octet-stream", fileToShare.length());

            // attachment - 主值
            // 分号后面跟子参数name等...
            // 逗号后面代表并列的独立值
            ctx.header("Content-Disposition", "attachment; filename=\"" + fileToShare.getName() + "\"");
            //ctx.header("Content-Length", String.valueOf(fileToShare.length()));
            //ctx.contentType("application/octet-stream");

            // ctx里既有Request, 也有Response, 互不影响
            log.info("设备({}:{})正在下载文件: {}", ctx.ip(), ctx.port(), fileToShare.getName());
        }); // 函数结束时, Javalin发送响应头, 响应体, 关闭

        // 注册一个POST api, Client发送POST请求触发
        app.post("/api/prepare", ctx -> {
            // 从Client请求的地址解析出fileName, size, ip
            String fileName = ctx.queryParam("name");
            String fileSizeStr = ctx.queryParam("size");
            String senderIp = ctx.ip();

            if (fileName == null || fileSizeStr == null) {
                ctx.status(400).result("缺少参数 name 或 size");
                return;
            }

            log.info("收到来自 [{}] 的文件传输申请 | 文件名: {}, 大小: {} 字节", senderIp, fileName, fileSizeStr);

            // 后续加入确认是否接收
            boolean accepted = true;

            if (accepted) {
                // 8位sessionId, 后续收到api/upload时校验
                String sessionId = UUID.randomUUID().toString().substring(0, 8);

                // 把sessionId和fileName放入Map, 后续知道sessionId就知道fileName
                // 加上fileSize, 后面计算进度需要
                activeSessions.put(sessionId, fileName + ":" + fileSizeStr);

                log.info("已同意接收文件. SessionId: {}", sessionId);

                // 用Map向Client返回Json(都是键与值)
                // 键是String, 值可以是很多对象
                Map<String, Object> response = new HashMap<>();
                response.put("accepted", true);
                response.put("sessionId", sessionId);
                // 把response放入ctx准备返回给Client
                // 自动把对象序列化
                ctx.json(response);
            } else {
                log.info("已拒绝接收来自 [{}] 的文件: {}", senderIp, fileName);
                ctx.json(Map.of("accepted", false));
            }
        });


        // 接收文件
        app.post("api/upload", ctx -> {
            // 从地址中解析出sessionId
            String sessionId = ctx.queryParam("sessionId");

            // 去Map校验sessionId
            if (sessionId == null || !activeSessions.containsKey(sessionId)) {
                log.warn("检测到无效sessionId, IP: {}", ctx.ip());
                // 401未授权, 400错误请求
                ctx.status(401).result("sessionId无效");
                return;
            }

            // 用sessionId取出fileName并删除这个sessionId
            // 通过":"分隔解析出name和size
            String sessionData = activeSessions.remove(sessionId);
            String[] parts = sessionData.split(":");
            String fileName = parts[0];
            long fileSize = Long.parseLong(parts[1]);

            File destFile = new File(saveDir, fileName);

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
                System.out.printf("\r接收 [%s] [%s] %d%% (%.2f MB/s)", displayName, bar.toString(), progress, speed);
                if (read >= total) {
                    System.out.println();
                }
            };

            log.info("开始接收文件, 保存路径: {}", destFile.getAbsolutePath());

            try (InputStream netStream = ctx.bodyInputStream()) {
                ProgressInputStream progressStream = new ProgressInputStream(netStream, fileName,
                        fileSize, progressListener);
                // 输入流, 输出文件, 如果存在则替换
                // 代替了使用while循环读写文件的操作
                Files.copy(progressStream, destFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("文件 [{}] 接收成功!", fileName);
                ctx.result("success");
            } catch (Exception e) {
                log.error("文件接收失败: {}", fileName, e);
                // 500 - 服务器内部错误
                ctx.status(500).result("写入磁盘失败");
            }
        });
    }
}
