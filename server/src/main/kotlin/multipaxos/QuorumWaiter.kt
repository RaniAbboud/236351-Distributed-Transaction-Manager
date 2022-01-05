package multipaxos

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import java.util.function.Predicate
import kotlin.coroutines.CoroutineContext

internal data class QuorumWait<ID, T>(
    val result: Boolean,
    val results: List<Pair<ID, T?>>,
)

internal interface QuorumWaiter<ID, Stub> {
    suspend fun <T> waitQuorum(
        predicate: Predicate<Pair<ID, T>>,
        func: suspend Stub.() -> T?,
    ): QuorumWait<ID, T>
}

internal class MajorityQuorumWaiter<ID, Stub>(
    private val nodes: Map<ID, Stub>,
    private val scope: CoroutineScope,
    private val context: CoroutineContext = paxosThread,
) : QuorumWaiter<ID, Stub> {
    private val majoritySize = nodes.size / 2 + 1

    override suspend fun <T> waitQuorum(
        predicate: Predicate<Pair<ID, T>>,
        func: suspend Stub.() -> T?,
    ): QuorumWait<ID, T> {
        val chan = Channel<Pair<ID, T?>>(nodes.size)
        for ((id, stub) in nodes) {
            scope.launch(context) {
                val r = stub.func()
                chan.send(Pair(id, r))
            }
        }

        val list = mutableListOf<Pair<ID, T?>>()
        var count = 0
        var okCount = 0
        while (count < nodes.size) {
            val pair = chan.receive()
            list.add(pair)

            val (id, t) = pair
            if (t != null && predicate.test(Pair(id, t))) {
                okCount++
                if (okCount >= majoritySize) break
            }

        }

        scope.launch(context) {
            while (count < nodes.size) {
                chan.receive()
                count++
            }
            chan.close()
        }
        return QuorumWait(okCount >= majoritySize, list)
    }
}

