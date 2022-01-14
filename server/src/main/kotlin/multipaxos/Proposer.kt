package multipaxos

import com.google.protobuf.ByteString
import com.google.protobuf.Empty
import com.google.protobuf.empty
import cs236351.multipaxos.*
import io.grpc.ManagedChannel
import io.grpc.StatusException
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.SendChannel
import kotlin.coroutines.CoroutineContext


import cs236351.multipaxos.MultiPaxosProposerServiceGrpcKt.MultiPaxosProposerServiceCoroutineImplBase as ProposerGrpcImplBase
import cs236351.multipaxos.MultiPaxosAcceptorServiceGrpcKt.MultiPaxosAcceptorServiceCoroutineStub as AcceptorGrpcStub

class Proposer(
    private val id: String,
    acceptors: Map<String, ManagedChannel>,
    private val thisLearner: LearnerService,
    private val omegaFD: OmegaFailureDetector,
    private val scope: CoroutineScope,
    context: CoroutineContext = paxosThread,
    proposalCapacityBufferSize: Int = 10,
) : ProposerGrpcImplBase(context) {
    private val acceptors: Map<Int, AcceptorGrpcStub> = (
            acceptors.mapValues { (_, v) -> AcceptorGrpcStub(v) }
                .mapKeys { (k,_) -> k.toInt()} )

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

        fun isLeader(): Boolean {
            return (cache == id)
        }
    }

    private val quorumWaiter: QuorumWaiter<Int, AcceptorGrpcStub> =
        MajorityQuorumWaiter(this.acceptors, scope, context)

    private val proposalsStream = Channel<Pair<ByteString, Channel<ProposeResponse>>>(proposalCapacityBufferSize)
    val proposalSendStream: SendChannel<Pair<ByteString, Channel<ProposeResponse>>> = proposalsStream
    suspend fun addProposal(proposal: Pair<ByteString, Channel<ProposeResponse>>) = proposalSendStream.send(proposal)

    override suspend fun doPropose(request: Propose): ProposeResponse {
        if (MULTIPAXOS_DEBUG) println("Called on ${request.value}")
        var chan: Channel<ProposeResponse> = Channel<ProposeResponse>(1)
        addProposal(Pair(request.value, chan))
        // Need to wait until it is actually committed before returning.
        // Need this so requester can identify failures and find the new leader
        var ret = chan.receive()
        if (MULTIPAXOS_DEBUG) println("Done on ${request.value}. Returning ${ret.ack}")
        return ret
    }

    public fun start() = scope.launch(context) {
        for ((proposal, chan) in proposalsStream) {
            val instanceNo = thisLearner.lastInstance.get() + 1
            Instance(instanceNo, proposal, proposal, chan).run()
        }
    }

    private inner class Instance(
        val instanceNo: Int,
        var value: ByteString,
        var origValue: ByteString,
        var origChannel : Channel<ProposeResponse>
    ) {
        internal suspend fun run() {
            while (true) {
                if (MULTIPAXOS_DEBUG) println("Inside run on ${value}")
                if (leaderWaiter.isLeader()) {
                    if (MULTIPAXOS_DEBUG) println("I am leader!")
                    val success = doRound()
                    if (success) {
                        return
                    }
                } else {
                    if (MULTIPAXOS_DEBUG) println("I am not leader!")
                    if (value == origValue)
                        origChannel.send(proposeResponse {Ack.NO})
                    return
                }
            }
        }

        private var roundNo = RoundNo(1, id.toInt())
        private suspend fun doRound(): Boolean {
            roundNo++
            var (ok, v) = preparePromise()
            if (!ok) return false

            // FIXME - Sajy - See if this is the correct way to do it
            // v?.let { value = it }
            if (!v.equals(EMPTY_BYTE_STRING)) {
                value = v
                if (!v.equals(origValue)) {
                    addProposal(Pair(origValue, origChannel))
                }
            }

            ok = acceptAccepted()
            if (!ok) return false

            commit()
            if (value == origValue)
                origChannel.send(proposeResponse {Ack.YES})
            return true
        }


        private suspend fun preparePromise(): Pair<Boolean, ByteString> {
            val prepareMsg = prepare {
                roundNo = this@Instance.roundNo.toProto()
                instanceNo = this@Instance.instanceNo
                value = this@Instance.value
            }
                .also {
                    if (MULTIPAXOS_DEBUG)
                        println("Proposer [$instanceNo, $roundNo]" +
                                "\tPrepare: value=\"${it.value?.toStringUtf8() ?: "null"}\"\n===")
                }
            val (ok, results) = quorumWaiter.waitQuorum({ (_, it) -> it.ack == Ack.YES }) {
                try {
                    this.doPrepare(prepareMsg)
                        .also {
                            if (MULTIPAXOS_DEBUG)
                                "Proposer [$instanceNo, $roundNo]" +
                                        println("\tPromise: ${it.ack} value=\"${
                                            it.value?.let { if (it.size() == 0) it.toStringUtf8() else "null" }
                                        }\"\n\t\t lastgoodround=${RoundNo(it.goodRoundNo)}\n===")
                        }
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
                .also {
                    if (MULTIPAXOS_DEBUG)
                        println("Proposer [$instanceNo, $roundNo]" +
                                "\tAccept: value=\"${it.value?.let { if (it.size() == 0) it.toStringUtf8() else "null" }}\"\n===")
                }
            val (ok, _) = quorumWaiter.waitQuorum({ (_, it) -> it.ack == Ack.YES }) {
                try {
                    this.doAccept(acceptMsg)
                        .also {
                            if (MULTIPAXOS_DEBUG)
                                "Proposer [$instanceNo, $roundNo]" +
                                        println("\tAccepted: ${it.ack}\n===")
                        }
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