package com.felixbrucker.torrenthttpdownloader;

interface IRemoveTorrentCallback {
    void onSuccess(String taskId);
    void onFailure(String error);
}
