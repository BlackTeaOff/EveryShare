package com.blacktea.everyshare.core;

import lombok.Data;

@Data
public class Device {
    private String ip;
    private String deviceId;
    private String osName;

    public Device(String ip, String deviceId) {
        this.ip = ip;
        this.deviceId = deviceId;
    }
}
