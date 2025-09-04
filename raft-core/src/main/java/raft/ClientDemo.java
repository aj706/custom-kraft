package raft;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Simple client demo that connects to a Raft cluster and submits commands.
 * Demonstrates the log replication functionality.
 */
public class ClientDemo {
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Raft Client Demo...");
        
        // Create a simple client that connects to the cluster
        RaftClient client = new RaftClient("localhost:5001");
        
        // Wait for cluster to stabilize
        System.out.println("Waiting for cluster to stabilize...");
        Thread.sleep(2000);
        
        // Submit some test commands
        System.out.println("\n=== Submitting Commands ===");
        
        // PUT operations
        submitCommand(client, "PUT|name|Alice");
        submitCommand(client, "PUT|age|25");
        submitCommand(client, "PUT|city|New York");
        
        // GET operations
        submitCommand(client, "GET|name");
        submitCommand(client, "GET|age");
        submitCommand(client, "GET|city");
        
        // DELETE operation
        submitCommand(client, "DELETE|age");
        
        // Verify deletion
        submitCommand(client, "GET|age");
        
        // Query operations
        submitCommand(client, "SIZE");
        submitCommand(client, "KEYS");
        
        System.out.println("\n=== Demo Complete ===");
        client.close();
    }
    
    private static void submitCommand(RaftClient client, String command) {
        try {
            System.out.printf("Submitting: %s%n", command);
            
            CompletableFuture<byte[]> future = client.submit(command.getBytes());
            byte[] result = future.get(5, TimeUnit.SECONDS);
            
            String resultStr = new String(result);
            System.out.printf("Result: %s%n", resultStr);
            
        } catch (Exception e) {
            System.err.printf("Error submitting command '%s': %s%n", command, e.getMessage());
        }
        
        // Small delay between commands
        try {
            Thread.sleep(100);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

/**
 * Simple Raft client that connects to a cluster node.
 */
class RaftClient {
    private final String target;
    
    public RaftClient(String target) {
        this.target = target;
    }
    
    public CompletableFuture<byte[]> submit(byte[] command) {
        // For now, this is a placeholder that simulates client behavior
        // In a real implementation, this would make gRPC calls to the cluster
        
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        
        // Simulate network delay and processing
        new Thread(() -> {
            try {
                Thread.sleep(100); // Simulate network delay
                future.complete(("Simulated response for: " + new String(command)).getBytes());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }).start();
        
        return future;
    }
    
    public void close() {
        // Cleanup resources
    }
}
