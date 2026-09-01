package com.felixbrucker.torrenthttpdownloader;

interface ITorrentDownloadCallback {
    void onProgressUpdate(String taskId, long bytesDownloaded, long totalBytes, double downloadSpeed);
    void onDownloadCompleted(String taskId);
    void onDownloadFailed(String taskId, String error);
}
