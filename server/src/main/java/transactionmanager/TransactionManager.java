package transactionmanager;

import atomicbroadcast.AtomicBroadcast;
import grpcservice.RPCService;
import grpcservice.RequestHandler;
import grpcservice.RequestHandlerUtils;
import io.grpc.Server;
import io.grpc.ServerBuilder;
import io.grpc.StatusRuntimeException;
import javassist.bytecode.stackmap.TypeData;
import model.*;
import org.apache.zookeeper.KeeperException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import zookeeper.ZooKeeperClient;
import zookeeper.ZooKeeperClientImpl;

import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

import static java.lang.Thread.sleep;

@Service
public class TransactionManager {
    private static final Logger LOGGER = Logger.getLogger(TypeData.ClassName.class.getName());

    // ZooKeeper Client
    final private ZooKeeperClient zk;

    // My server and shard ID - so we don't have to ask for it every time
    private String myServerId;
    private String myShardId;

    // Request Handler Delegate - Used to forward requests to other servers
    final private RequestHandler delegate;
    // RPC Service is responsible for issuing gRPC requests to other servers
    final private RPCService rpcService;
    // Atomic Broadcast Service
    final private AtomicBroadcast atomicBroadcast;

    // The Transaction Ledger (db)
    final private TransactionLedger ledger;

    public TransactionManager() {
        this.zk = new ZooKeeperClientImpl();
        this.ledger = new TransactionLedger();
        this.delegate = new RequestHandler(this);
        this.rpcService = new RPCService(this);
        this.atomicBroadcast = new AtomicBroadcast(this);
    }

    /** Setup Stage for all the subcomponents */
    public void setup() throws IOException {
        // Setup Zookeeper and wait until all servers are up
        this.zk.setup();

        // Get all the needed information
        LOGGER.log(Level.INFO, String.format("setup: Zookeeper finished setup"));
        this.myServerId = zk.getServerId();
        this.myShardId = zk.getShardId();
        Map<String, String> serversAddresses = new HashMap<>();
        Map<String, List<String>> shards = new HashMap<>();
        Map<String, String> sequencers = new ConcurrentHashMap<>();
        try {
            List<String> servers = zk.getServers();
            for (String server : servers) {
                serversAddresses.put(server, zk.getServerAddress(server));
            }
            shards = zk.getShards();
            for (String shard : shards.keySet()) {
                sequencers.put(shard, zk.watchLeader(shard, (currShardId, newLeader) -> {
                    sequencers.put(currShardId, newLeader);
                    return null;
                }));
            }
        } catch (KeeperException | InterruptedException | ClassNotFoundException e) {
            e.printStackTrace();
        }
        LOGGER.log(Level.INFO, String.format("Finished configuration:"));
        LOGGER.log(Level.INFO, String.format("- myServerId=[%s]", myServerId));
        LOGGER.log(Level.INFO, String.format("- myShardId=[%s]", myShardId)); LOGGER.log(Level.INFO, String.format("- shards=[%s]", shards)); LOGGER.log(Level.INFO, String.format("- serversAddresses=[%s]", serversAddresses)); LOGGER.log(Level.INFO, String.format("- sequencers=[%s]", sequencers));

        // Same serverBuilder will be used by all services
        ServerBuilder<?> serverBuilder = ServerBuilder.forPort(Integer.parseInt(serversAddresses.get(myServerId).split(":")[1]));

        // Add services to the serverBuilder before continuing
        this.delegate.addServices(serverBuilder);
        this.rpcService.addServices(serverBuilder);
        this.atomicBroadcast.addServices(serverBuilder);

        // Run the gRPC server in the background
        Server grpcServer = serverBuilder.build();
        try {
            grpcServer.start();
        } catch (IOException e) {
            e.printStackTrace();
        }

        // Setup services
        this.delegate.setup(serversAddresses);
        this.rpcService.setup(serversAddresses);
        this.atomicBroadcast.setup(myServerId, myShardId, serversAddresses, shards, sequencers);

        // Add Genesis Block to pool
        try {
            String genesisShardId = zk.getResponsibleShard("Genesis");
            if (genesisShardId.equals(myShardId)) {
                LOGGER.log(Level.INFO, String.format("My shard is responsible for Genesis Transaction"));
                this.addGenesisBlock(this.getNewTimestamp());
            } else {
                LOGGER.log(Level.INFO, String.format("My shard isn't responsible for Genesis Transaction, %s is", genesisShardId));
            }
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
        }


        // FIXME FIXME tests delete
        // FIXME FIXME tests delete
        // FIXME FIXME tests delete
        for (String s : List.of("Genesis", "Satoshi", "Sajy", "Rani", "Yaron", "Coco")) {
            try {
                LOGGER.log(Level.INFO, String.format("Responsible[%s] is %s", s, zk.getResponsibleShard(s)));
            } catch (InterruptedException e) {
                e.printStackTrace();
            } catch (KeeperException e) {
                e.printStackTrace();
            }
        }
        LOGGER.log(Level.INFO, String.format("Got timestamp: %d", this.getNewTimestamp()));
        LOGGER.log(Level.INFO, String.format("Got timestamp: %d", this.getNewTimestamp()));
        LOGGER.log(Level.INFO, String.format("Got timestamp: %d", this.getNewTimestamp()));
        LOGGER.log(Level.INFO, String.format("Got timestamp: %d", this.getNewTimestamp()));
        LOGGER.log(Level.INFO, String.format("Got timestamp: %d", this.getNewTimestamp()));
        LOGGER.log(Level.INFO, String.format("Got timestamp: %d", this.getNewTimestamp()));


        // FIXME: MUST wait for setup to finish in ALL servers before finishing setup
        // Add a barrier
        try { sleep(3000); } catch (InterruptedException e) { e.printStackTrace(); }

    }

    ////////////////////// Pending Requests ///////////////////////
    final private List<PendingRequest> pendingRequests = new ArrayList<>();
    private class PendingRequest {
        private Integer mutex;
        private String requestId;
        private int localRequestId; // for Sajy
    }

    /**
     *  Request Handling:
     *  Functions are called from either the REST server or from another Server using the RequestHandler service.
     *  They either perform locally or post a request to the atomic broadcast and wait until the request is completed.
     *  Functions should return the response - not exception so calling server using gRPC can handle them correctly.
     */
    public Response.TransactionResp handleTransaction(Request.TransactionRequest req) {
        // FIXME: Implement
        LOGGER.log(Level.INFO, String.format("handleTransaction: Got request %s", req.toString()));
        // FIXME FIXME tests delete
        // FIXME FIXME tests delete
        // FIXME FIXME tests delete
        atomicBroadcast.broadcastListEntireHistory(List.of("shard-1"), 0, myServerId, 100);
        atomicBroadcast.broadcastListEntireHistory(List.of("shard-2"), 1, myServerId, 101);
        atomicBroadcast.broadcastListEntireHistory(List.of("shard-1", "shard-2"), 2, myServerId, 102);
        atomicBroadcast.broadcastListEntireHistory(List.of("shard-2", "shard-0"), 3, myServerId, 103);
        atomicBroadcast.broadcastListEntireHistory(List.of("shard-1", "shard-2", "shard-0"), 4, myServerId, 104);
        return null;
    }
    public Response.TransactionResp handleCoinTransfer(String sourceAddress, String targetAddress, long coins, String reqId) {
        // FIXME: Implement
        return null;
    }
    public Response.TransactionListResp handleListEntireHistory(int limit) {
        // FIXME: Implement
        return null;
    }
    public Response.TransactionListResp handleAtomicTxList(List<Request.TransactionRequest> atomicList) {
        // FIXME: Implement
        return null;
    }
    public Response.UnusedUTxOListResp handleListAddrUTxO(String sourceAddress) {
        // FIXME: Implement
        return null;
    }
    public Response.TransactionListResp handleListAddrTransactions(String sourceAddress, int limit) {
        // FIXME: Implement
        return null;
    }


    /**
     *  RPC services:
     *  Functions are called using gRPC from other servers. Used for submitting a transaction,
     *  checking if an atomic list can be submitted or giving the entire history.
     */
    public void recordSubmittedTransaction(Transaction transaction) {
        // FIXME: Implement
        return;
    }
    public List<Response> canProcessAtomicTxListStubs(List<Request.TransactionRequest> atomicList) {
        // FIXME: Implement
        return null;
    }
    public List<Transaction> getEntireHistory(int limit) {
        // FIXME: Implement
        return null;
    }


    /**
     *  Request Processing:
     *  Functions are called on requests that were broadcast using AtomicBroadcast to multiple servers.
     *  Functions should do the processing logic here for each of the functions.
     *  Each request (other than the ListEntireHistory) arrives with an idempotency key used to
     *  identify a specific request. If the idempotency key matches a previous request, we don't re-execute.
     *  We should then return the original response with a `FIXME` http status code.
     *  The idempotencyKey for Coin Transfer should be: "CoinTransfer-{reqId}-{sourceAddress}-{targetAddress}-{coins}"
     *  The idempotencyKey for a regular Transaction or for an AtomicList is the transactionIds.
     */
    public void processTransaction(Transaction trans, String idempotencyKey, String origServerId, int pendingReqId) {
        // FIXME: Implement
        return;
    }
    public void processAtomicTxList(List<Transaction> atomicList, String idempotencyKey, String origServerId, int pendingReqId) {
        // FIXME: Implement
        return;
    }
    public void processListEntireHistory(int limit, String origServerId, int pendingReqId) {
        // FIXME: Implement
        return;
    }

    /**
     *  General useful helper functions
     */
    public long getNewTimestamp() {
        return zk.getTimestamp();
    }
    private void addGenesisBlock(long timestamp) {
        // FIXME: Should add the Genesis Block to the database since we are responsible for it.
        return;
    }

}
