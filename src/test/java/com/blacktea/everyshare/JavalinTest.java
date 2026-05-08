package com.blacktea.everyshare;

import com.blacktea.everyshare.core.HttpShareServer;

import java.io.File;

public class JavalinTest {
    private static final String defaultSavePath = System.getProperty("user.dir") + File.separator + "downloads" + File.separator;

    public static void main(String[] args) {
        HttpShareServer shareServer = new HttpShareServer(9999, defaultSavePath);
        shareServer.start();
    }
}
