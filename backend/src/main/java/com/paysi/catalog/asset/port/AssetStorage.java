package com.paysi.catalog.asset.port;

public interface AssetStorage {
    void save(String storageKey, byte[] content);

    byte[] read(String storageKey);

    void delete(String storageKey);
}
