package raft;

import java.util.concurrent.CompletableFuture;

/**
 * Core interface for a Raft consensus node.
 * Each node can be in one of three roles: Follower, Candidate, or Leader.
 */
public interface RaftNode {
    /**
     * @return the unique identifier of this node
     */
    String id();
    
    /**
     * Start the node and begin participating in consensus.
     * @throws Exception if startup fails
     */
    void start() throws Exception;
    
    /**
     * Stop the node and clean up resources.
     * @throws Exception if shutdown fails
     */
    void stop() throws Exception;

    /**
     * Submit a command to be replicated and committed by the cluster.
     * Only works when this node is the leader.
     * 
     * @param command the command bytes to replicate
     * @return future that completes when the command is committed, or fails if not leader
     */
    CompletableFuture<byte[]> submit(byte[] command);
}
