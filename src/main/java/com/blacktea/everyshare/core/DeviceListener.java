package com.blacktea.everyshare.core;

// 用来给其他类使用DiscoveryService
// 需要得到监听信息的类(如UI类)实现这个接口, 并给出自己的处理方式
// DiscoveryService里需要注入这个Listener, 监听到时调用那个类的onDeviceFound方法, 交给他来处理
public interface DeviceListener {
    void onDeviceFound(Device device);
}
