package zookeeper;

import org.apache.zookeeper.CreateMode;
import org.apache.zookeeper.KeeperException;
import org.apache.zookeeper.ZooDefs;
import org.apache.zookeeper.ZooKeeper;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.List;

class ServerNodeData {
    String address;
    String id;

    public ServerNodeData(String address, String id) {
        this.address = address;
        this.id = id;
    }
}

public class ZooKeeperClientImpl implements ZooKeeperClient {
    private ZooKeeper zk;
    private String serverId;
    final private int sessionTimeout = 5000;
    final private String shardsPath = "/shards";
    final private String serversPath = "/servers";

    /**
     * Constructor.
     * @param zkConnection: comma-separated list of host:port pairs, each corresponding to a ZooKeeper server.
     * @param serverId: current server's ID
     * @throws IOException
     */
    public ZooKeeperClientImpl(String zkConnection, String serverId) throws IOException {
        this.serverId = serverId;
        this.zk = new ZooKeeper(zkConnection, sessionTimeout, null); // todo: watcher?
    }

    private String[] getServers(String shardId) throws InterruptedException, KeeperException {
        final String shardPath = shardsPath+"/"+shardId;
        List<String> list = zk.getChildren(shardPath, null); // we don't need a watch here so watcher=null
        return list.toArray(new String[0]);
    };

    @Override
    public void registerServer(String serverId, String address) throws InterruptedException, KeeperException {
        zk.create(serversPath+"/"+serverId, new byte[ServerNodeData(address, serverId)], ZooDefs.Ids.OPEN_ACL_UNSAFE, CreateMode.EPHEMERAL_SEQUENTIAL);
        // todo: when will we register to /shards?
    }

    @Override
    public void enterBarrier(String path, String[] shards) throws KeeperException, InterruptedException {
        zk.create(path + "/" + serverId, new byte[0], ZooDefs.Ids.OPEN_ACL_UNSAFE,
                CreateMode.EPHEMERAL_SEQUENTIAL);
        while (true) {
            synchronized (mutex) {
                List<String> list = zk.getChildren(path, true);

                if (list.size() < size) {
                    mutex.wait();
                } else {
                    return;
                }
            }
        }
    }

    @Override
    public void leaveBarrier(String path, String[] shards) {

    }

    @Override
    public String getShard(String address) {
        return null;
    }

    @Override
    public String getLeader(String shardId) {
        return null;
    }

    @Override
    public boolean atomicCommitWait(String path, boolean myVote, String[] shards) {
        return false;
    }

    @Override
    public void watchLeader(String shardId, Method func) {

    }
}
