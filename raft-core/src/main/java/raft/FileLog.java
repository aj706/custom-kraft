package raft;

import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.List;
import com.google.protobuf.ByteString;

/**
 * Simple file-based persistent log for Raft entries.
 * Uses Java NIO for efficient file I/O operations.
 */
public class FileLog implements AutoCloseable {
    private final Path logFile;
    private final FileChannel channel;
    private long lastLogIndex = 0;

    public FileLog(Path logFile) throws IOException {
        this.logFile = logFile;
        
        // Create log file if it doesn't exist
        if (!Files.exists(logFile)) {
            Files.createFile(logFile);
        }
        
        this.channel = FileChannel.open(logFile, 
            StandardOpenOption.READ, 
            StandardOpenOption.WRITE, 
            StandardOpenOption.CREATE
        );
        
        // Calculate last log index from file size
        this.lastLogIndex = channel.size() / 8; // Assuming 8 bytes per entry
    }

    /**
     * Append a log entry to the end of the log.
     * 
     * @param entry the log entry to append
     * @return the index where the entry was appended
     * @throws IOException if I/O operation fails
     */
    public synchronized long append(raft.LogEntry entry) throws IOException {
        long index = lastLogIndex + 1;
        
        // Write entry to file
        ByteBuffer buffer = ByteBuffer.allocate(8); // Simple 8-byte entries for demo
        buffer.putLong(index);
        buffer.flip();
        
        channel.position(channel.size());
        channel.write(buffer);
        channel.force(true); // Ensure data is written to disk
        
        lastLogIndex = index;
        return index;
    }

    /**
     * Get a log entry by index.
     * 
     * @param index the index of the entry to retrieve
     * @return the log entry, or null if not found
     * @throws IOException if I/O operation fails
     */
    public synchronized raft.LogEntry get(long index) throws IOException {
        if (index <= 0 || index > lastLogIndex) {
            return null;
        }
        
        // Calculate file position for this entry
        long position = (index - 1) * 8;
        if (position >= channel.size()) {
            return null;
        }
        
        // Read entry from file
        ByteBuffer buffer = ByteBuffer.allocate(8);
        channel.position(position);
        channel.read(buffer);
        buffer.flip();
        
        long entryIndex = buffer.getLong();
        return raft.LogEntry.newBuilder()
            .setIndex(entryIndex)
            .setTerm(0) // TODO: Store actual term
            .setPayload(ByteString.copyFrom(new byte[0])) // TODO: Store actual payload
            .build();
    }

    /**
     * Get all log entries from a given index onwards.
     * 
     * @param fromIndex the starting index (inclusive)
     * @return list of log entries
     * @throws IOException if I/O operation fails
     */
    public synchronized List<raft.LogEntry> getFrom(long fromIndex) throws IOException {
        List<raft.LogEntry> entries = new ArrayList<>();
        
        for (long i = fromIndex; i <= lastLogIndex; i++) {
            raft.LogEntry entry = get(i);
            if (entry != null) {
                entries.add(entry);
            }
        }
        
        return entries;
    }

    /**
     * Get the last log index.
     * 
     * @return the index of the last entry in the log
     */
    public synchronized long getLastLogIndex() {
        return lastLogIndex;
    }

    /**
     * Truncate the log to a specific index.
     * 
     * @param index the index to truncate to
     * @throws IOException if I/O operation fails
     */
    public synchronized void truncate(long index) throws IOException {
        if (index < 0 || index > lastLogIndex) {
            throw new IllegalArgumentException("Invalid truncate index: " + index);
        }
        
        long newSize = index * 8;
        channel.truncate(newSize);
        lastLogIndex = index;
    }

    @Override
    public synchronized void close() throws IOException {
        if (channel != null) {
            channel.close();
        }
    }
}
