package multipaxos

import com.google.protobuf.ByteString
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.*

data class TotallyOrderedMessage<T>(
    val sequenceNo: Int,
    val message: T,
)

abstract class AtomicBroadcast<T>(
    protected val learner: LearnerService,
    public val biSerializer: ByteStringBiSerializer<T>,
    receiveCapacityBufferSize: Int = 100,
) {
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


    init {
        learner.observers += { seq, serialized ->
            messageBuffer.buffer(seq, serialized)
            messageBuffer.deliverAll()
        }
    }

    public suspend fun send(msg: T) {
        this._send(biSerializer(msg))
    }

    public suspend fun receive(): TotallyOrderedMessage<T> = stream.receive()

    // ToDo for Students:
    // This function should send the message to an orderer / sequencer
    // The orderer / sequencer should invoke a MultiPaxos instance
    //
    // Note that you need to check for the delivery of the message, since the
    // orderer / sequencer might crash. In such case, be sure to retransmit the message
    // to the new sequencer (also make sure it is not received twice)
    //
    // Do not send an empty byte string, it will be ignored by MultiPaxos
    abstract suspend fun _send(byteString: ByteString)

    // ToDo for Students:
    // This function should reconstruct the object(s) for delivery from the
    // binary string that was returned from a MultiPaxos Instance
    //
    // Do not send an empty byte string, it will be ignored by MultiPaxos,
    // and you will not receive it here
    abstract fun _deliver(byteString: ByteString): List<T>
}
