package com.felixbrucker.torrenthttpdownloader;

parcelable AddTorrentParams {
    String uri;
    @nullable String name;
    @nullable String destinationSubdirectory;
    boolean createSubfolderByName;
    boolean notifyOnCompletion;
    String fileSelectionMode;
}
