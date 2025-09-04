package raft;

/**
 * Represents the current role of a Raft node.
 * 
 * FOLLOWER: Passive state, responds to requests from leaders and candidates
 * CANDIDATE: Active state during leader election, requests votes from other nodes
 * LEADER: Active state, handles all client requests and replicates log entries
 */
public enum Role {
    FOLLOWER,
    CANDIDATE,
    LEADER
}
