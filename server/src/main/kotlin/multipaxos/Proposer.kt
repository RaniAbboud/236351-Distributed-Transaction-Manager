package multipaxos

import com.google.protobuf.ByteString
import cs236351.multipaxos.*
import io.grpc.ManagedChannel
import io.grpc.StatusException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext


import cs236351.multipaxos.MultiPaxosAcceptorServiceGrpcKt.MultiPaxosAcceptorServiceCoroutineStub as AcceptorGrpcStub
import cs236351.multipaxos.MultiPaxosLearnerServiceGrpcKt.MultiPaxosLearnerServiceCoroutineStub as LearnerGrpcStub

typealias ID = Int

class Proposer(
    private val id: ID,
    acceptors: Map<ID, ManagedChannel>,
    private val thisLearner: LearnerService,
    private val omegaFD: OmegaFailureDetector<Int>,
    private val scope: CoroutineScope,
    private val context: CoroutineContext = paxosThread,
    proposalCapacityBufferSize: Int = 10,
) {
    private val acceptors: Map<ID, AcceptorGrpcStub> = (acceptors.mapValues { (_, v) ->
        AcceptorGrpcStub(v)
    })

    private val leaderWaiter = object {
        var chan = Channel<Unit>(1)
        var cache = omegaFD.leader

        init {
            omegaFD.addWatcher {
                cache = omegaFD.leader
                chan.send(Unit)
            }
        }

        suspend fun waitUntilLeader() {
            do {
                if (cache == id) return
                chan.receive()
            } while (true)
        }
    }

    private val quorumWaiter: QuorumWaiter<ID, AcceptorGrpcStub> =
        MajorityQuorumWaiter(this.acceptors, scope, context)

    private val proposalsStream = Channel<ByteString>(proposalCapacityBufferSize)
    public val proposalSendStream: SendChannel<ByteString> = proposalsStream
    public suspend fun addProposal(proposal: ByteString) = proposalSendStream.send(proposal)

    public fun start() = scope.launch(context) {
        for (proposal in proposalsStream) {
            val instanceNo = thisLearner.lastInstance.get() + 1
            Instance(instanceNo, proposal).run()
        }
    }

    private inner class Instance(
        val instanceNo: Int,
        var value: ByteString,
    ) {
        internal suspend fun run() {
            while (true) {
                leaderWaiter.waitUntilLeader()
                val success = doRound()
                if (success) {
                    return
                }
            }
        }

        private var roundNo = RoundNo(1, id)
        private suspend fun doRound(): Boolean {
            roundNo++
            var (ok, v) = preparePromise()
            if (!ok) return false
            v?.let { value = it }

            ok = acceptAccepted()
            if (!ok) return false

            commit()
            return true
        }


        private suspend fun preparePromise(): Pair<Boolean, ByteString> {
            val prepareMsg = prepare {
                roundNo = this@Instance.roundNo.toProto()
                instanceNo = this@Instance.instanceNo
                value = this@Instance.value
            }
                /*.also {
                println("Proposer [$instanceNo, $roundNo]" +
                        "\tPrepare: value=\"${it.value?.toStringUtf8() ?: "null"}\"\n===")
            }*/
            val (ok, results) = quorumWaiter.waitQuorum({ (_, it) -> it.ack == Ack.YES }) {
                try {
                    this.doPrepare(prepareMsg)
                        /*.also {
                            "Proposer [$instanceNo, $roundNo]" +
                                    println("\tPromise: ${it.ack} value=\"${
                                        it.value?.let { if (it.size() == 0) it.toStringUtf8() else "null" }
                                    }\"\n\t\t lastgoodround=${RoundNo(it.goodRoundNo)}\n===")
                        }*/
                } catch (e: StatusException) {
                    null
                }
            }
            val promises = results.map { it.second }.filterNotNull()
            return Pair(ok, if (ok) maxByRoundNo(promises) else EMPTY_BYTE_STRING)
        }

        private suspend fun acceptAccepted(): Boolean {
            val acceptMsg = accept {
                roundNo = this@Instance.roundNo.toProto()
                value = this@Instance.value
                instanceNo = this@Instance.instanceNo
            }
               /* .also {
                println("Proposer [$instanceNo, $roundNo]" +
                        "\tAccept: value=\"${it.value?.let { if (it.size() == 0) it.toStringUtf8() else "null" }}\"\n===")
            }*/
            val (ok, _) = quorumWaiter.waitQuorum({ (_, it) -> it.ack == Ack.YES }) {
                try {
                    this.doAccept(acceptMsg)
                        /*.also {
                        "Proposer [$instanceNo, $roundNo]" +
                                println("\tAccepted: ${it.ack}\n===")
                    }*/
                } catch (e: StatusException) {
                    null
                }
            }
            return ok
        }

        private suspend fun commit() {
            val commitMsg = commit {
                value = this@Instance.value
                instanceNo = this@Instance.instanceNo
            }
            thisLearner.doCommit(commitMsg)
        }
    }
}

private fun maxByRoundNo(promises: List<Promise>): ByteString {
    var maxRoundNo = RoundNo(0, 0)
    var v: ByteString = EMPTY_BYTE_STRING

    for (promise in promises) {
        val roundNo = RoundNo(promise.roundNo)
        if (maxRoundNo < roundNo) {
            maxRoundNo = roundNo
            v = promise.value
        }
    }
    return v
}

