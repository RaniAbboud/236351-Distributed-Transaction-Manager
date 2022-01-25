package atomicbroadcast;

import com.google.protobuf.Empty;
import cs236351.grpcservice.BroadcastMsg;
import cs236351.grpcservice.TransactionHistoryMsg;
import grpcservice.RequestHandlerUtils;
import io.grpc.*;
import io.grpc.stub.StreamObserver;
import kotlin.Pair;
import model.Transaction;
import transactionmanager.TransactionManager;
import cs236351.grpcservice.AtomicBroadcastServiceGrpc;

import java.util.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.LinkedBlockingDeque;
import java.util.function.Function;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;


public class AtomicBroadcast extends AtomicBroadcastServiceGrpc.AtomicBroadcastServiceImplBase {
    private static final Logger logger = Logger.getLogger(AtomicBroadcast.class.getName());

    /** The TransactionManager sending/receiving Broadcasts */
    TransactionManager mngr;

    /**
     * System information:
     *   myServerId: the serverId of this module
     *   myShardId: the shardId of this module
     *   sequencers: A mapping from each shardId to the serverId of the shard's sequencers
     */
    String myServerId;
    String myShardId;
    String ID;
    volatile Map<String, String> sequencers = new ConcurrentHashMap<>();

    /** Proposals Queue: Contains all the proposals for the Sequencer to schedule */
    private static class BroadcastMsgAndFlag {
        BroadcastMsg msg;
        volatile boolean scheduledSignal;
        public BroadcastMsgAndFlag(BroadcastMsg msg) {
            this.msg = msg;
            this.scheduledSignal = false;
        }
        public void finish() {
            synchronized (this) {
                scheduledSignal = true;
                this.notify();
            }
        }
        public void waitScheduled() {
            synchronized (this) {
                while (!this.scheduledSignal) {
                    try {
                        this.wait();
                    } catch (InterruptedException e) {
                        /* Nothing */
                    }
                }
            }
        }
    }
    BlockingQueue<BroadcastMsgAndFlag> proposalsQueue;
    /** Packets Queue: Contains all the packets ordered by sequencer that need to be executed */
    BlockingQueue<BroadcastMsg> packetsQueue;

    /** Channels to all other Services */
    private Map<String, Function<BroadcastMsg, Empty>> broadcastToShardStubs = new HashMap<>();

    /** Sequencer and Executor */
    Sequencer sequencer;
    Executor executor;


    public AtomicBroadcast(TransactionManager mngr) {
        this.mngr = mngr;
        this.proposalsQueue = new LinkedBlockingDeque<>();
        this.packetsQueue = new LinkedBlockingDeque<>();
        this.executor = new Executor(mngr, packetsQueue);
        this.sequencer = new Sequencer(mngr, executor, proposalsQueue);
    }

    public void addServices(ServerBuilder<?> serverBuilder) {
        serverBuilder.addService(this);
    }

    public void setup(String myServerId, String myShardId, Map<String, String> serversAddresses, Map<String, List<String>> shards, Map<String, String> sequencers ) {
        this.myServerId = myServerId;
        this.myShardId = myShardId;
        this.ID = String.format("%s-%s", myServerId, myShardId);
        Map<String, Function<BroadcastMsg, Empty>> executeMsgStubs = new HashMap<>();
        for (Map.Entry<String,String> entry : serversAddresses.entrySet()) {
            logger.log(Level.INFO, String.format("%s: Creating a blocking stub to server %s at address %s",
                    ID, entry.getKey(), entry.getValue()));
            ManagedChannel channel = ManagedChannelBuilder.forTarget(entry.getValue()).usePlaintext().build();
            AtomicBroadcastServiceGrpc.AtomicBroadcastServiceBlockingStub stub = AtomicBroadcastServiceGrpc.newBlockingStub(channel);
            // broadcastToShardStubs are needed to all servers since all of them can be sequencers at some point
            this.broadcastToShardStubs.put(entry.getKey(), stub::broadcastToShard);
            // executeMsgStubs needed only for servers in my Shard other than me
            if (shards.get(myShardId).contains(entry.getKey()) && !myServerId.equals(entry.getKey())) {
                executeMsgStubs.put(entry.getKey(), stub::executeMsg);
            }
        }
        this.sequencer.setup(myServerId, myShardId, ID, executeMsgStubs);
        this.executor.setup(ID);
        // Run the Sequencer and Executor on two new Threads
        new Thread(this.sequencer, "AtomicBroadcastSequencer").start();
        new Thread(this.executor, "AtomicBroadcastExecutor").start();
        // Add sequencers
        this.sequencers = sequencers;
    }

    /**
     * Broadcast Functions
     * These functions are called by the Transaction Manager to broadcast specific requests to specific shards.
     * Should go over the shards list (sorted to prevent deadlocks) and propose the request to the leader of the shard.
     * When the shard guarantees that it is submitted or that it has already broadcast a request with the same pair
     * of <origServerId, pendingReqId> then it returns, and we can continue to the next shard.
     *
     * When broadcasting a Transaction or a CoinTransfer, the leader should first get a timestamp and add it to the Transaction
     * before submitting it. It is not needed for AtomicTxList since it will be taken inside the barrier.
     */
    public void broadcastTransaction(String shard, Transaction trans, String idempotencyKey, String origServerId, int pendingReqId) {
        BroadcastMsg msg = BroadcastMsg.newBuilder()
                .setOrigServerId(origServerId)
                .setPendingReqId(pendingReqId)
                .setIdempotencyKey(idempotencyKey)
                .setTransaction(RequestHandlerUtils.createTransactionMsg(trans))
                .build();
        List<String> shardSingleton = Collections.singletonList(shard);
        this.broadcastToShards(shardSingleton, msg);
    }
    public void broadcastAtomicTxList(List<String> shards, List<Transaction> atomicList, String idempotencyKey, String origServerId, int pendingReqId) {
        BroadcastMsg msg = BroadcastMsg.newBuilder()
                .setOrigServerId(origServerId)
                .setPendingReqId(pendingReqId)
                .setIdempotencyKey(idempotencyKey)
                .setTransactionsList(TransactionHistoryMsg.newBuilder().addAllTransactions(atomicList.stream().map(
                        RequestHandlerUtils::createTransactionMsg).collect(Collectors.toList())).build())
                .build();
        this.broadcastToShards(shards, msg);
    }
    public void broadcastListEntireHistory(List<String> shards, int limit, String origServerId, int pendingReqId) {
        BroadcastMsg msg = BroadcastMsg.newBuilder()
                .setOrigServerId(origServerId)
                .setPendingReqId(pendingReqId)
                .setLimit(limit)
                .build();
        this.broadcastToShards(shards, msg);
    }
    public void broadcastToShards(List<String> shardsList, BroadcastMsg msg) {
        shardsList = new ArrayList<>(shardsList);
        Collections.sort(shardsList); // Very important to prevent deadlocks
        logger.log(Level.INFO, String.format("%s: Need to broadcast %s to %s", ID, msg.toString(), shardsList.toString()));
        for (String currShard : shardsList) {
            String currSequencer = sequencers.get(currShard);
            try {
                logger.log(Level.FINEST, String.format("%s: Sending message %s to sequencer %s of shard %s", ID, msg.toString(), currSequencer, currShard));
                Empty resp = broadcastToShardStubs.get(currSequencer).apply(msg);
                logger.log(Level.FINEST, String.format("%s: Sending message to %s succeeded", ID, currSequencer));
            } catch (StatusRuntimeException e) {
                logger.log(Level.FINEST, String.format("%s: Sending message to %s failed!! Sequencers should be alive !!", ID, currSequencer));
                throw new RuntimeException(String.format("%s: Sequencer %s of Shard %s isn't available!!", ID, currSequencer, currShard));
            }
        }
    }

    /**
     *  The services used in the AtomicBroadcast:
     *  Execution and Proposition
     */
    /* Called on a request that we need to broadcast */
    @Override
    public void broadcastToShard(BroadcastMsg request, StreamObserver<Empty> responseObserver) {
        logger.log(Level.FINEST, String.format("%s: Received proposal to sequencer: %s. Pushing to Queue.", ID, request.toString()));
        BroadcastMsgAndFlag msg = new BroadcastMsgAndFlag(request);
        proposalsQueue.add(msg);
        msg.waitScheduled();
        logger.log(Level.FINEST, String.format("%s: Request %s scheduled.", ID, request.toString()));
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    /* Called on a request by the sequencer to execute */
    @Override
    public void executeMsg(BroadcastMsg request, StreamObserver<Empty> responseObserver) {
        logger.log(Level.FINEST, String.format("%s: Received message from sequencer to execute: %s. Pushing to Queue.", ID, request.toString()));
        packetsQueue.add(request);
        responseObserver.onNext(Empty.newBuilder().build());
        responseObserver.onCompleted();
    }

    /**
     * Sequencer is the class responsible for sequencing the requests
     */
    class Sequencer implements Runnable {
        private final Logger logger = Logger.getLogger(Sequencer.class.getName());

        TransactionManager mngr;
        Executor executor;
        BlockingQueue<BroadcastMsgAndFlag> proposalsQueue;
        String myServerId;
        String myShardId;
        String ID;
        Map<String, Function<BroadcastMsg, Empty>> executeMsgStubs;

        public Sequencer(TransactionManager mngr, Executor executor, BlockingQueue<BroadcastMsgAndFlag> proposalsQueue) {
            this.mngr = mngr;
            this.executor = executor;
            this.proposalsQueue = proposalsQueue;
        }

        public void setup(String myServerId, String myShardId, String ID, Map<String, Function<BroadcastMsg, Empty>> executeMsgStubs) {
            this.myServerId = myServerId;
            this.myShardId = myShardId;
            this.ID = ID;
            this.executeMsgStubs = executeMsgStubs;
        }

        @Override
        public void run() {
            Set<Pair<String, Integer>> done = new HashSet<>();
            while (true) {
                try {
                    BroadcastMsgAndFlag currReq = proposalsQueue.take();
                    logger.log(Level.FINEST, String.format("%s: Popped message from proposals queue to execute: %s", ID, currReq.msg.toString()));
                    currReq.finish(); // Signal that it is scheduled to requester
                    Pair<String, Integer> currId = new Pair(currReq.msg.getOrigServerId(), currReq.msg.getPendingReqId());
                    if (done.contains(currId)) {
                        logger.log(Level.FINEST, String.format("%s: Message from %s with pendingReqId %s already executed", ID, currId.component1(), currId.component2()));
                    } else {
                        BroadcastMsg msgToExecute = currReq.msg;
                        // Check if need to take a timestamp
                        if (currReq.msg.hasTransaction()) {
                            long currTimestamp = mngr.getNewTimestamp();
                            logger.log(Level.FINEST, String.format("%s: Took timestamp %s for message", ID, currTimestamp));
                            msgToExecute = currReq.msg.toBuilder().setAssignedTimestamp(currTimestamp).build();
                        }
                        logger.log(Level.FINEST, String.format("%s: Executing %s", ID, msgToExecute.toString()));
                        for (Map.Entry<String, Function<BroadcastMsg, Empty>> entry : executeMsgStubs.entrySet()) {
                            try {
                                logger.log(Level.FINEST, String.format("%s: Sending packet to %s", ID, entry.getKey()));
                                Empty resp = entry.getValue().apply(msgToExecute);
                                logger.log(Level.FINEST, String.format("%s: Sending packet to %s succeeded", ID, entry.getKey()));
                            } catch (StatusRuntimeException e) {
                                logger.log(Level.FINEST, String.format("%s: Sending packet to %s failed", ID, entry.getKey()));
                            }
                        }
                        this.executor.executePacket(msgToExecute);
                    }
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    /**
     * Executor is the class responsible for executing - for non sequencers
     */
    class Executor implements Runnable {
        private final Logger logger = Logger.getLogger(Executor.class.getName());

        TransactionManager mngr;
        BlockingQueue<BroadcastMsg> packetsQueue;
        String ID;

        public Executor(TransactionManager mngr, BlockingQueue<BroadcastMsg> packetsQueue) {
            this.mngr = mngr;
            this.packetsQueue = packetsQueue;
        }

        public void setup(String ID) {
            this.ID = ID;
        }

        public void executePacket(BroadcastMsg packet) {
            logger.log(Level.FINEST, String.format("%s: Started executing: %s", ID, packet.toString()));
            if (packet.hasTransaction()) {
                Transaction trans = RequestHandlerUtils.createTransaction(packet.getTransaction());
                trans.setTimestamp(packet.getAssignedTimestamp());
                mngr.processTransactionLocally(trans, packet.getIdempotencyKey(), packet.getOrigServerId(), packet.getPendingReqId());
            } else if (packet.hasTransactionsList()) {
                mngr.processAtomicTxListLocally(
                        packet.getTransactionsList().getTransactionsList().stream().map(RequestHandlerUtils::createTransaction)
                                .collect(Collectors.toList()),
                        packet.getIdempotencyKey(), packet.getOrigServerId(), packet.getPendingReqId());
            } else if (packet.hasLimit()) {
                mngr.processListEntireHistoryLocally(packet.getLimit(), packet.getOrigServerId(), packet.getPendingReqId());
            }
            logger.log(Level.FINEST, String.format("%s: Done executing", ID));
        }

        @Override
        public void run() {
            while (true) {
                try {
                    BroadcastMsg currReq = packetsQueue.take();
                    logger.log(Level.FINEST, String.format("%s: Popped message from queue to execute: %s", ID, currReq.toString()));
                    this.executePacket(currReq);
                } catch (InterruptedException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
