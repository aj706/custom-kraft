package raft;

import java.util.HashMap;
import java.util.Map;

/**
 * Demo cluster that runs 3 Raft nodes locally for testing.
 * Creates a simple 3-node cluster where nodes communicate via gRPC.
 */
public class DemoCluster {
    
    private static final int BASE_PORT = 5001;
    private static final String[] NODE_IDS = {"n1", "n2", "n3"};
    
    public static void main(String[] args) throws Exception {
        System.out.println("Starting Raft demo cluster with 3 nodes...");
        
        // Create peer configuration (each node knows about all others)
        Map<String, String> peers = new HashMap<>();
        for (String nodeId : NODE_IDS) {
            peers.put(nodeId, "localhost:" + (BASE_PORT + getNodeIndex(nodeId)));
        }
        
        // Create and start all nodes
        RaftServer[] servers = new RaftServer[NODE_IDS.length];
        RaftNode[] nodes = new RaftNode[NODE_IDS.length];
        
        for (int i = 0; i < NODE_IDS.length; i++) {
            String nodeId = NODE_IDS[i];
            int port = BASE_PORT + i;
            
            // Create node and server
            nodes[i] = new FollowerNode(nodeId, peers);
            servers[i] = new RaftServer(port, nodes[i]);
            
            // Start the node and server
            nodes[i].start();
            servers[i].start();
        }
        
        System.out.println("All nodes started. Press Ctrl+C to stop.");
        
        // Wait for shutdown
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            System.out.println("\nShutting down cluster...");
            for (RaftServer server : servers) {
                server.stop();
            }
            for (RaftNode node : nodes) {
                try {
                    node.stop();
                } catch (Exception e) {
                    // Ignore shutdown errors
                }
            }
        }));
        
        // Keep main thread alive
        Thread.currentThread().join();
    }
    
    private static int getNodeIndex(String nodeId) {
        for (int i = 0; i < NODE_IDS.length; i++) {
            if (NODE_IDS[i].equals(nodeId)) {
                return i;
            }
        }
        throw new IllegalArgumentException("Unknown node ID: " + nodeId);
    }
}
