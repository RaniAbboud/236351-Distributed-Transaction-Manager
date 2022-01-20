package zookeeper;

import org.apache.zookeeper.KeeperException;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

public interface ZooKeeperClient {

    void setup() throws IOException;
    void setupInitialStructures() throws InterruptedException, KeeperException;
    void registerServer(String address) throws InterruptedException, KeeperException, IOException;

    String getServerId();
    String getShardId();
    List<String> getServers() throws InterruptedException, KeeperException;
    List<String> getServersInShard(String shardId) throws InterruptedException, KeeperException;
    Map<String, List<String>> getShards() throws InterruptedException, KeeperException;
    String getServerAddress(String serverId) throws InterruptedException, KeeperException, IOException, ClassNotFoundException;

    String getShardLeader(String shardId) throws InterruptedException, KeeperException;
    String watchLeader(String shardId, BiFunction<String, String, Object> func);

    String getResponsibleShard(String address) throws InterruptedException, KeeperException;

    boolean atomicCommitWait(String atomicTxnListId, String initiatorServer, boolean vote, List<String> votingShards) throws Exception;

    long getTimestamp();

    void enterBarrier(String barrierId, List<String> shards) throws KeeperException, InterruptedException, IOException;
    void setDecision(String barrierId, boolean decision) throws InterruptedException, KeeperException, IOException;
    boolean waitForDecision(String barrierId, String initiatorServerId) throws Exception;
    void leaveBarrier(String path, List<String> shards) throws InterruptedException, KeeperException;

    boolean isResponsibleForAddress(String address);
}
