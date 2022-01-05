package zookeeper;

import org.apache.zookeeper.KeeperException;

import java.lang.reflect.Method;

public interface ZooKeeperClient {
    void registerServer(String serverId, String address) throws InterruptedException, KeeperException;
    void enterBarrier(String path, String[] shards) throws KeeperException, InterruptedException;
    void leaveBarrier(String path, String[] shards);
    String getShard(String address);
    String getLeader(String shardId);
    boolean atomicCommitWait(String path, boolean myVote, String[] shards);
    void watchLeader(String shardId, Method func);
}
