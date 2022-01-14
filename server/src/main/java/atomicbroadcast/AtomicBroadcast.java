package atomicbroadcast;

import grpcservice.RequestHandlerClient;
import grpcservice.RequestHandlerServer;
import io.grpc.ServerBuilder;
import model.Transaction;
import transactionmanager.TransactionManager;

import java.util.List;
import java.util.Map;

public class AtomicBroadcast {

    /** The TransactionManager sending/receiving Broadcasts */
    TransactionManager mngr;

    public AtomicBroadcast(TransactionManager mngr) {
        this.mngr = mngr;
    }

    public void addServices(ServerBuilder<?> serverBuilder) {
        // FIXME: Add services
    }

    public void setup() {
        // FIXME: Get system configuration and create whatever needed.
    }

    /**
     * Broadcast Functions
     * These functions are called by the Transaction Manager to broadcast specific requests to specific shards.
     * Should go over the shards list (sorted to prevent deadlocks) and propose the request to the leader of the shard.
     * When the shard guarantees that it is submitted or that it has already broadcast a request with the same pair
     * of <origServerId, pendingReqId> then it returns, and we can continue to the next shard.
     */
    public void broadcastTransaction(List<String> shards, String idempotencyKey, Transaction trans, String origServerId, int pendingReqId) {
    }
    public void broadcastAtomicTxList(List<String> shards, String idempotencyKey, List<Transaction> atomicList, String origServerId, int pendingReqId) {
    }
    public void broadcastListEntireHistory(List<String> shards, int limit, String origServerId, int pendingReqId) {
    }

}
