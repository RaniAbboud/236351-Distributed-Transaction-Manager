package multipaxos

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import com.google.protobuf.empty
import cs236351.multipaxos.Commit

import io.grpc.ManagedChannel
import io.grpc.StatusException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentMap
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import kotlin.coroutines.CoroutineContext

import cs236351.multipaxos.MultiPaxosLearnerServiceGrpcKt.MultiPaxosLearnerServiceCoroutineImplBase as LearnerGrpcImplBase
import cs236351.multipaxos.MultiPaxosLearnerServiceGrpcKt.MultiPaxosLearnerServiceCoroutineStub as LearnerGrpcStub

class LearnerService(
    private val scope: CoroutineScope,
    context: CoroutineContext = paxosThread,
) : LearnerGrpcImplBase(context) {

    val lastInstance = AtomicInteger(0)

    val observers: MutableList<suspend (Int, ByteString) -> Unit> = mutableListOf()
    private fun notifyObservers(instanceNo: Int, value: ByteString) {
        scope.launch(context) {
            for (ob in observers) {
                ob(instanceNo, value)
            }
        }
    }

    private var learners: List<LearnerGrpcStub> = emptyList()
    var learnerChannels: List<ManagedChannel>
        get() = throw NotImplementedError()
        set(value) {
            learners = value.map { LearnerGrpcStub(it) }
        }

    private fun `reliable broadcast`(m: Commit) {
        scope.launch(context) {
            learners.map {
                async(context) {
                    try {
                        it.doCommit(m)
                    } catch (e: StatusException) {
                    }
                }
            }.awaitAll()
        }
    }

    private class Instance(
        val instanceNo: Int,
        var value: ByteString = EMPTY_BYTE_STRING,
    ) {
        private val _decisionStatus = AtomicBoolean(false)
        fun decideAndTest() = _decisionStatus.compareAndExchange(false, true)
    }

    private val instances: ConcurrentMap<Int, Instance> = ConcurrentHashMap()
    private fun getInstance(no: Int) = instances.computeIfAbsent(no) { Instance(it) }!!


    override suspend fun doCommit(request: Commit): Empty {
        val instanceNo = request.instanceNo
        lastInstance.updateAndGet { if (it < instanceNo) instanceNo else it }
        return getInstance(instanceNo).run {
            val decided = decideAndTest()
            if (!decided) {
                value = request.value
                `reliable broadcast`(request)
                notifyObservers(instanceNo, value)
            }

            return@run empty { }
        }
    }


}