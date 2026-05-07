package com.blacktea.everyshare.core;

import io.javalin.Javalin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class HttpShareServer {
    private static final Logger log = LoggerFactory.getLogger(HttpShareServer.class);
    // final变量必须初始化
    private final int port;
    private final String saveDir;
    private Javalin app;

    public HttpShareServer(int port, String saveDir) {
        this.port = port;
        this.saveDir = saveDir;
    }
}
