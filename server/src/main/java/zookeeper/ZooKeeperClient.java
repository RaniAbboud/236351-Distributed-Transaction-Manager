package zookeeper;

import org.apache.zookeeper.KeeperException;

import java.lang.reflect.Method;

public interface ZooKeeperClient {
    String registerServer(String address) throws InterruptedException, KeeperException;

    void waitForDecision(String barrierId, String initiatorServerId) throws Exception;

    void setDecision(String barrierId, boolean decision) throws InterruptedException, KeeperException;

    void enterBarrier(String barrierId, String[] shards) throws KeeperException, InterruptedException;

    void leaveBarrier(String path, String[] shards) throws InterruptedException, KeeperException;

    String getResponsibleShard(String address) throws InterruptedException, KeeperException;

    String getShardLeader(String shardId) throws InterruptedException, KeeperException;

    void watchLeader(String shardId, Method func);

    int getTimestamp();

    void setupInitialStructures() throws InterruptedException, KeeperException;

    String getServerAddress(String serverId) throws InterruptedException, KeeperException;
}
