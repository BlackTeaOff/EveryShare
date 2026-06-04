package com.blacktea.everyshare.demo;

import com.blacktea.everyshare.core.HttpShareServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;

public class HttpTerminal {
    private static final Logger log = LoggerFactory.getLogger(HttpTerminal.class);

    static String defaultSavePath = System.getProperty("user.dir") + File.separator + "downloads" + File.separator;

    public static void main(String[] args) {
        HttpShareServer httpShareServer = new HttpShareServer(8888, defaultSavePath);
        httpShareServer.setFileToShare("D:\\zh-cn_windows_10_consumer_editions_version_22h2_updated_april_2025_x64_dvd_a39ebe02.iso");
        httpShareServer.start();
    }
}
