package com.example.mcp_github.service;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Service for persistent memory management using a local JSON file.
 */
@Service
public class MemoryService {

    @Value("${memory.file.path:${user.home}/.mcp-github/memory.json}")
    private String memoryFilePath;

    private final ObjectMapper objectMapper = new ObjectMapper();

    public void remember(String key, String value) {
        try {
            File file = new File(memoryFilePath);
            file.getParentFile().mkdirs(); // ← crée le dossier si absent
            Map<String, String> memory = loadMemory();
            memory.put(key, value);
            objectMapper.writeValue(file, memory);
        } catch (Exception e) {
            throw new RuntimeException("Error saving memory: " + e.getMessage());
        }
    }

    public String recall(String key) {
        try {
            return loadMemory().getOrDefault(key, null);
        } catch (Exception e) {
            throw new RuntimeException("Error reading memory: " + e.getMessage());
        }
    }

    public Map<String, String> recallAll() {
        try {
            return loadMemory();
        } catch (Exception e) {
            throw new RuntimeException("Error reading memory: " + e.getMessage());
        }
    }

    public void forget(String key) {
        try {
            Map<String, String> memory = loadMemory();
            memory.remove(key);
            objectMapper.writeValue(new File(memoryFilePath), memory); // ✅ corrigé
        } catch (Exception e) {
            throw new RuntimeException("Error deleting memory: " + e.getMessage());
        }
    }

    public void forgetAll() {
        try {
            File file = new File(memoryFilePath);
            file.getParentFile().mkdirs();
            objectMapper.writeValue(file, new HashMap<>()); // ✅ corrigé
        } catch (Exception e) {
            throw new RuntimeException("Error clearing memory: " + e.getMessage());
        }
    }

    @SuppressWarnings("unchecked")
    private Map<String, String> loadMemory() throws Exception {
        File file = new File(memoryFilePath);
        if (!file.exists() || file.length() == 0) { // ← ajouter file.length() == 0
            return new HashMap<>();
        }
        return objectMapper.readValue(file, Map.class);
    }
}
