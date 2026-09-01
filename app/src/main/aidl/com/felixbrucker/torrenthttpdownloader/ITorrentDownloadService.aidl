package com.felixbrucker.torrenthttpdownloader;

import com.felixbrucker.torrenthttpdownloader.IAddTorrentCallback;
import com.felixbrucker.torrenthttpdownloader.IRemoveTorrentCallback;
import com.felixbrucker.torrenthttpdownloader.ITorrentDownloadCallback;
import com.felixbrucker.torrenthttpdownloader.AddTorrentParams;

interface ITorrentDownloadService {
    void addTorrent(in AddTorrentParams params, IAddTorrentCallback callback);
    void removeTorrent(String taskId, boolean deleteFiles, boolean deleteTorrentFile, IRemoveTorrentCallback callback);
    void registerCallback(ITorrentDownloadCallback callback);
    void unregisterCallback(ITorrentDownloadCallback callback);
}
