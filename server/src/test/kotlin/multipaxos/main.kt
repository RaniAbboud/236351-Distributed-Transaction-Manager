package multipaxos


import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteStringUtf8
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import kotlinx.coroutines.*
import kotlinx.coroutines.CoroutineScope


suspend fun main(args: Array<String>) = coroutineScope {

    // FIXME - Sajy - Adds too much DEBUG messages
    // Displays all debug messages from gRPC
    // org.apache.log4j.BasicConfigurator.configure()

    // Take my ID
    val myId = args[0]

    // Current configuration
    val NUM_SHARDS = 3
    val NUM_SERVERS_PER_SHARD = 3
    val BASE_PORT = 8980

    // Create map of shards
    val shards : Map<String, List<String>> =
        (1..NUM_SHARDS).associate { it ->
            it.toString() to
                    ((it-1)*NUM_SHARDS+1..(it-1)*NUM_SHARDS + NUM_SERVERS_PER_SHARD).map {iit -> iit.toString()}.toList()
        }

    // Create map of id to port number
    val idToPort : Map<String, Int> = (1..NUM_SHARDS*NUM_SERVERS_PER_SHARD).associate { it.toString() to (BASE_PORT + it) }

    // Create failure detectors for each shard
    class OmegaFailureDetectorImpl (
        public var currLeader: String = "1"
    ) : OmegaFailureDetector {
        val observers: MutableList<suspend () -> Unit> = mutableListOf()
        override val leader: String get() = currLeader
        override fun addWatcher(observer: suspend () -> Unit) {
            observers.add(observer)
        }
        fun notifyObservers(scope: CoroutineScope) {
            scope.launch(paxosThread) {
                for (observer in observers) {
                    observer()
                }
            }
        }
        fun set(leader: String, scope: CoroutineScope) {
            currLeader = leader
            notifyObservers(scope)
        }
    }
    val omegaFD : Map<String, OmegaFailureDetector> = (1..NUM_SHARDS).associate {
        it.toString() to OmegaFailureDetectorImpl(((it-1)*NUM_SHARDS + 1).toString())
    }

    /* Create serializers */
    val biSerializer = object : ByteStringBiSerializer<String> {
        override fun serialize(obj: String) = obj.toByteStringUtf8()
        override fun deserialize(serialization: ByteString) = serialization.toStringUtf8()!!
    }

    /* Create managed channels to all servers */
    var channels: MutableMap<String, ManagedChannel> = mutableMapOf()
    for ((_, serverIds) in shards) {
        for (serverId in serverIds) {
            channels[serverId] = ManagedChannelBuilder.forAddress("localhost", idToPort[serverId]!!)
                .usePlaintext()
                .build()!!
        }
    }

    /* Create server for this client */
    var serverBuilder = ServerBuilder.forPort(idToPort[myId]!!)

    /* Print configuration */
    println("Configuration:")
    println("ID: ${myId}")
    println("NUM_SHARDS: ${NUM_SHARDS}")
    println("NUM_SERVERS_PER_SHARD: ${NUM_SERVERS_PER_SHARD}")
    println("shards: ${shards}")
    println("idToPort: ${idToPort}")
    println("omegaFD: ${omegaFD.mapValues{(_,v) -> v.leader}}")

    /* Create the atomic broadcast object */
    val atomicBroadcast = AtomicBroadcast<String>(
        myId = myId,
        shards = shards,
        serverBuilder = serverBuilder,
        channels = channels,
        omegaFD = omegaFD,
        biSerializer = biSerializer,
        scope = this
    )

    /* Build and Start gRPC server */
    var server = serverBuilder.build()
    withContext(Dispatchers.IO) {
        server.start()
    }

    // Run the atomic broadcast
    launch {
        atomicBroadcast.start()
    }

    startRecievingMessages(atomicBroadcast)
    delay(10000)
    startGeneratingMessages(myId, atomicBroadcast, NUM_SHARDS)


    // FIXME - Sajy - See if this is needed
    withContext(Dispatchers.IO) {
        server.awaitTermination()
    }
}

fun <T> Set<T>.subsets(): Sequence<Set<T>> = sequence {
    when (size) {
        0 -> yield(emptySet<T>())
        else -> {
            val head = first()
            val tail = this@subsets - head
            yieldAll(tail.subsets())
            for (subset in tail.subsets()) {
                yield(setOf(head) + subset)
            }
        }
    }
}

private fun CoroutineScope.startGeneratingMessages(
    myId: String,
    atomicBroadcast: AtomicBroadcast<String>,
    NUM_SHARDS : Int
) {
    println("Started Generating Messages")
    val NUM_MESSAGES_PER_TARGET = 100
    val DELAY_BETWEEN_MESSAGES = 1L
    val allSets = (1..NUM_SHARDS).toSet().subsets()
    for (s in allSets) {
        if (!s.isEmpty()) {
            var l = s.toList().map {it -> it.toString()}
            println("Sending to $l")
            launch {
                (1..NUM_MESSAGES_PER_TARGET).forEach {
                    delay(DELAY_BETWEEN_MESSAGES)
                    val prop = "[Value no $it/$NUM_MESSAGES_PER_TARGET from $myId to ${l}]"
                    println("Adding Proposal $prop to $l")
                    atomicBroadcast.send(prop, l)
                }
            }
        }
    }
}

private fun CoroutineScope.startRecievingMessages(atomicBroadcast: AtomicBroadcast<String>) {
    launch {
        while (true) {
            var (seqNo, msg) = atomicBroadcast.receive()
            println("Message #$seqNo: $msg  received!")
        }
    }
}