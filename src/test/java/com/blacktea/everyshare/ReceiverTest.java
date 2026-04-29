package com.blacktea.everyshare;

import com.blacktea.everyshare.core.Device;
import com.blacktea.everyshare.core.DeviceListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ReceiverTest implements DeviceListener {

    private static final Logger log = LoggerFactory.getLogger(ReceiverTest.class);

    @Override
    public void onDeviceFound(Device device) {
        log.debug("回调ReceiverTest");
    }
}
