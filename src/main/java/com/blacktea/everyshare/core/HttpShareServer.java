package com.blacktea.everyshare.core;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class HttpShareServer {
    private static final Logger log = LoggerFactory.getLogger(HttpShareServer.class);
    // final变量必须初始化
    private final int port;
    private final String saveDir;
    private Javalin app;

    private File fileToShare = null;

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
                ctx.status(404).result("当前设备没有分享任何文件").header("Content-Type", "text/plain; charset=utf-8");
                return;
            }

            // result 用来设置 HTTP 响应体(Response Body)
            // 不支持分片请求与断点续传
            // ctx.result(new FileInputStream(fileToShare));
            // writeSeekableStream支持 HTTP Range请求
            ctx.writeSeekableStream(new FileInputStream(fileToShare), "application/octet-stream", fileToShare.length());

            // attachment - 主值
            // 分号后面跟子参数name等...
            // 逗号后面代表并列的独立值
            ctx.header("Content-Disposition", "attachment; filename=\"" + fileToShare.getName() + "\"");
            //ctx.header("Content-Length", String.valueOf(fileToShare.length()));
            //ctx.contentType("application/octet-stream");

            // ctx里既有Request, 也有Response, 互不影响
            log.info("设备({}:{})正在下载文件: {}", ctx.ip(), ctx.port(), fileToShare.getName());
        }); // 函数结束时, Javalin发送响应头, 响应体, 关闭
    }
}
