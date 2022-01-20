package transactionmanager;

import atomicbroadcast.AtomicBroadcast;
import grpcservice.RPCService;
import grpcservice.RequestHandler;
import io.grpc.Server;
import io.grpc.ServerBuilder;
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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;

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
        LOGGER.log(Level.INFO, String.format("- myShardId=[%s]", myShardId));
        LOGGER.log(Level.INFO, String.format("- shards=[%s]", shards));
        LOGGER.log(Level.INFO, String.format("- serversAddresses=[%s]", serversAddresses));
        LOGGER.log(Level.INFO, String.format("- sequencers=[%s]", sequencers));

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

        // Wait for all servers to finish setup using the "initial barrier".
        final String initialBarrierId = "intial-setup";
        try{
            zk.enterBarrier(initialBarrierId, zk.getShards().keySet().toArray(new String[0]));
        } catch (InterruptedException | KeeperException e) {
            LOGGER.log(Level.SEVERE, "failed to enter initial-setup barrier", e);
        }
        LOGGER.log(Level.INFO, String.format("Server %s has entered the initial barrier", myServerId));
        // Add Genesis Block to the server's storage (in case this server in the responsible shard)
        try {
            String genesisShardId = zk.getResponsibleShard("Genesis-Transaction");
            if (genesisShardId.equals(myShardId)) {
                LOGGER.log(Level.INFO, String.format("My shard is responsible for Genesis Transaction. Adding it to the ledger."));
                this.addGenesisBlockToLedger(this.getNewTimestamp());
            } else {
                LOGGER.log(Level.INFO, String.format("My shard isn't responsible for Genesis Transaction, %s is", genesisShardId));
            }
        } catch (InterruptedException | KeeperException e) {
            LOGGER.log(Level.SEVERE, String.format("Server $s (in shard $s) failed to register the Genesis Transaction.", myServerId, myShardId), e);
        }
        try{
            zk.leaveBarrier(initialBarrierId, zk.getShards().keySet().toArray(new String[0]));
        } catch (InterruptedException | KeeperException e) {
            LOGGER.log(Level.SEVERE, String.format("Server %s failed to leave initial-setup barrier", myServerId), e);
        }
        LOGGER.log(Level.INFO, String.format("Server %s has left the initial barrier", myServerId));
    }

    ////////////////////// Pending Requests ///////////////////////
    volatile private Map<Integer, PendingRequest> pendingRequests = new ConcurrentHashMap<>();
    volatile private AtomicInteger currPendingReqId = new AtomicInteger();
    private class PendingRequest {
        private volatile Response resp = null;
        private void finish(Response resp) {
            synchronized (this) {
                this.resp = resp;
                this.notify();
            }
        }
        private <T> T waitDone() {
            synchronized (this) {
                while (resp == null) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        /* Nothing */
                    }
                }
            }
            return (T) this.resp;
        }
    }

    /**
     *  Request Handling:
     *  Functions are called from either the REST server or from another Server using the RequestHandler service.
     *  They either perform locally or post a request to the atomic broadcast and wait until the request is completed.
     *  Functions should return the response - not exception so calling server using gRPC can handle them correctly.
     */
    public Response.TransactionResp handleTransaction(Request.TransactionRequest req) {
        LOGGER.log(Level.INFO, String.format("handleTransaction: Received request %s", req.toString()));
        if (isResponsibleForAddress(req.inputs.get(0).getAddress())) {
            LOGGER.log(Level.INFO, String.format("handleTransaction: Will handle request"));
            Transaction transaction = new Transaction(req.inputs, req.outputs);
            String idempotencyKey = String.format("Transaction-%s", transaction.getTransactionId());
            Integer pendingReqId = currPendingReqId.incrementAndGet();
            PendingRequest pendingRequest = new PendingRequest();
            pendingRequests.put(pendingReqId, pendingRequest);
            LOGGER.log(Level.INFO, String.format("handleTransaction: Broadcasting transaction [%s] to shard [%s] with key [%s]",
                                        transaction.toString(), myShardId, idempotencyKey));
            atomicBroadcast.broadcastTransaction(myShardId, transaction, idempotencyKey, myServerId, pendingReqId);
            Response.TransactionResp resp = pendingRequest.waitDone();
            pendingRequests.remove(pendingReqId);
            LOGGER.log(Level.INFO, String.format("handleTransaction: Got response %s", resp.toString()));
            return resp;
        } else {
            String responsibleShard = getResponsibleShard(req.inputs.get(0).getAddress());
            List<String> responsibleServers = getServersInShard(responsibleShard);
            LOGGER.log(Level.INFO, String.format("handleTransaction: Won't handle request, will send to responsible shard: %s at servers: %s",
                        responsibleShard, responsibleServers.toString()));
            return delegate.client.delegateHandleTransaction(responsibleServers, req);
        }
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
    public void gRPCRecordSubmittedTransaction(Transaction transaction) {
        // FIXME: Implement
        return;
    }
    public List<Response> gRPCCanProcessAtomicTxListStubs(List<Request.TransactionRequest> atomicList) {
        // FIXME: Implement
        return null;
    }
    public List<Transaction> gRPCGetEntireHistory(int limit) {
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

    /** All finished requests with the responses we created for them */
    volatile private Map<String, Response> doneRequests = new ConcurrentHashMap<>();

    public void processTransactionLocally(Transaction trans, String idempotencyKey, String origServerId, int pendingReqId) {
        LOGGER.log(Level.INFO, String.format("processTransaction: Received transaction %s with key %s from %s with pendingReqId %d",
                            trans.toString(), idempotencyKey, origServerId, pendingReqId));
        if (doneRequests.containsKey(idempotencyKey)) {
            LOGGER.log(Level.INFO, String.format("processTransaction: Request with key %s already processed"));
            if (origServerId.equals(myServerId)) {
                LOGGER.log(Level.INFO, String.format("processTransaction: I will return the original response"));
                this.pendingRequests.get(pendingReqId).finish(doneRequests.get(idempotencyKey));
            } else {
                LOGGER.log(Level.INFO, String.format("processTransaction: Already processed and not originated by me, ignoring."));
            }
        } else {
            LOGGER.log(Level.INFO, String.format("processTransaction: Need to process transaction"));
            Response.TransactionResp resp = this.tryProcessTransactionLocally(trans);
            if (resp.statusCode == HttpStatus.OK) {
                LOGGER.log(Level.INFO, String.format("processTransaction: Transaction processed successfully"));
                this.doneRequests.put(idempotencyKey, resp);
            } else {
                LOGGER.log(Level.INFO, String.format("processTransaction: Transaction failed!!"));
            }
            if (myServerId.equals(origServerId)) {
                LOGGER.log(Level.INFO, String.format("processTransaction: Transaction processed, returning %s", resp.toString()));
                this.pendingRequests.get(pendingReqId).finish(resp);
            }
        }
    }
    public void processAtomicTxListLocally(List<Transaction> atomicList, String idempotencyKey, String origServerId, int pendingReqId) {
        // FIXME: Implement
        return;
    }
    public void processListEntireHistoryLocally(int limit, String origServerId, int pendingReqId) {
        // FIXME: Implement
        return;
    }

    /**
     *  General useful helper functions
     */
    public long getNewTimestamp() {
        return zk.getTimestamp();
    }
    private String getResponsibleShard(String address) {
        try {
            return zk.getResponsibleShard(address);
        } catch (InterruptedException | KeeperException e) {
            e.printStackTrace();
            return null;
        }
    }
    private boolean isResponsibleForAddress(String address) {
        return this.myShardId.equals(this.getResponsibleShard(address));
    }
    private List<String> getServersInShard(String shardId) {
        try {
            return zk.getServersInShard(shardId);
        } catch (InterruptedException | KeeperException  e) {
            e.printStackTrace();
            return null;
        }
    }

    private Response.TransactionResp tryProcessTransactionLocally(Transaction trans) {
        // FIXME: Implement
        // 0. If already processed: - Need to check in db
        // return the processing response along with an ALREADY_PROCESSED flag.
        // 1. Verify it can be processed:
        // if not, return a response with INVALID flag - maybe specify exactly why.
        // 2. Move each input UTxO to the "used UTxOs"
        // 3. Add the transaction to "transactions pool"
        // 4. Add Transfers in his shard to the pool of "unused UTxOs"
        // 5. Send transaction and transfers to the relevant shards (other than my shard) and wait until it is received by them.
        // 6. Return a response of the transaction processing with SUBMITTED flag.
        trans.setTimestamp(1000);
        return new Response.TransactionResp(HttpStatus.OK, "All good", trans);
    }

    private void addGenesisBlockToLedger(long timestamp) {
        // FIXME: Should add the Genesis Block to the database since we are responsible for it.
        return;
    }

}
