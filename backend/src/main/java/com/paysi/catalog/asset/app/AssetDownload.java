package com.paysi.catalog.asset.app;

public record AssetDownload(String contentType, byte[] content) {
    public AssetDownload {
        content = content.clone();
    }

    @Override
    public byte[] content() {
        return content.clone();
    }
}
