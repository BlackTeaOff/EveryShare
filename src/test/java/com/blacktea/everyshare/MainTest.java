package com.blacktea.everyshare;

import com.blacktea.everyshare.core.DiscoveryService;

public class MainTest {

    public static void main(String[] args) {
        ReceiverTest receiverTest = new ReceiverTest();
        DiscoveryService discoveryService = new DiscoveryService(receiverTest);
        discoveryService.startReceiver();
        discoveryService.startBroadcaster();
    }
}
