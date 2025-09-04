package raft;

import java.io.IOException;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import io.grpc.ManagedChannel;
import raft.RaftServiceGrpc;
import raft.RequestVoteRequest;
import raft.RequestVoteResponse;

/**
 * Implements the Candidate role in the Raft consensus algorithm.
 * Candidates request votes from other nodes during leader elections.
 */
public class CandidateNode extends AbstractRaftNode {
    
    public CandidateNode(String id, Map<String, String> peers) throws IOException {
        super(id, peers);
        this.role = Role.CANDIDATE;
        // Start election immediately when becoming candidate
        startElection();
    }

    @Override
    protected void onElectionTimeout() {
        // Restart election if we haven't won yet
        if (role == Role.CANDIDATE) {
            startElection();
        }
    }

    @Override
    protected void sendHeartbeats() {
        // Candidates don't send heartbeats
    }

    @Override
    public CompletableFuture<byte[]> submit(byte[] cmd) {
        return CompletableFuture.failedFuture(
            new UnsupportedOperationException("Candidates cannot submit commands")
        );
    }

    /**
     * Start a leader election by requesting votes from all peers.
     */
    public void startElection() {
        // Increment term and vote for self
        currentTerm++;
        votedFor = id;
        
        AtomicInteger votes = new AtomicInteger(1); // Start with self-vote
        int majority = (peers.size() / 2) + 1;

        // Request votes from all peers
        peers.keySet().stream()
            .filter(pid -> !pid.equals(id))
            .forEach(pid -> requestVoteFromPeer(pid, votes, majority));
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
}
