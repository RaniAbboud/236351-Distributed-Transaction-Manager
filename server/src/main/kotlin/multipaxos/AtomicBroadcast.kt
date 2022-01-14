package multipaxos

import com.google.protobuf.ByteString
import cs236351.multipaxos.*
import io.grpc.*
import cs236351.multipaxos.MultiPaxosProposerServiceGrpcKt.MultiPaxosProposerServiceCoroutineStub as ProposerGrpcStub
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.lang.Thread.sleep
import java.util.*
import kotlin.coroutines.CoroutineContext

data class TotallyOrderedMessage<T>(
    val sequenceNo: Int,
    val message: T,
)

class AtomicBroadcast<T>(
    /* System information */
    private val myId: String,                      // This server's Id
    private val shards: Map<String, List<String>>, // A mapping from shardId to list of servers within shard
    private val omegaFD: Map<String, OmegaFailureDetector>, // Failure detectors for each of the shards
    /* Server and Channels */
    private var serverBuilder: ServerBuilder<*>,
    private var channels: MutableMap<String, ManagedChannel>,
    /* Other variables */
    public val biSerializer: ByteStringBiSerializer<T>,
    receiveCapacityBufferSize: Int = 100,
    private val scope: CoroutineScope,
    private val context: CoroutineContext = paxosThread,
) {

    /* Information about this server */
    private var myShardId : String = ""

    /* gRPC channels and services */
    private var proposer : Proposer
    private var proposers: Map<String, ProposerGrpcStub> = emptyMap()

    init {
        /* Get my shardId*/
        for ((shardId, serverIds) in shards) {
            if (myId in serverIds) {
                myShardId = shardId
            }
        }

        /* Create my Services */
        val learnerService = LearnerService(scope)
        val acceptorService = AcceptorService(myId)

        /* Create proposer channels */
        proposers = (channels.mapValues { (_, v) -> ProposerGrpcStub(v) })

        /* Add servers in same shard to my learner service */
        learnerService.learnerChannels = channels.filterKeys {
            (it != myId) && (it in shards[myShardId]!!)
        }.values.toList()

        /* Create Proposer */
        proposer = Proposer(
            id = myId,
            omegaFD = omegaFD[myShardId]!!,
            scope = scope,
            acceptors = channels.filterKeys { it in shards[myShardId]!! },
            thisLearner = learnerService,
        )

        /* Add services to serverBuilder */
        serverBuilder.apply {addService(acceptorService)}
            .apply {addService(learnerService)}
            .apply {addService(proposer)}

        /* Register in learner service */
        learnerService.observers += { seq, serialized ->
            messageBuffer.buffer(seq, serialized)
            messageBuffer.deliverAll()
        }
    }

    /*
    *  Main function of the AtomicBroadcast
    */
    public suspend fun start() {
        /* Start proposer thread */
        proposer.start()
    }

    /*
    *   Handle receiving of messages from learner
    */
    private var `sequence#` = 0
    private val chan = Channel<TotallyOrderedMessage<T>>(receiveCapacityBufferSize)
    public val stream: ReceiveChannel<TotallyOrderedMessage<T>>
        get() = chan

    private val messageBuffer = object {
        private val buffer = PriorityQueue<Pair<Int, ByteString>>(
            // Comparator
            { o1, o2 -> o1!!.first - o2!!.first }
        )
        private var lastDelivered = 0

        // Mutex is used to ensure that only one coroutine
        // interacts with the queue at a time since the priority queue
        // implementation that is used is not thread safe
        private val mutex = Mutex()

        suspend fun buffer(seq: Int, serialized: ByteString) = mutex.withLock {
            buffer.offer(Pair(seq, serialized))
        }

        // Delivers all messages that have become deliverable
        suspend fun deliverAll() = mutex.withLock {
            while (!buffer.isEmpty()) {
                if (!isLatestMessageDeliverable()) break
                deliverLatestMessage()
            }
        }


        // A message is deliverable if it is the *next* message
        // after most recent message that has been delivered
        private fun isLatestMessageDeliverable() =
            buffer.peek()!!.first == lastDelivered + 1

        private suspend fun deliverLatestMessage() {
            val (seq, serialized) = buffer.poll()!!
            lastDelivered = seq

            // Note: This ignores empty byte strings
            if (serialized.size() == 0) {
                return
            }

            for (msg in _deliver(serialized)) {
                `sequence#`++
                chan.send(TotallyOrderedMessage(`sequence#`, msg))
            }
        }
    }

    /*
     * Handle sending and receiving messages with client:
     *    - send: Used to broadcast a message
     *    - receive: Used to receive a broadcast message
     */

    public suspend fun send(msg: T, toShards : List<String>) {
        this._send(biSerializer(msg), toShards)
    }
    public suspend fun receive(): TotallyOrderedMessage<T> = stream.receive()

    suspend fun _send(byteString: ByteString, toShards : List<String>) {
        if (MULTIPAXOS_DEBUG) println("Need to send ${byteString} to shards list: ${toShards}")
        for (targetShard in toShards) {
            scope.launch(context) {
                if (MULTIPAXOS_DEBUG) println("Need to send ${byteString} to ${targetShard}")
                while (true) {
                    var currLeader = omegaFD[targetShard]!!.leader
                    if (MULTIPAXOS_DEBUG) println("Need to send ${byteString} to leader ${currLeader} of ${targetShard}")
                    try {
                        val resp = proposers[currLeader]?.doPropose(propose { value = byteString })
                        if (resp?.ack == Ack.YES) {
                            if (MULTIPAXOS_DEBUG) println("Submitted ${byteString} to ${targetShard}")
                            break
                        } else {
                            if (MULTIPAXOS_DEBUG) println("Leader ${currLeader} of shard ${targetShard} changed")
                            sleep(1)
                            continue
                        }
                    } catch (e: StatusException) {
                        if (MULTIPAXOS_DEBUG) println("Leader ${currLeader} of shard ${targetShard} failed, need to resend")
                        sleep(1)
                        continue
                    }
                }
            }
        }
    }

    fun _deliver(byteString: ByteString): List<T> = listOf(biSerializer(byteString))
}