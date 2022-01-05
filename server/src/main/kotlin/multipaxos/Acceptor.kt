package multipaxos

import com.google.protobuf.ByteString
import cs236351.multipaxos.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap

import cs236351.multipaxos.MultiPaxosAcceptorServiceGrpcKt.MultiPaxosAcceptorServiceCoroutineImplBase as AcceptorGrpcImplBase

internal val EMPTY_BYTE_STRING = ByteString.copyFrom(ByteArray(0))

class AcceptorService(
    private val id: Int,
) : AcceptorGrpcImplBase(paxosThread) {

    private class Instance(val instanceNo: Int, acceptorID: Int) {
        val mutex = Mutex()
        var lastRound: RoundNo = RoundNo(0, acceptorID)
        var lastGoodRound: RoundNo = RoundNo(0, acceptorID)
        var value: ByteString = EMPTY_BYTE_STRING
    }

    private val instances: ConcurrentMap<Int, Instance> = ConcurrentHashMap()
    private suspend fun <T> withInstance(no: Int, block: suspend Instance.() -> T): T {
        val instance = instances.computeIfAbsent(no) {
            Instance(instanceNo = it, acceptorID = this.id)
        }!!
        return instance.mutex.withLock { instance.block() }
    }

    override suspend fun doPrepare(request: Prepare): Promise {
        val roundNo = RoundNo(request.roundNo)
        val instanceNo = request.instanceNo
        fun Instance.makePromise(`ack'`: Ack, `value'`: ByteString = EMPTY_BYTE_STRING) = promise {
            this.roundNo = roundNo.toProto()
            this.goodRoundNo = lastGoodRound.toProto()
            this.ack = `ack'`
            this.value = `value'`
            this.instanceNo = instanceNo
        }

        val promise = withInstance(instanceNo) {
            if (lastRound == 0 as Any) {
                value = request.value!!
            }
            if (roundNo > lastRound) {
                lastRound = roundNo
                makePromise(Ack.YES, value)
            } else {
                makePromise(Ack.NO)
            }
        }
        return promise
        /*.also {
            println("Acceptor [$instanceNo, $roundNo]\n" +
                    "\tPrepare: value=\"${request.value?.toStringUtf8() ?: "null"}\"\n" +
                    "\tPromise: ${promise.ack.toString()} value=\"${
                        promise.value.let { if (it.size() == 0) it.toStringUtf8() else "null" }
                    }\" lastgoodround=${RoundNo(promise.goodRoundNo)}\n===")
        }*/
    }

    override suspend fun doAccept(request: Accept): Accepted {
        val roundNo = RoundNo(request.roundNo)
        val instanceNo = request.instanceNo
        val ack = withInstance(request.instanceNo) {
            if (roundNo >= lastRound || lastRound == 0 as Any) {
                lastRound = roundNo
                lastGoodRound = roundNo
                value = request.value
                Ack.YES
            } else {
                Ack.NO
            }
        }
        return accepted {
            this.ack = ack
            this.roundNo = roundNo.toProto()
            this.instanceNo = instanceNo
        }
        /*.also {
            println("Acceptor [$instanceNo, $roundNo]\n" +
                    "\tAccept: value=\"${request.value.let { if (it.size() == 0) it.toStringUtf8() else "null" }}\"\n" +
                    "\tAccepted: ${ack}\n===")
        }*/
    }
}

