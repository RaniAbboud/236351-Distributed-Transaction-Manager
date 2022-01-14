package multipaxos

import cs236351.multipaxos.roundNo
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.asExecutor
import kotlinx.coroutines.newSingleThreadContext

data class RoundNo(private var no: Int, private var id: Int) {
    constructor(r: cs236351.multipaxos.RoundNo) : this(r.no, r.id)

    operator fun inc(): RoundNo {
        no++
        return this
    }

    fun toProto(): cs236351.multipaxos.RoundNo = roundNo {
        id = this@RoundNo.id
        no = this@RoundNo.no
    }

    operator fun compareTo(lastRound: RoundNo): Int = when {
        no < lastRound.no -> -1
        no == lastRound.no -> id - lastRound.id
        else -> 1
    }

    override operator fun equals(other: Any?) = when (other) {
        is RoundNo -> this.no == other.no && this.id == other.id
        is Int -> this.no == other
        else -> throw NotImplementedError()
    }

    override fun toString() = "($no,$id)"
}

internal val paxosThread = newSingleThreadContext(
    name = "MultiPaxosThread")
    .asExecutor()
    .asCoroutineDispatcher()

interface OmegaFailureDetector {
    // This property should return the value immediately
    val leader: String

    // Notifies each time when the leader has change
    // by invoking the function argument
    fun addWatcher(observer: suspend () -> Unit)

}

// Debug for multipaxos kotlin code
val MULTIPAXOS_DEBUG : Boolean = false
