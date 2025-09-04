package raft;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import io.grpc.ManagedChannel;
import raft.RaftServiceGrpc;
import raft.RequestVoteRequest;
import raft.RequestVoteResponse;
import raft.AppendEntriesRequest;
import raft.AppendEntriesResponse;
import java.util.List;

/**
 * Implements the Follower role in the Raft consensus algorithm.
 * Followers respond to requests from leaders and candidates, and can become candidates
 * when they don't receive heartbeats for an election timeout period.
 */
public class FollowerNode extends AbstractRaftNode {
    
    public FollowerNode(String id, Map<String, String> peers) throws IOException {
        super(id, peers);
        this.role = Role.FOLLOWER;
    }

    @Override
    protected void onElectionTimeout() {
        // Increment term and vote for self
        currentTerm++;
        votedFor = id;
        
        // Transition to candidate role and start election
        transitionTo(Role.CANDIDATE);
        startElection();
    }

    @Override
    protected void sendHeartbeats() {
        // If we're actually a leader (role changed but object type didn't), send heartbeats
        if (role == Role.LEADER) {
            sendLeaderHeartbeats();
        }
        // Otherwise, followers don't send heartbeats
    }

    @Override
    public CompletableFuture<byte[]> submit(byte[] cmd) {
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Followers cannot submit commands")
        );
    }

    /**
     * Start a leader election by requesting votes from all peers.
     */
    public void startElection() {
        AtomicInteger votes = new AtomicInteger(0);
        int majority = (peers.size() / 2) + 1;

        // Request votes from all peers
        peers.keySet().stream()
            .filter(pid -> !pid.equals(id))
            .forEach(pid -> requestVoteFromPeer(pid, votes, majority));
        
        // Vote for self
        votes.incrementAndGet();
        if (votes.get() >= majority && role == Role.CANDIDATE) {
            transitionTo(Role.LEADER);
        }
    }

    private void requestVoteFromPeer(String peerId, AtomicInteger votes, int majority) {
        executor.submit(() -> {
            try {
                ManagedChannel ch = channelTo(peerId);
                RaftServiceGrpc.RaftServiceBlockingStub stub = RaftServiceGrpc.newBlockingStub(ch);
                
                RequestVoteRequest req = RequestVoteRequest.newBuilder()
                    .setTerm(currentTerm)
                    .setCandidateId(id)
                    .build();
                
                RequestVoteResponse resp = stub.requestVote(req);
                
                if (resp.getVoteGranted()) {
                    int voteCount = votes.incrementAndGet();
                    if (voteCount >= majority && role == Role.CANDIDATE) {
                        transitionTo(Role.LEADER);
                    }
                } else if (resp.getTerm() > currentTerm) {
                    updateTerm(resp.getTerm());
                }
                
                ch.shutdown();
            } catch (Exception e) {
                // Log RPC failure but continue election
            }
        });
    }

    /**
     * Send heartbeats as a leader (used when this FollowerNode becomes leader).
     */
    private void sendLeaderHeartbeats() {
        peers.keySet().stream()
            .filter(pid -> !pid.equals(id))
            .forEach(this::sendHeartbeatToPeer);
    }

    private void sendHeartbeatToPeer(String peerId) {
        executor.submit(() -> {
            try {
                ManagedChannel ch = channelTo(peerId);
                RaftServiceGrpc.RaftServiceBlockingStub stub = RaftServiceGrpc.newBlockingStub(ch);
                
                AppendEntriesRequest req = AppendEntriesRequest.newBuilder()
                    .setTerm(currentTerm)
                    .setLeaderId(id)
                    .setPrevLogIndex(0)
                    .setPrevLogTerm(0)
                    .setLeaderCommit(commitIndex)
                    .build();
                
                AppendEntriesResponse resp = stub.appendEntries(req);
                
                if (resp.getTerm() > currentTerm) {
                    updateTerm(resp.getTerm());
                }
                
                ch.shutdown();
            } catch (Exception e) {
                // Log heartbeat failure but continue
            }
        });
    }
    
    /**
     * Handle AppendEntries RPC from leader.
     * This method is called by RaftServer when it receives an AppendEntries request.
     */
    public synchronized boolean handleAppendEntries(String leaderId, long leaderTerm, 
                                                   long prevLogIndex, long prevLogTerm,
                                                   List<raft.LogEntry> entries, long leaderCommit) {
        // 1. Reply false if term < currentTerm (§5.1)
        if (leaderTerm < currentTerm) {
            return false;
        }
        
        // 2. Reply false if log doesn't contain an entry at prevLogIndex
        //    whose term matches prevLogTerm (§5.3)
        if (prevLogIndex > 0) {
            try {
                raft.LogEntry prevEntry = log.get(prevLogIndex);
                if (prevEntry == null || prevEntry.getTerm() != prevLogTerm) {
                    return false;
                }
            } catch (IOException e) {
                return false;
            }
        }
        
        // 3. If an existing entry conflicts with a new one (same index
        //    but different terms), delete the existing entry and all that
        //    follow it (§5.3)
        if (!entries.isEmpty()) {
            try {
                long firstNewIndex = entries.get(0).getIndex();
                long lastExistingIndex = log.getLastLogIndex();
                
                // Truncate conflicting entries
                if (firstNewIndex <= lastExistingIndex) {
                    log.truncate(firstNewIndex - 1);
                }
                
                // Append new entries
                for (raft.LogEntry entry : entries) {
                    log.append(entry);
                }
            } catch (IOException e) {
                return false;
            }
        }
        
        // 4. Apply entries to state machine if leaderCommit > commitIndex
        if (leaderCommit > commitIndex) {
            long newCommitIndex = Math.min(leaderCommit, log.getLastLogIndex());
            commitIndex = newCommitIndex;
            applyCommittedEntries();
        }
        
        // 5. Reset election timer
        resetElectionTimer();
        
        return true;
    }
}
