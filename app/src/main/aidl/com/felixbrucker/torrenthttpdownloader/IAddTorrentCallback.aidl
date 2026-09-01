package com.felixbrucker.torrenthttpdownloader;

interface IAddTorrentCallback {
    void onSuccess(String taskId);
    void onFailure(String error);
}
