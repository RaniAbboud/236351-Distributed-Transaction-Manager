package multipaxos

import com.google.protobuf.ByteString
import com.google.protobuf.kotlin.toByteStringUtf8
import io.grpc.ManagedChannelBuilder
import io.grpc.ServerBuilder
import kotlinx.coroutines.*

val biSerializer = object : ByteStringBiSerializer<String> {
    override fun serialize(obj: String) = obj.toByteStringUtf8()
    override fun deserialize(serialization: ByteString) = serialization
        .toStringUtf8()!!
}

suspend fun main(args: Array<String>) = coroutineScope {

    // Displays all debug messages from gRPC
    org.apache.log4j.BasicConfigurator.configure()

    // Take the ID as the port number
    val id = args[0].toInt()

    // Init services
    val learnerService = LearnerService(this)
    val acceptorService = AcceptorService(id)

    // Build gRPC server
    val server = ServerBuilder.forPort(id)
        .apply {
            if (id > 0) // Apply your own logic: who should be an acceptor
                addService(acceptorService)
        }
        .apply {
            if (id > 0) // Apply your own logic: who should be a learner
                addService(learnerService)
        }
        .build()

    // Use the atomic broadcast adapter to use the learner service as an atomic broadcast service
    val atomicBroadcast = object : AtomicBroadcast<String>(learnerService, biSerializer) {
        // These are dummy implementations
        override suspend fun _send(byteString: ByteString) = throw NotImplementedError()
        override fun _deliver(byteString: ByteString) = listOf(biSerializer(byteString))
    }

    withContext(Dispatchers.IO) { // Operations that block the current thread should be in a IO context
        server.start()
    }

    // Create channels with clients
    val chans = listOf(8980, 8981, 8982).associateWith {
        ManagedChannelBuilder.forAddress("localhost", it).usePlaintext().build()!!
    }

    /*
     * Don't forget to add the list of learners to the learner service.
     * The learner service is a reliable broadcast service and needs to
     * have a connection with all processes that participate as learners
     */
    learnerService.learnerChannels = chans.filterKeys { it != id }.values.toList()

    /*
     * You Should implement an omega failure detector.
     */
    val omega = object : OmegaFailureDetector<ID> {
        override val leader: ID get() = id
        override fun addWatcher(observer: suspend () -> Unit) {}
    }

    // Create a proposer, not that the proposers' ids and
    // the acceptors' ids must be all unique (they break symmetry)
    val proposer = Proposer(
        id = id, omegaFD = omega, scope = this, acceptors = chans,
        thisLearner = learnerService,
    )

    // Starts The proposer
    proposer.start()

    startRecievingMessages(atomicBroadcast)

    // "Key press" barrier so only one proposer sends messages
    withContext(Dispatchers.IO) { // Operations that block the current thread should be in a IO context
        System.`in`.read()
    }
    startGeneratingMessages(id, proposer)
    withContext(Dispatchers.IO) { // Operations that block the current thread should be in a IO context
        server.awaitTermination()
    }
}

private fun CoroutineScope.startGeneratingMessages(
    id: Int,
    proposer: Proposer,
) {
    launch {
        println("Started Generating Messages")
        (1..100).forEach {
            delay(1000)
            val prop = "[Value no $it from $id]".toByteStringUtf8()
                .also { println("Adding Proposal ${it.toStringUtf8()!!}") }
            proposer.addProposal(prop)
        }
    }
}

private fun CoroutineScope.startRecievingMessages(atomicBroadcast: AtomicBroadcast<String>) {
    launch {
        for ((`seq#`, msg) in atomicBroadcast.stream) {
            println("Message #$`seq#`: $msg  received!")
        }
    }
}