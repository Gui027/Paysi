package com.paysi.catalog.asset.adapter;

import com.paysi.catalog.asset.port.AssetStorage;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;

@Component
class LocalAssetStorage implements AssetStorage {
    private final Path root;

    LocalAssetStorage(@Value("${paysi.asset.storage-path:./data/assets}") String storagePath) {
        this.root = Path.of(storagePath).toAbsolutePath().normalize();
    }

    @Override
    public void save(String storageKey, byte[] content) {
        Path target = resolve(storageKey);
        try {
            Files.createDirectories(root);
            Files.write(target, content, StandardOpenOption.CREATE_NEW);
        } catch (IOException error) {
            throw new UncheckedIOException("Não foi possível armazenar o ativo", error);
        }
    }

    @Override
    public byte[] read(String storageKey) {
        try {
            return Files.readAllBytes(resolve(storageKey));
        } catch (IOException error) {
            throw new UncheckedIOException("Não foi possível ler o ativo", error);
        }
    }

    @Override
    public void delete(String storageKey) {
        try {
            Files.deleteIfExists(resolve(storageKey));
        } catch (IOException error) {
            throw new UncheckedIOException("Não foi possível remover o ativo", error);
        }
    }

    private Path resolve(String storageKey) {
        Path target = root.resolve(storageKey).normalize();
        if (!target.getParent().equals(root)) {
            throw new IllegalArgumentException("Chave de armazenamento inválida");
        }
        return target;
    }
}
