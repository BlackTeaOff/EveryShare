package com.blacktea.everyshare.core;

// 发送过程中, FileSender调用它发送传输信息等数据
public interface TransferListener {
    // 传输开始前触发
    void onStart(String fileName, long totalSize);

    // 传输过程中持续触发, 前两个参数用于计算百分比
    void onProgress(long totalSent, long totalSize, double speedMbps);

    void onFinish(String fileName, double totalTimeSeconds, double avgSpeedMbps);

    void onError(Throwable e);
}
