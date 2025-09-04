package raft;

import io.grpc.ManagedChannel;
import io.grpc.ManagedChannelBuilder;

import java.io.IOException;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Base class for Raft nodes providing common state management and role transitions.
 * Concrete implementations handle role-specific behavior.
 */
public abstract class AbstractRaftNode implements RaftNode {
    // Node identity and cluster configuration
    protected final String id;
    protected final Map<String, String> peers; // id -> host:port
    
    // Raft state
    protected volatile long currentTerm = 0;
    protected volatile String votedFor = null;
    protected volatile Role role = Role.FOLLOWER;
    
    // Log replication state
    protected volatile long commitIndex = 0;
    protected volatile long lastApplied = 0;
    protected final Map<String, Long> nextIndex = new ConcurrentHashMap<>();
    protected final Map<String, Long> matchIndex = new ConcurrentHashMap<>();
    
    // Persistence and networking
    protected final FileLog log;
    protected final StateMachine stateMachine;
    protected final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();
    protected final ExecutorService executor = Executors.newCachedThreadPool();
    
    // Task management
    protected ScheduledFuture<?> electionTimeoutTask;
    protected ScheduledFuture<?> heartbeatTask;
    
    // Timing configuration
    protected final Duration ELECTION_TIMEOUT;
    protected static final Duration HEARTBEAT_INTERVAL = Duration.ofMillis(50);

    protected AbstractRaftNode(String id, Map<String, String> peers) throws IOException {
        this.id = id;
        this.peers = peers;
        this.log = new FileLog(Paths.get(id + ".log"));
        this.stateMachine = new KeyValueStore();
        
        // Each node gets a different random election timeout to avoid split votes
        this.ELECTION_TIMEOUT = Duration.ofMillis(150 + ThreadLocalRandom.current().nextInt(150));
        
        for (String peerId : peers.keySet()) {
            if (!peerId.equals(id)) {
                nextIndex.put(peerId, 1L);
                matchIndex.put(peerId, 0L);
            }
        }
    }

    @Override
    public String id() {
        return id;
    }

    @Override
    public void start() {
        resetElectionTimer();
    }

    @Override
    public void stop() throws IOException {
        if (electionTimeoutTask != null) electionTimeoutTask.cancel(true);
        if (heartbeatTask != null) heartbeatTask.cancel(true);
        scheduler.shutdownNow();
        executor.shutdownNow();
        log.close();
    }

    // ---- Timer management ----
    
    protected void resetElectionTimer() {
        if (electionTimeoutTask != null) electionTimeoutTask.cancel(false);
        electionTimeoutTask = scheduler.schedule(
            this::onElectionTimeout, 
            ELECTION_TIMEOUT.toMillis(), 
            TimeUnit.MILLISECONDS
        );
    }

    protected void startHeartbeat() {
        if (heartbeatTask != null) heartbeatTask.cancel(false);
        heartbeatTask = scheduler.scheduleAtFixedRate(
            this::sendHeartbeats, 
            0, 
            HEARTBEAT_INTERVAL.toMillis(), 
            TimeUnit.MILLISECONDS
        );
    }

    // ---- Networking ----
    
    protected ManagedChannel channelTo(String peerId) {
        String target = peers.get(peerId);
        return ManagedChannelBuilder.forTarget(target).usePlaintext().build();
    }

    // ---- Term and role management ----
    
    protected synchronized void updateTerm(long newTerm) {
        if (newTerm > this.currentTerm) {
            this.currentTerm = newTerm;
            this.votedFor = null;
            if (role != Role.FOLLOWER) {
                transitionTo(Role.FOLLOWER);
            }
        }
    }

    protected synchronized void transitionTo(Role newRole) {
        if (this.role == newRole) return;
        
        this.role = newRole;
        
        // Cancel old role-specific tasks
        if (heartbeatTask != null) heartbeatTask.cancel(false);

        switch (newRole) {
            case FOLLOWER -> resetElectionTimer();
            case CANDIDATE -> {
                resetElectionTimer();
                // Candidate-specific logic will start election outside.
            }
            case LEADER -> {
                // Cancel election timer when becoming leader
                if (electionTimeoutTask != null) electionTimeoutTask.cancel(false);
                startHeartbeat();
                // Send heartbeats immediately to establish authority
                sendHeartbeats();
                // Initialize leader state
                initializeLeaderState();
            }
        }
    }

    // ---- Vote management ----
    
    protected synchronized boolean grantVote(String candidateId, long candidateTerm) {
        if (candidateTerm < currentTerm) {
            return false;
        }
        
        if (candidateTerm > currentTerm) {
            updateTerm(candidateTerm);
        }
        
        // Grant vote if we haven't voted in this term, OR if we're voting for the same candidate
        if (votedFor == null || votedFor.equals(candidateId)) {
            votedFor = candidateId;
            resetElectionTimer();
            return true;
        }
        
        return false;
    }

    // ---- Heartbeat handling ----
    
    protected synchronized boolean handleHeartbeat(String leaderId, long leaderTerm) {
        if (leaderTerm < currentTerm) {
            return false;
        }
        
        if (leaderTerm > currentTerm) {
            updateTerm(leaderTerm);
        }
        
        // Reset election timer on valid heartbeat
        resetElectionTimer();
        return true;
    }

    // ---- Log replication ----
    
    protected synchronized void initializeLeaderState() {
        long lastLogIndex = log.getLastLogIndex();
        for (String peerId : peers.keySet()) {
            if (!peerId.equals(id)) {
                nextIndex.put(peerId, lastLogIndex + 1);
                matchIndex.put(peerId, 0L);
            }
        }
    }
    
    protected synchronized void updateCommitIndex() {
        // Find the highest index that has been replicated to a majority
        long[] indices = matchIndex.values().stream()
            .mapToLong(Long::longValue)
            .sorted()
            .toArray();
        
        if (indices.length > 0) {
            int majorityIndex = indices.length / 2;
            long newCommitIndex = indices[majorityIndex];
            
            if (newCommitIndex > commitIndex) {
                commitIndex = newCommitIndex;
                // Apply committed entries to state machine
                applyCommittedEntries();
            }
        }
    }
    
    protected synchronized void applyCommittedEntries() {
        while (lastApplied < commitIndex) {
            lastApplied++;
            try {
                raft.LogEntry entry = log.get(lastApplied);
                if (entry != null && entry.getCommandType() == raft.CommandType.CLIENT_COMMAND) {
                    byte[] result = stateMachine.apply(entry.getPayload().toByteArray());
                    // TODO: Store result for client response
                }
            } catch (IOException e) {
                // Log error but continue
                System.err.println("Error applying log entry " + lastApplied + ": " + e.getMessage());
            }
        }
    }
    
    protected synchronized long getNextLogIndex() {
        return log.getLastLogIndex() + 1;
    }
    
    protected synchronized long getLastLogTerm() {
        try {
            long lastIndex = log.getLastLogIndex();
            if (lastIndex > 0) {
                raft.LogEntry entry = log.get(lastIndex);
                return entry != null ? entry.getTerm() : 0;
            }
        } catch (IOException e) {
            // Log error
        }
        return 0;
    }

    // ---- Abstract methods to be implemented by concrete roles ----
    
    protected abstract void onElectionTimeout();
    protected abstract void sendHeartbeats();
}
