package com.felixbrucker.torrenthttpdownloader;

import com.felixbrucker.torrenthttpdownloader.IAddTorrentCallback;
import com.felixbrucker.torrenthttpdownloader.AddTorrentParams;
import com.felixbrucker.torrenthttpdownloader.TorrentProgressStats;

interface ITorrentDownloadService {
    void addTorrent(in AddTorrentParams params, IAddTorrentCallback callback);
    @nullable TorrentProgressStats getProgress(String taskId);
}
