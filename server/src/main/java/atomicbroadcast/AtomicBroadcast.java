package atomicbroadcast;

import io.grpc.ServerBuilder;
import model.Transaction;
import transactionmanager.TransactionManager;

import java.util.List;


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
    public void broadcastTransaction(String shards, Transaction trans, String origServerId, int pendingReqId) {
    }
    public void broadcastCoinTransfer(String shards, Transaction trans, String idempotencyKey, String origServerId, int pendingReqId) {
    }
    public void broadcastAtomicTxList(List<String> shards, List<Transaction> atomicList, String origServerId, int pendingReqId) {
    }
    public void broadcastListEntireHistory(List<String> shards, int limit, String origServerId, int pendingReqId) {
    }

}
