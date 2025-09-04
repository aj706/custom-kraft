package raft;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicLong;
import io.grpc.ManagedChannel;
import raft.RaftServiceGrpc;
import raft.AppendEntriesRequest;
import raft.AppendEntriesResponse;
import com.google.protobuf.ByteString;

/**
 * Implements the Leader role in the Raft consensus algorithm.
 * Leaders handle all client requests and replicate log entries to followers.
 */
public class LeaderNode extends AbstractRaftNode {
    
    private final AtomicLong clientRequestCounter = new AtomicLong(0);
    
    public LeaderNode(String id, Map<String, String> peers) throws IOException {
        super(id, peers);
        this.role = Role.LEADER;
        startHeartbeat();
    }

    @Override
    protected void onElectionTimeout() {
        // Leaders shouldn't hit election timeout
        throw new IllegalStateException("Leader should not have election timeout");
    }

    @Override
    protected void sendHeartbeats() {
        // Send empty AppendEntries RPCs to all peers as heartbeats
        peers.keySet().stream()
            .filter(pid -> !pid.equals(id))
            .forEach(this::sendHeartbeatToPeer);
    }

    @Override
    public CompletableFuture<byte[]> submit(byte[] cmd) {
        CompletableFuture<byte[]> future = new CompletableFuture<>();
        
        executor.submit(() -> {
            try {
                // Create log entry
                long logIndex = getNextLogIndex();
                raft.LogEntry entry = raft.LogEntry.newBuilder()
                    .setTerm(currentTerm)
                    .setIndex(logIndex)
                    .setPayload(ByteString.copyFrom(cmd))
                    .setCommandType(raft.CommandType.CLIENT_COMMAND)
                    .build();
                
                // Append to local log
                log.append(entry);
                
                // Replicate to followers
                replicateToFollowers(entry, future);
                
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
        
        return future;
    }

    /**
     * Send a heartbeat to a specific peer.
     */
    private void sendHeartbeatToPeer(String peerId) {
        executor.submit(() -> {
            try {
                ManagedChannel ch = channelTo(peerId);
                RaftServiceGrpc.RaftServiceBlockingStub stub = RaftServiceGrpc.newBlockingStub(ch);
                
                AppendEntriesRequest req = AppendEntriesRequest.newBuilder()
                    .setTerm(currentTerm)
                    .setLeaderId(id)
                    .setPrevLogIndex(nextIndex.get(peerId) - 1)
                    .setPrevLogTerm(getLastLogTerm())
                    .setLeaderCommit(commitIndex)
                    .build();
                
                AppendEntriesResponse resp = stub.appendEntries(req);
                
                if (resp.getTerm() > currentTerm) {
                    updateTerm(resp.getTerm());
                } else if (resp.getSuccess()) {
                    // Update replication state
                    updateReplicationState(peerId, resp.getLastLogIndex());
                } else {
                    // Decrement nextIndex and retry
                    decrementNextIndex(peerId);
                }
                
                ch.shutdown();
            } catch (Exception e) {
                // Log heartbeat failure but continue
            }
        });
    }
    
    /**
     * Replicate a log entry to all followers.
     */
    private void replicateToFollowers(raft.LogEntry entry, CompletableFuture<byte[]> future) {
        // Send AppendEntries RPCs to all followers
        peers.keySet().stream()
            .filter(pid -> !pid.equals(id))
            .forEach(peerId -> replicateToPeer(peerId, entry, future));
    }
    
    /**
     * Replicate a log entry to a specific peer.
     */
    private void replicateToPeer(String peerId, raft.LogEntry entry, CompletableFuture<byte[]> future) {
        executor.submit(() -> {
            try {
                ManagedChannel ch = channelTo(peerId);
                RaftServiceGrpc.RaftServiceBlockingStub stub = RaftServiceGrpc.newBlockingStub(ch);
                
                AppendEntriesRequest req = AppendEntriesRequest.newBuilder()
                    .setTerm(currentTerm)
                    .setLeaderId(id)
                    .setPrevLogIndex(entry.getIndex() - 1)
                    .setPrevLogTerm(getLastLogTerm())
                    .addEntries(entry)
                    .setLeaderCommit(commitIndex)
                    .build();
                
                AppendEntriesResponse resp = stub.appendEntries(req);
                
                if (resp.getTerm() > currentTerm) {
                    updateTerm(resp.getTerm());
                    future.completeExceptionally(new RuntimeException("Leader stepped down"));
                } else if (resp.getSuccess()) {
                    // Update replication state
                    updateReplicationState(peerId, resp.getLastLogIndex());
                    
                    // Check if we can commit this entry
                    checkCommitability(entry.getIndex(), future);
                } else {
                    // Decrement nextIndex and retry
                    decrementNextIndex(peerId);
                    // TODO: Implement retry logic
                }
                
                ch.shutdown();
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        });
    }
    
    /**
     * Update replication state after successful AppendEntries response.
     */
    private synchronized void updateReplicationState(String peerId, long lastLogIndex) {
        nextIndex.put(peerId, lastLogIndex + 1);
        matchIndex.put(peerId, lastLogIndex);
        
        // Update commit index
        updateCommitIndex();
    }
    
    /**
     * Decrement nextIndex for a peer after failed AppendEntries.
     */
    private synchronized void decrementNextIndex(String peerId) {
        Long current = nextIndex.get(peerId);
        if (current != null && current > 1) {
            nextIndex.put(peerId, current - 1);
        }
    }
    
    /**
     * Check if an entry can be committed and complete the client request.
     */
    private synchronized void checkCommitability(long entryIndex, CompletableFuture<byte[]> future) {
        // Count how many nodes have replicated this entry
        long replicatedCount = matchIndex.values().stream()
            .filter(index -> index >= entryIndex)
            .count() + 1; // +1 for leader
        
        if (replicatedCount >= (peers.size() / 2) + 1) {
            // Entry is committed, complete the future
            try {
                // Wait a bit for the entry to be applied to state machine
                Thread.sleep(10);
                future.complete("OK: Command committed".getBytes());
            } catch (Exception e) {
                future.completeExceptionally(e);
            }
        }
    }
}
