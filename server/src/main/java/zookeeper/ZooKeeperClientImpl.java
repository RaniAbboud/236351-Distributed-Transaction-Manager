package zookeeper;

import com.google.common.hash.Hashing;
import constants.Constants;
import javassist.bytecode.stackmap.TypeData;
import org.apache.zookeeper.*;
import org.apache.zookeeper.data.ACL;
import org.apache.zookeeper.data.Stat;

import java.io.*;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiFunction;
import java.util.logging.Level;
import java.util.logging.Logger;

class NodeData implements Serializable {
    private String address;
    private Boolean decision = null;

    public String getAddress() {
        return address;
    }

    public void setAddress(String address) {
        this.address = address;
    }

    public Boolean getDecision() {
        return decision;
    }

    public void setDecision(boolean decision) {
        this.decision = decision;
    }

    public NodeData(boolean decision) { this.decision = decision; }

    public NodeData(String address) {
        this.setAddress(address);
    }

    public static byte[] convertToBytes(NodeData obj) throws IOException {
        try (ByteArrayOutputStream bos = new ByteArrayOutputStream();
             ObjectOutputStream out = new ObjectOutputStream(bos)) {
            out.writeObject(obj);
            return bos.toByteArray();
        }
    }
    public static NodeData convertFromBytes(byte[] bytes) throws IOException, ClassNotFoundException {
        try (ByteArrayInputStream bis = new ByteArrayInputStream(bytes);
             ObjectInputStream in = new ObjectInputStream(bis)) {
            return (NodeData) in.readObject();
        }
    }
}

public class ZooKeeperClientImpl implements ZooKeeperClient, Watcher {
    private static final Logger LOGGER = Logger.getLogger(TypeData.ClassName.class.getName());
    private String zkConnection;
    private ZooKeeper zk;

    private String serverId;
    private String shardId;
    private int numShards;
    private int numServersPerShard;

    private volatile Map<String, String> locks = new ConcurrentHashMap<>();
    final private String shardsPath = "/shards";
    final private String serversPath = "/servers";
    final private String barriersPath = "/barriers";
    final private String counterPath = "/counter";

    public ZooKeeperClientImpl(String zkConnection) {
        this.zkConnection = zkConnection;
    }
    public ZooKeeperClientImpl() {
        this(System.getenv(Constants.ENV_ZK_CONNECTION));
    }


    private Watcher createWatcher(String id, Watcher.Event.EventType eventType) {
        class IdWatcher implements Watcher {
            @Override
            public void process(WatchedEvent event) {
                String mutex = locks.get(id);
                synchronized (mutex) {
                    if (eventType == null || eventType == event.getType()){
                        mutex.notify();
                    }
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

    public List<String> getServersInShard(String shardId) throws InterruptedException, KeeperException {
        final String shardPath = shardsPath + "/" + shardId;
        List<String> serversInShard = zk.getChildren(shardPath, null); // we don't need a watch here so watcher=null
        Collections.sort(serversInShard);
        return serversInShard;
    }

    /**
     * @param serverId: the server's ID including the unique suffix generated by ZooKeeper.
     * @return shardId: the shard's ID
     * @throws InterruptedException
     * @throws KeeperException
     */
    private String getShardForServer(String serverId) throws InterruptedException, KeeperException {
        String serverIndexStr = serverId.replaceFirst("^.*server-", "");
        int serverIndex = Integer.parseInt(serverIndexStr); // The indexes start from 0
        return "shard-"+ serverIndex / this.numShards;
    }

    /**
     * Setup for the Zookeeper
     * Called first when a server first starts.
     * Needs to wait until all servers in the system are up and running before returning
     * Environment variables needed:
     *    - zk_connection : list of ZooKeeper server addresses
     *    - num_shards : Num shards
     *    - num_servers_per_shard : Number of servers in shard - should be odd
     *    - grpc_address : The IP:Port address to be used by the gRPC
     *    - rest_port: The Port of the REST server to be used by Spring
     */
    @Override
    public void setup() throws IOException {
        /** Create Zookeeper client */
        int sessionTimeout = 5000;
        this.zk = new ZooKeeper(zkConnection, sessionTimeout, this);
        /** Parse Env formation */
        this.numShards = Integer.parseInt(System.getenv(Constants.ENV_NUM_SHARDS));
        this.numServersPerShard = Integer.parseInt(System.getenv(Constants.ENV_NUM_SERVERS_PER_SHARD));
        /** Create the Zookeeper Hierarchy */
        try {
            /** Create initial structure */
            this.setupInitialStructures();
            /** Register Myself */
            this.registerServer(System.getenv(Constants.ENV_GRPC_ADDRESS));
            /** Wait for all other Servers to be registered */
            this.waitForAllServersToRegister();
        } catch (InterruptedException e) {
            e.printStackTrace();
        } catch (KeeperException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void setupInitialStructures() throws InterruptedException, KeeperException {
        // Create root path ("/")
        createNodeIfNotExists("/", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        // Create servers path ("/servers")
        createNodeIfNotExists(serversPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        // Create shards path ("/shards")
        createNodeIfNotExists(shardsPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        // Create barriers path ("/barriers")
        createNodeIfNotExists(barriersPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        // Create counter path ("/counter")
        createNodeIfNotExists(counterPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        // Create shard nodes ("/shards/:shardId")
        for (int i = 0; i < this.numShards; i++) {
            createNodeIfNotExists(shardsPath + "/shard-" + i, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT);
        }
    }

    /**
     * Registers the servers to the /servers and to the /shards/:shardId/ structures.
     * This method should be called after these structured where initialised.
     *
     * @param address: the server's address
     * @throws InterruptedException
     * @throws KeeperException
     */
    @Override
    public void registerServer(String address) throws InterruptedException, KeeperException, IOException {
        NodeData serverNodeData = new NodeData(address);
        String serverId = zk.create(serversPath + "/server-", NodeData.convertToBytes(serverNodeData), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        serverId = serverId.replaceFirst("^.*server-", "server-");
        this.serverId = serverId;
        // Register to /shards/:shardId/
        this.shardId = getShardForServer(serverId);
        zk.create(shardsPath + "/" + shardId + "/" + serverId, NodeData.convertToBytes(serverNodeData), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
    }

    /**
     * Wait for all servers in the system to be registered.
     */
    public void waitForAllServersToRegister() throws InterruptedException, KeeperException {
        int numServers = this.numShards * this.numServersPerShard;
        String lockId = "ALL_SERVERS_REGISTER_REGISTERED";
        this.locks.put(lockId, lockId); // create a mutex for this watch
        while (true) {
            synchronized (locks.get(lockId)) {
                List<String> list = zk.getChildren(this.serversPath, createWatcher(lockId, Watcher.Event.EventType.NodeChildrenChanged));
                if (list.size() < numServers) {
                    locks.get(lockId).wait();
                } else {
                    return;
                }
            }
        }
    }

    /**
     * Wait for a decision for the atomic-list operation. "Are we doing it or not?"
     * @param barrierId The barrier ID for the barrier to watch the atomic-transactions-list's decision.
     * @param initiatorServerId The server ID of the server that is responsible for the atomic list request.
     *                         If this server fails, we will exit the wait.
     */
    @Override
    public void waitForDecision(String barrierId, String initiatorServerId) throws Exception {
        String watchId = barrierId + "-decision"; // setting a different watchId than the one we're using for the barrier itself.
        this.locks.put(watchId, watchId); // create a mutex for this watch
        watchServer(initiatorServerId, watchId);
        Stat stat = zk.exists(barriersPath + "/" + barrierId, createWatcher(watchId, Watcher.Event.EventType.NodeDataChanged));
        if (stat == null) {
            throw new Exception("Failed to set watch on barrier node data: node doesn't exist.");
        }
    }

    /**
     * Sets the decision for the given barrier. The "other" servers will be using the waitForDecision method to wait until
     * the decision is set.
     * @param barrierId: The barrier ID for the barrier corresponding to the relevant atomic-transactions-list operation
     * @param decision: The decision (boolean).
     * @throws InterruptedException
     * @throws KeeperException
     */
    @Override
    public void setDecision(String barrierId, boolean decision) throws InterruptedException, KeeperException, IOException {
        zk.setData(barriersPath + "/" + barrierId, NodeData.convertToBytes(new NodeData(decision)), -1);
    }

    @Override
    public void enterBarrier(String barrierId, String[] shards) throws KeeperException, InterruptedException {
        String barrierPath = barriersPath + "/" + barrierId;
        this.locks.put(barrierId, barrierId); // create a mutex for this barrier
        try {
            zk.create(barrierPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); // create the barrier node
        } catch (KeeperException.NodeExistsException e) {
            LOGGER.log(Level.FINEST, "Not creating barrier node. Barrier node already exists. BarrierId=" + barrierId);
            // already exists, ignore error.
        }
        // create node representing the server as a child of the barrier node
        zk.create(barrierPath + "/" + serverId, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL);
        while (true) {
            synchronized (locks.get(barrierId)) {
                List<String> list = zk.getChildren(barrierPath, createWatcher(barrierId, Watcher.Event.EventType.NodeChildrenChanged)); // setting a watcher for this specific barrierId
                // get the number of servers participating in the barrier
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
        try {
            zk.delete(barriersPath + "/" + barrierId + "/" + serverId, -1);
        } catch (KeeperException.NoNodeException e) {
            LOGGER.log(Level.FINEST, "Barrier child node already deleted. BarrierId=" + barrierId + " ServerId=" + serverId);
            // already deleted, ignore error.
        }
        while (true) {
            synchronized (locks.get(barrierId)) {
                List<String> barrierChildren;
                try{
                    barrierChildren = zk.getChildren(barriersPath + "/" + barrierId, createWatcher(barrierId, Watcher.Event.EventType.NodeChildrenChanged));
                } catch (KeeperException.NoNodeException e) {
                    LOGGER.log(Level.INFO, "Barrier node has been deleted. Leaving barrier. BarrierId=" + barrierId);
                    return;
                }
                if (barrierChildren.size() > 0) {
                    locks.get(barrierId).wait();
                } else {
                    try {
                        zk.delete(barriersPath + "/" + barrierId, -1);
                    } catch (KeeperException.NoNodeException e) {
                        LOGGER.log(Level.FINEST, "Barrier node already deleted. BarrierId=" + barrierId);
                        // already deleted, ignore error.
                    }
                    return;
                }
            }
        }
    }

    /**
     * getResponsibleShard uses consistentHash to find the shard responsible for the client's address.
     *
     * @param address: the client's address
     * @return the shardId responsible for the client's address
     * @throws InterruptedException
     * @throws KeeperException
     */
    @Override
    public String getResponsibleShard(String address) throws InterruptedException, KeeperException {
        List<String> shards = getAllShards();
        int bucket = Hashing.consistentHash(address.hashCode(), shards.size());
        return shards.get(bucket);
    }

    /**
     * getShardLeader returns the leader of the shard by choosing the server with the minimal ID under the given shard.
     *
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


    /**
     * watchLeader is used by the atomic-broadcast to get notified when the leader needs to be changed.
     *  @param shardId
     * @param func
     * @return
     */
    @Override
    public String watchLeader(String shardId, BiFunction<String, String, Object> func) {
        class LeaderWatcher implements Watcher {
            @Override
            public void process(WatchedEvent event) {
                // leader may have changed
                try {
                    // set the watcher again (since it's a one-time trigger)
                    List<String> serversInShard = zk.getChildren(shardsPath + "/" + shardId, new LeaderWatcher());
                    String newLeader = Collections.min(serversInShard);
                    LOGGER.log(Level.INFO, String.format("Leader of %s is currently %s", shardId, newLeader));
                    func.apply(shardId, newLeader);
                } catch (InterruptedException | KeeperException e) {
                    LOGGER.log(Level.SEVERE, "Failed to reset watcher on leader for shard " + shardId, e);
                }
            }
        }
        // set the initial watch
        try {
            List<String> serversInShard = zk.getChildren(shardsPath + "/" + shardId, new LeaderWatcher());
            return Collections.min(serversInShard);
        } catch (KeeperException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Failed to set watcher on leader for shard " + shardId, e);
            return null;
        }
    }

//    @Override
//    public boolean atomicCommitWait(String atomicTxnListId, boolean vote, String[] shards) throws InterruptedException, KeeperException {
//        String myShardId = getShardForServer(serverId);
//        String atomicTransactionsPath = "/atomic-transactions";
//        String atomicTxnListPath = atomicTransactionsPath + "/" + atomicTxnListId;
//        // create node representing this atomic transactions list
//        try {
//            zk.create(atomicTxnListPath, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); // create the barrier node
//        } catch (KeeperException.NodeExistsException e) {
//            LOGGER.log(Level.FINEST, "Not creating atomic list node. Atomic list node already exists. Atomic transaction list id =" + atomicTxnListId);
//            // already exists, ignore error.
//        }
//        // create node representing my shard's vote for this atomic list
//        try {
//            NodeData nodeData = new NodeData();
//            zk.create(atomicTxnListPath + "/" + myShardId, SerializationUtils.serialize(nodeData), ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.PERSISTENT); // create the barrier node
//        } catch (KeeperException.NodeExistsException e) {
//            LOGGER.log(Level.FINEST, "Not creating shard node for this atomic list. Shard node already exists. Atomic transaction list id =" + atomicTxnListId);
//            // already exists, ignore error.
//        }
//        /* Enter Barrier */
//        enterBarrier(atomicTxnListId, shards);
//
//        List<String> votingShards = zk.getChildren(atomicTxnListPath, null);
//        List<Boolean> votes = new ArrayList<>();
//        for (String shard : votingShards) {
//            // read shard vote
//            byte[] data = zk.getData(atomicTxnListPath + "/" + shard, false, null);
//            NodeData nodeData = (NodeData) SerializationUtils.deserialize(data);
//            assert nodeData != null;
//            votes.add(nodeData.getDecision());
//        }
//        /* Leave Barrier */
//        leaveBarrier(atomicTxnListId, shards);
//        return votes.stream().allMatch(val -> val);
//    }

    private void watchServer(String serverId, String watchId) throws Exception {
        Stat stat = zk.exists(serversPath + "/" + serverId, createWatcher(watchId, Watcher.Event.EventType.NodeDeleted));
        if (stat == null) {
            throw new Exception("Failed to set watch on server: server is probably dead.");
        }
    }

    /**
     * @return A new unique timestamp as int.
     */
    @Override
    public long getTimestamp() {
        // create sequential node
        long index = -1;
        String indexStr = "";
        try {
            String id = zk.create(counterPath + "/child-", new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
            indexStr = id.replaceFirst("^.*child-", "");
            index = Long.parseLong(indexStr);
        } catch (KeeperException | InterruptedException e) {
            LOGGER.log(Level.SEVERE, "Failed to create counter node", e);
            return -1;
        }

        try {
            zk.delete(counterPath + "/child-" + indexStr, 0);
        } catch (InterruptedException | KeeperException e) {
            LOGGER.log(Level.WARNING, String.format("Failed to delete counter node after generating timestamp. Node path: %s/child-$s",counterPath,indexStr), e);
        }

        return index;
    }

    private void createNodeIfNotExists(String path, byte[] data, List<ACL> acl, CreateMode createMode) throws InterruptedException, KeeperException {
        try {
            zk.create(path, data, acl, createMode);
        } catch (KeeperException.NodeExistsException e) {
            LOGGER.log(Level.FINEST, String.format("Node %s already exists!", path));
        }
    }


    @Override
    public String getServerAddress(String serverId) throws InterruptedException, KeeperException, IOException, ClassNotFoundException {
        byte[] data = zk.getData(serversPath + "/" + serverId, false, null);
        NodeData nodeData = NodeData.convertFromBytes(data);
        assert nodeData != null;
        return nodeData.getAddress();
    }

    @Override
    public void process(WatchedEvent event) {
        LOGGER.log(Level.INFO, String.format("Got the event %s: ", event.toString()));
    }

    public String getServerId() {
        return serverId;
    }

    public void setServerId(String serverId) {
        this.serverId = serverId;
    }

    public String getShardId() {
        return shardId;
    }

    @Override
    public List<String> getServers() throws InterruptedException, KeeperException {
        return zk.getChildren(this.serversPath, false);
    }

    @Override
    public Map<String, List<String>> getShards() throws InterruptedException, KeeperException {
        Map<String, List<String>> shards = new HashMap<>();
        for (String shard : getAllShards()) {
            shards.put(shard, getServersInShard(shard));
        }
        return shards;
    }

    public void setShardId(String shardId) {
        this.shardId = shardId;
    }
}
