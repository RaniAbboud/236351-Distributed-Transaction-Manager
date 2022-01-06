package zookeeper;

import com.google.common.hash.Hashing;
import constants.Constants;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.Stat;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;

//class ServerNodeData {
//    String address;
//    String id;
//
//    public ServerNodeData(String address, String id) {
//        this.address = address;
//        this.id = id;
//    }
//}

public class ZooKeeperClientImpl implements ZooKeeperClient {
    private ZooKeeper zk;
    private String serverId;
    private HashMap<String, Integer> locks = new HashMap<>();
    final private int sessionTimeout = 5000;
    final private String shardsPath = "/shards";
    final private String serversPath = "/servers";
    final private String barriersPath = "/barriers";

    /**
     * Constructor.
     *
     * @param zkConnection: comma-separated list of host:port pairs, each corresponding to a ZooKeeper server.
     * @param serverId:     current server's ID
     * @throws IOException
     */
    public ZooKeeperClientImpl(String zkConnection, String serverId) throws IOException {
        this.serverId = serverId;
        this.zk = new ZooKeeper(zkConnection, sessionTimeout, null); // todo: watcher?
    }

    private Watcher createWatcher(String id) {
        class IdWatcher implements Watcher {
            @Override
            public void process(WatchedEvent event) {
                Integer mutex = locks.get(id);
                synchronized (mutex) {
                    mutex.notify();
                }
            }
        }
        return new IdWatcher();
    }

    /**
     * @return Returns all shard ids sorted
     * @throws InterruptedException
     * @throws KeeperException
     */
    public List<String> getAllShards() throws InterruptedException, KeeperException {
        List<String> shards = zk.getChildren(shardsPath, null); // we don't need a watch here so watcher=null
        Collections.sort(shards);
        return shards;
    }

    private static int getNumberOfShards() {
        return Integer.parseInt(System.getenv(Constants.ENV_NUM_SHARDS));
    }

    private List<String> getServersInShard(String shardId) throws InterruptedException, KeeperException {
        final String shardPath = shardsPath + "/" + shardId;
        List<String> list = zk.getChildren(shardPath, null); // we don't need a watch here so watcher=null
        Collections.sort(list);
        return list;
    }

    /**
     * Registers the servers to the /servers and to the /shards/:shardId/ structures.
     * This method should be called after these structured where initialised.
     *
     * @param address: the server's address
     * @return the serverId
     * @throws InterruptedException
     * @throws KeeperException
     */
    @Override
    public String registerServer(String address) throws InterruptedException, KeeperException {
        byte[] serverNodeData = address.getBytes();
        String serverId = zk.create(serversPath + "/server", serverNodeData, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        // Register to /shards/:shardId/
        String serverIndexStr = toString().replaceFirst("^.*server", "");
        int serverIndex = Integer.parseInt(serverIndexStr) - 1; // I think the indexes start from 1 and not 0. That's why I did -1.
        String shardId = getAllShards().get(serverIndex / getNumberOfShards());
        zk.create(shardsPath + "/" + shardId + "/server", serverNodeData, ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        return serverId;
    }

    @Override
    public void enterBarrier(String barrierId, String[] shards) throws KeeperException, InterruptedException {
        Stat s = zk.exists(barriersPath + "/" + barrierId, false);
        if (s == null) {
            this.locks.put(barrierId, 1); // create a mutex for this barrier
            zk.create(barrierId, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); // create the barrier node
        }
        // create node representing the server as a child of the barrier node
        zk.create(barriersPath + "/" + barrierId + "/" + serverId, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        while (true) {
            synchronized (locks.get(barrierId)) {
                List<String> list = zk.getChildren(barrierId, createWatcher(barrierId)); // setting a watcher for this specific barrierId
                // get the number of server participating in the barrier
                int serversCount = 0;
                for (String shard : shards) {
                    serversCount += getServersInShard(shard).size();
                }
                if (list.size() < serversCount) {
                    locks.get(barrierId).wait();
                } else {
                    return;
                }
            }
        }
    }

    @Override
    public void leaveBarrier(String barrierId, String[] shards) throws InterruptedException, KeeperException {
        zk.delete(barriersPath + "/" + barrierId + "/" + serverId, 0);
        while (true) {
            synchronized (locks.get(barrierId)) {
                List<String> list = zk.getChildren(barriersPath + "/" + barrierId, createWatcher(barrierId));
                if (list.size() > 0) {
                    locks.get(barrierId).wait();
                } else {
                    return;
                }
            }
        }
    }

    /**
     * getResponsibleShard uses consistentHash to find the shard responsible for the client's address.
     * @param address: the client's address
     * @return the shardId responsible for the client's address
     * @throws InterruptedException
     * @throws KeeperException
     */
    @Override
    public String getResponsibleShard(String address) throws InterruptedException, KeeperException {
        List<String> shards = getAllShards();
        int bucket = Hashing.consistentHash(address.hashCode(), shards.size());
        shards.get(bucket);
        return null;
    }

    /**
     * getShardLeader returns the leader of the shard by choosing the server with the minimal ID under the given shard.
     * @param shardId
     * @return
     * @throws InterruptedException
     * @throws KeeperException
     */
    @Override
    public String getShardLeader(String shardId) throws InterruptedException, KeeperException {
        List<String> shardServers = getServersInShard(shardId);
        return Collections.min(shardServers);
    }

    @Override
    public boolean atomicCommitWait(String path, boolean myVote, String[] shards) {
        return false;
    }

    @Override
    public void watchLeader(String shardId, Method func) {

    }

    @Override
    public int getTimestamp() {
        return 0;
    }

    @Override
    public void setupInitialStructures() throws InterruptedException, KeeperException {
        // Create root path ("/")
        final String rootPath = "/";
        Stat stat = zk.exists(rootPath, false);
        if (stat == null) {
            zk.create(rootPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        // Create servers path ("/servers")
        stat = zk.exists(serversPath, false);
        if (stat == null) {
            zk.create(serversPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        // Create shards path ("/shards")
        stat = zk.exists(shardsPath, false);
        if (stat == null) {
            zk.create(shardsPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
        // Create shard nodes ("/shards/:shardId")
        for (int i=0; i<getNumberOfShards(); i++){
            zk.create(shardsPath + "/shard", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT_SEQUENTIAL);
        }
    }
}
