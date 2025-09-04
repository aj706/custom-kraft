package raft;

import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.stub.StreamObserver;
import raft.RaftServiceGrpc;
import raft.AppendEntriesRequest;
import raft.AppendEntriesResponse;
import raft.RequestVoteRequest;
import raft.RequestVoteResponse;

/**
 * gRPC server implementation for Raft RPCs.
 * Handles RequestVote and AppendEntries RPCs from other nodes.
 */
public class RaftServer {
    private final Server server;
    private final RaftNode node;

    public RaftServer(int port, RaftNode node) throws Exception {
        this.node = node;
        this.server = ServerBuilder.forPort(port)
            .addService(new RaftServiceImpl(node))
            .build();
    }

    public void start() throws Exception {
        server.start();
        System.out.printf("RaftServer %s started on port %d%n", node.id(), server.getPort());
    }

    public void stop() {
        if (server != null) {
            server.shutdown();
        }
    }

    public void blockUntilShutdown() throws InterruptedException {
        if (server != null) {
            server.awaitTermination();
        }
    }

    /**
     * Implementation of the RaftService gRPC interface.
     */
    private static class RaftServiceImpl extends RaftServiceGrpc.RaftServiceImplBase {
        private final RaftNode node;

        RaftServiceImpl(RaftNode node) {
            this.node = node;
        }

        @Override
        public void requestVote(RequestVoteRequest request, StreamObserver<RequestVoteResponse> responseObserver) {
            boolean voteGranted = ((AbstractRaftNode) node).grantVote(
                request.getCandidateId(), 
                request.getTerm()
            );
            
            RequestVoteResponse response = RequestVoteResponse.newBuilder()
                .setTerm(((AbstractRaftNode) node).currentTerm)
                .setVoteGranted(voteGranted)
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }

        @Override
        public void appendEntries(AppendEntriesRequest request, StreamObserver<AppendEntriesResponse> responseObserver) {
            boolean success;
            
            if (request.getEntriesCount() == 0) {
                // This is a heartbeat
                success = ((AbstractRaftNode) node).handleHeartbeat(
                    request.getLeaderId(), 
                    request.getTerm()
                );
            } else {
                // This is a log replication request
                if (node instanceof FollowerNode) {
                    success = ((FollowerNode) node).handleAppendEntries(
                        request.getLeaderId(),
                        request.getTerm(),
                        request.getPrevLogIndex(),
                        request.getPrevLogTerm(),
                        request.getEntriesList(),
                        request.getLeaderCommit()
                    );
                } else {
                    // Non-follower nodes shouldn't receive AppendEntries
                    success = false;
                }
            }
            
            AppendEntriesResponse response = AppendEntriesResponse.newBuilder()
                .setTerm(((AbstractRaftNode) node).currentTerm)
                .setSuccess(success)
                .setLastLogIndex(((AbstractRaftNode) node).log.getLastLogIndex())
                .build();
            
            responseObserver.onNext(response);
            responseObserver.onCompleted();
        }
    }
}
