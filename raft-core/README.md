# Raft Consensus Implementation

A clean, educational implementation of the Raft consensus algorithm in Java.

## Overview

This project implements the core Raft consensus algorithm as described in the [Raft paper](https://raft.github.io/raft.pdf). It provides a working implementation of leader election, log replication, and basic consensus mechanisms.

## Architecture

### Core Components

- **`RaftNode`**: Interface defining the core operations of a Raft node
- **`AbstractRaftNode`**: Base class providing common state management and role transitions
- **`FollowerNode`**: Implements the Follower role (passive state, responds to requests)
- **`CandidateNode`**: Implements the Candidate role (active during leader elections)
- **`LeaderNode`**: Implements the Leader role (handles client requests, replicates logs)
- **`RaftServer`**: gRPC server for handling RPCs between nodes
- **`FileLog`**: Simple persistent log implementation using Java NIO
- **`StateMachine`**: Interface for applying committed log entries
- **`KeyValueStore`**: Example state machine implementation (key-value store)

### Key Features

- **Leader Election**: Implements the Raft leader election protocol with randomized timeouts
- **Heartbeat Mechanism**: Leaders send periodic heartbeats to maintain authority
- **Log Replication**: Full log replication with consistency guarantees
- **State Machine**: Committed entries are applied to a state machine
- **Role Transitions**: Clean state transitions between Follower, Candidate, and Leader roles
- **gRPC Communication**: Nodes communicate via gRPC using Protocol Buffers
- **Persistence**: Basic log persistence using file-based storage
- **Client Interface**: Submit commands and get responses from the cluster

## Getting Started

### Prerequisites

- Java 17 or higher
- Maven 3.6 or higher

### Building

```bash
cd custom-kraft/raft-core
mvn clean package
```

### Running the Demo

```bash
# Start the 3-node cluster
mvn exec:java -Dexec.mainClass=raft.DemoCluster

# In another terminal, test the state machine
mvn exec:java -Dexec.mainClass=raft.StateMachineTest
```

This will start a 3-node Raft cluster locally:
- **n1**: localhost:5001
- **n2**: localhost:5002  
- **n3**: localhost:5003

## How It Works

### 1. Initialization
- All nodes start as Followers
- Each node has a randomized election timeout (150-300ms)

### 2. Leader Election
- When a Follower times out, it becomes a Candidate
- Candidate increments term and requests votes from all peers
- Candidate votes for itself and waits for responses
- If majority of votes received, Candidate becomes Leader

### 3. Leadership Maintenance
- Leader sends periodic heartbeats (every 50ms) to all Followers
- Followers reset their election timers on valid heartbeats
- If Leader fails, Followers will timeout and start new elections

### 4. Log Replication
- Client submits command to Leader
- Leader creates log entry and appends to local log
- Leader sends AppendEntries RPCs to all Followers
- Followers validate and append entries to their logs
- When majority acknowledges, Leader commits entry
- Committed entries are applied to state machine on all nodes

### 5. State Machine
- Committed log entries are applied to the state machine
- Example implementation: KeyValueStore with PUT/GET/DELETE operations
- All nodes execute commands in the same order
- State machine provides consistency across the cluster

### 6. State Transitions
- **Follower → Candidate**: On election timeout
- **Candidate → Leader**: On receiving majority votes
- **Candidate → Follower**: On discovering higher term
- **Leader → Follower**: On discovering higher term

## Code Structure

```
src/main/java/raft/
├── RaftNode.java              # Core interface
├── AbstractRaftNode.java      # Base implementation
├── FollowerNode.java          # Follower role
├── CandidateNode.java         # Candidate role  
├── LeaderNode.java            # Leader role
├── RaftServer.java            # gRPC server
├── FileLog.java               # Persistent log
├── StateMachine.java          # State machine interface
├── KeyValueStore.java         # Example state machine
├── Role.java                  # Role enumeration
├── DemoCluster.java           # Demo application
├── StateMachineTest.java      # State machine tests
└── ClientDemo.java            # Client demo
```

## Configuration

### Timing Parameters

- **Election Timeout**: 150-300ms (randomized per node)
- **Heartbeat Interval**: 50ms
- **gRPC Ports**: 5001, 5002, 5003

### Network Configuration

- **Transport**: gRPC with Protocol Buffers
- **Security**: Plaintext (no TLS for demo)
- **Addressing**: localhost with configurable ports

## State Machine Commands

The example KeyValueStore supports these commands:

### Write Operations (require consensus)
- `PUT|key|value` - Set a key-value pair
- `DELETE|key` - Remove a key-value pair

### Read Operations (local queries)
- `GET|key` - Retrieve a value
- `SIZE` - Get number of key-value pairs
- `KEYS` - Get list of all keys

## Testing

### State Machine Test
```bash
mvn exec:java -Dexec.mainClass=raft.StateMachineTest
```

This tests:
- Direct state machine operations
- Simulated Raft log replication
- Key-value store functionality

### Cluster Demo
```bash
mvn exec:java -Dexec.mainClass=raft.DemoCluster
```

This demonstrates:
- Leader election
- Heartbeat mechanism
- Log replication
- State machine execution

## Extending the Implementation

### Adding New Commands
1. Extend the `StateMachine.apply()` method
2. Add command parsing logic
3. Implement the operation
4. Test with the cluster

### Adding New State Machines
1. Implement the `StateMachine` interface
2. Define command format
3. Integrate with log replication
4. Test end-to-end functionality

## Limitations

This is an educational implementation with some limitations:
- Basic persistence (file-based, no compaction)
- No snapshot mechanism
- No membership changes
- No client request deduplication
- Basic error handling

## Contributing

This is a learning project. Feel free to:
- Add missing Raft features
- Improve error handling
- Add comprehensive tests
- Optimize performance
- Add monitoring and metrics

## License

This project is for educational purposes. The Raft algorithm is described in the academic paper "In Search of an Understandable Consensus Algorithm" by Diego Ongaro and John Ousterhout.
