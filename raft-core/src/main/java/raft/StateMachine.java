package raft;

/**
 * Interface for a state machine that applies committed log entries.
 * The state machine processes commands in the same order across all nodes.
 */
public interface StateMachine {
    
    /**
     * Apply a command to the state machine.
     * This method must be deterministic and side-effect free.
     * 
     * @param command the command bytes to apply
     * @return the result of applying the command
     */
    byte[] apply(byte[] command);
    
    /**
     * Query the state machine without modifying it.
     * This method can be called on any node, not just the leader.
     * 
     * @param query the query bytes
     * @return the result of the query
     */
    byte[] query(byte[] query);
    
    /**
     * Get the current state of the state machine.
     * 
     * @return a snapshot of the current state
     */
    byte[] getSnapshot();
    
    /**
     * Apply a snapshot to restore the state machine.
     * 
     * @param snapshot the snapshot data
     */
    void applySnapshot(byte[] snapshot);
}
