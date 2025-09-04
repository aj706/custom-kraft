package raft;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

/**
 * Simple key-value store implementation of StateMachine.
 * Supports basic GET, PUT, DELETE operations.
 */
public class KeyValueStore implements StateMachine {
    
    private final Map<String, String> store = new ConcurrentHashMap<>();
    
    @Override
    public byte[] apply(byte[] command) {
        try {
            String cmd = new String(command, StandardCharsets.UTF_8);
            String[] parts = cmd.split("\\|");
            
            if (parts.length < 2) {
                return "ERROR: Invalid command format".getBytes(StandardCharsets.UTF_8);
            }
            
            String operation = parts[0].toUpperCase();
            String key = parts[1];
            
            switch (operation) {
                case "PUT":
                    if (parts.length < 3) {
                        return "ERROR: PUT requires key and value".getBytes(StandardCharsets.UTF_8);
                    }
                    String value = parts[2];
                    store.put(key, value);
                    return ("OK: PUT " + key + " = " + value).getBytes(StandardCharsets.UTF_8);
                    
                case "DELETE":
                    String deletedValue = store.remove(key);
                    if (deletedValue != null) {
                        return ("OK: DELETED " + key + " = " + deletedValue).getBytes(StandardCharsets.UTF_8);
                    } else {
                        return ("OK: KEY NOT FOUND " + key).getBytes(StandardCharsets.UTF_8);
                    }
                    
                case "GET":
                    String retrievedValue = store.get(key);
                    if (retrievedValue != null) {
                        return retrievedValue.getBytes(StandardCharsets.UTF_8);
                    } else {
                        return "KEY_NOT_FOUND".getBytes(StandardCharsets.UTF_8);
                    }
                    
                default:
                    return ("ERROR: Unknown operation " + operation).getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return ("ERROR: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
        }
    }
    
    @Override
    public byte[] query(byte[] query) {
        try {
            String cmd = new String(query, StandardCharsets.UTF_8);
            if (cmd.startsWith("GET|")) {
                String key = cmd.substring(4);
                String value = store.get(key);
                if (value != null) {
                    return value.getBytes(StandardCharsets.UTF_8);
                } else {
                    return "KEY_NOT_FOUND".getBytes(StandardCharsets.UTF_8);
                }
            } else if (cmd.equals("SIZE")) {
                return String.valueOf(store.size()).getBytes(StandardCharsets.UTF_8);
            } else if (cmd.equals("KEYS")) {
                return String.join(",", store.keySet()).getBytes(StandardCharsets.UTF_8);
            } else {
                return ("ERROR: Unknown query " + cmd).getBytes(StandardCharsets.UTF_8);
            }
        } catch (Exception e) {
            return ("ERROR: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
        }
    }
    
    @Override
    public byte[] getSnapshot() {
        try {
            StringBuilder snapshot = new StringBuilder();
            for (Map.Entry<String, String> entry : store.entrySet()) {
                snapshot.append(entry.getKey()).append("=").append(entry.getValue()).append("\n");
            }
            return snapshot.toString().getBytes(StandardCharsets.UTF_8);
        } catch (Exception e) {
            return ("ERROR: " + e.getMessage()).getBytes(StandardCharsets.UTF_8);
        }
    }
    
    @Override
    public void applySnapshot(byte[] snapshot) {
        try {
            store.clear();
            String snapshotStr = new String(snapshot, StandardCharsets.UTF_8);
            String[] lines = snapshotStr.split("\n");
            
            for (String line : lines) {
                if (line.trim().isEmpty()) continue;
                String[] parts = line.split("=", 2);
                if (parts.length == 2) {
                    store.put(parts[0], parts[1]);
                }
            }
        } catch (Exception e) {
            // Log error but don't fail
            System.err.println("Error applying snapshot: " + e.getMessage());
        }
    }
    
    /**
     * Get the current size of the store.
     */
    public int size() {
        return store.size();
    }
    
    /**
     * Check if the store contains a key.
     */
    public boolean containsKey(String key) {
        return store.containsKey(key);
    }
}
