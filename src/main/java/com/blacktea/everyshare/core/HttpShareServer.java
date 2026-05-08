package com.blacktea.everyshare.core;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

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
    }
}
