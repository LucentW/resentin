package pm.antani.resentin.e2e

import android.util.Log
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import pm.antani.resentin.net.dto.PairingMessageDto
import pm.antani.resentin.net.dto.PairingPostDto
import pm.antani.resentin.net.dto.PairingRendezvousDto
import pm.antani.resentin.net.rest.E2ePairingApi
import retrofit2.HttpException
import uniffi.vodozemac_android.Ecies
import uniffi.vodozemac_android.EstablishedEcies

// Pairing two devices of one account (CLIENT_PROTOCOL §4g; port of cicchetto `pairing.ts` and
// `pairingSession.ts`). The device that holds the identity (the HOLDER) opens a rendezvous and offers
// it; the new device (the JOINER) joins it and speaks first. Both sides encrypt the channel with
// ECIES between themselves; the 2-digit check code both screens show is what defeats a relay in the
// middle. Nothing is signed before both users confirmed: the holder by typing the code, the joiner
// by its tap. Only public keys and signatures cross.

data class PairCode(val id: String, val curve: String)
data class NetworkResult(val network: String, val outcome: String)

/** One live rendezvous: ordered delivery of what the OTHER side posted, and the doors to post and close. */
class PairingChannel(
    private val api: E2ePairingApi,
    val rendezvous: PairingRendezvousDto,
    private val onMessage: suspend (String) -> Unit,
    private val onClosed: suspend (String) -> Unit,
) {
    private var lastSeq = 0
    private var open = true
    private val mutex = Mutex()

    val role get() = rendezvous.role

    suspend fun send(payload: String) {
        api.post(rendezvous.id, PairingPostDto(payload))
    }

    /** Fetches what the other side posted since [lastSeq]; a 404 means the rendezvous ended while we missed the push. */
    suspend fun catchUp() = mutex.withLock {
        if (!open) return
        val r = api.messages(rendezvous.id, lastSeq)
        if (r.code() == 404) return end("gone")
        if (!r.isSuccessful) throw HttpException(r)
        for (m in r.body()?.messages.orEmpty().sortedBy { it.seq }) catching(m)
    }

    private suspend fun catching(m: PairingMessageDto) {
        if (!open || m.seq <= lastSeq) return
        lastSeq = m.seq
        onMessage(m.payload)
    }

    suspend fun end(reason: String) {
        if (!open) return
        open = false
        onClosed(reason)
    }

    suspend fun close() {
        if (!open) return
        open = false
        runCatching { api.close(rendezvous.id) }.onFailure { Log.w("E2ePairing", "close failed on ${rendezvous.id}", it) }
    }
}

enum class HolderStage { WAITING, CODE, OFFERED, SIGNING, DONE, ENDED }
enum class JoinerStage { JOINING, CODE, CONFIRM, JOINING_NETWORKS, DONE, ENDED }

data class HolderState(
    val stage: HolderStage = HolderStage.WAITING,
    val offered: List<String> = emptyList(),
    val full: List<String> = emptyList(),
    val notMaster: List<String> = emptyList(),
    val results: List<NetworkResult> = emptyList(),
    /** closed | expired | replaced | gone | code-mismatch | nothing-to-offer | failed */
    val ended: String? = null,
)

data class JoinerState(
    val stage: JoinerStage = JoinerStage.JOINING,
    val checkCode: String? = null,
    val offered: List<String> = emptyList(),
    val full: List<String> = emptyList(),
    val results: List<NetworkResult> = emptyList(),
    /** closed | expired | replaced | gone | nothing-to-pair | failed */
    val ended: String? = null,
)

private fun pad2(n: Int) = n.toString().padStart(2, '0')
private const val TAG = "E2ePairing"

private suspend fun link(ch: PairingChannel?, link: EstablishedEcies?, m: PairMsg) {
    check(ch != null && link != null) { "the pairing channel is not open" }
    ch.send(link.encrypt(encodePairMessage(m)))
}

private fun read(role: String, link: EstablishedEcies?, payload: String): PairMsg? {
    if (link == null) return null
    val bytes = try { link.decrypt(payload) } catch (e: Exception) {
        Log.w(TAG, "pairing ($role): a payload that does not decrypt"); return null
    }
    return decodePairMessage(bytes).also { if (it == null) Log.w(TAG, "pairing ($role): a payload that is no pairing message") }
}

/** (slug, slot) of every network this account has, for the identity doors. */
typealias PairNetworks = suspend () -> List<Pair<String, String>>

/** The device that holds the identity. [curve] is what the rendezvous is opened with (the offer). */
class HolderPairing(private val identity: E2eIdentity, private val networks: PairNetworks, private val scope: CoroutineScope) {
    private var ecies: Ecies? = Ecies(PAIRING_INFO)
    val curve: String = ecies!!.publicKey()
    private val _state = MutableStateFlow(HolderState())
    val state: StateFlow<HolderState> = _state

    private var step = "waiting" // waiting code judging offered adding signing over
    private var channel: PairingChannel? = null
    private var link: EstablishedEcies? = null
    private val offered = LinkedHashMap<String, String>() // slug -> slot
    private val shown = mutableListOf<String>()
    private val outcomes = HashMap<String, String>()
    private val mu = Mutex()

    private fun results() = shown.mapNotNull { n -> outcomes[n]?.let { NetworkResult(n, it) } }
    private fun set(f: (HolderState) -> HolderState) = _state.update(f)

    private suspend fun over(patch: (HolderState) -> HolderState) {
        if (step == "over") return
        step = "over"
        set(patch)
        channel?.close()
        ecies?.close(); link?.close(); ecies = null; link = null
    }

    private suspend fun end(ended: String) {
        if (step == "signing") over { it.copy(stage = HolderStage.DONE, ended = ended) }
        else over { it.copy(stage = HolderStage.ENDED, ended = ended, results = if (step == "adding") results() else emptyList()) }
    }

    fun attach(ch: PairingChannel) {
        channel = ch
        if (step == "over") scope.launch { ch.close() }
    }

    fun onPayload(payload: String) { scope.launch { mu.withLock { handle(payload) } } }
    fun onClosed(reason: String) { scope.launch { mu.withLock { end(reason) } } }
    fun cancel() { scope.launch { mu.withLock { end("closed") } } }

    /** The user typed the code the new device shows. A mismatch ends the pairing: nothing is offered. */
    fun submitCode(code: String) {
        scope.launch {
            mu.withLock {
                val l = link
                if (step != "code" || l == null) return@withLock
                if (code != pad2(l.checkCode().toInt())) return@withLock end("code-mismatch")
                step = "judging"
                runStep { offer() }
            }
        }
    }

    private suspend fun runStep(block: suspend () -> Unit) {
        try { block() } catch (e: Exception) { Log.w(TAG, "a pairing step failed", e); if (step != "signing") end("failed") }
    }

    private suspend fun handle(payload: String) {
        if (step == "over") return
        if (step == "waiting") return hello(payload)
        val m = read("holder", link, payload)
        if (step == "offered" && m is PairMsg.Join) return runStep { signIn(m) }
        if (step == "signing" && m is PairMsg.Done) {
            for ((n, o) in outcomes.toMap()) if (o == "added" && n !in m.paired) { Log.w(TAG, "the new device did not take its place on $n"); outcomes[n] = PairRefusal.FAILED }
            return over { it.copy(stage = HolderStage.DONE, results = results()) }
        }
        end("failed")
    }

    private suspend fun hello(payload: String) {
        val initial = unwrapInitial(payload)
        val once = ecies
        if (initial != null && once != null) {
            ecies = null // used up by the attempt, whatever comes of it
            try {
                val inbound = once.establishInboundChannel(initial)
                if (decodePairMessage(inbound.plaintext) == PairMsg.Hello) link = inbound.channel
            } catch (_: Exception) { }
        }
        if (link != null) { step = "code"; set { it.copy(stage = HolderStage.CODE) }; return }
        Log.w(TAG, "a first payload that is no hello to this pairing's key")
        end("failed")
    }

    private suspend fun offer() {
        val nets = mutableListOf<Pair<String, String>>()
        val full = mutableListOf<String>(); val notMaster = mutableListOf<String>()
        for ((slug, slot) in networks()) {
            val judged = try { identity.offerable(slot, slug) } catch (e: Exception) { Log.w(TAG, "judging $slug failed", e); null }
            if (step != "judging") return
            when (judged) {
                E2eIdentity.Offerable.NotMaster -> notMaster += slug
                E2eIdentity.Offerable.Full -> full += slug
                null -> { shown += slug; outcomes[slug] = PairRefusal.FAILED }
                is E2eIdentity.Offerable.Master -> { shown += slug; offered[slug] = slot; nets += slug to judged.key }
            }
        }
        if (nets.isEmpty()) return over { it.copy(stage = HolderStage.ENDED, ended = "nothing-to-offer", full = full, notMaster = notMaster, results = results()) }
        step = "offered"
        set { it.copy(stage = HolderStage.OFFERED, offered = offered.keys.toList(), full = full, notMaster = notMaster) }
        link(channel, link, PairMsg.Offer(nets, full))
    }

    private suspend fun signIn(join: PairMsg.Join) {
        step = "adding"
        set { it.copy(stage = HolderStage.SIGNING) }
        val ids = LinkedHashMap<String, IdBlock>()
        val refused = mutableListOf<Refused>()
        for ((n, id) in join.devices) {
            if (n !in offered || n in ids) refused += Refused(n, PairRefusal.FAILED) else ids[n] = id
        }
        val skipped = join.skipped.associate { it.network to it.reason }
        for (slug in offered.keys) if (slug !in ids) outcomes[slug] = skipped[slug] ?: PairRefusal.FAILED
        val devices = mutableListOf<SignedDevice>()
        for ((slug, slot) in offered) {
            val id = ids[slug] ?: continue
            val added = try { identity.addDevice(slot, slug, id) } catch (e: Exception) { Log.w(TAG, "adding the device on $slug failed", e); E2eIdentity.AddResult.Refused(PairRefusal.FAILED) }
            if (step != "adding") return
            when (added) {
                is E2eIdentity.AddResult.Added -> { devices += SignedDevice(slug, added.cert, added.list); outcomes[slug] = "added" }
                is E2eIdentity.AddResult.Refused -> { refused += Refused(slug, added.reason); outcomes[slug] = added.reason }
                E2eIdentity.AddResult.Stale -> error("addDevice never answers Stale")
            }
        }
        step = "signing"
        set { it.copy(stage = HolderStage.SIGNING, results = results()) }
        link(channel, link, PairMsg.Signed(devices, refused))
    }
}

/** The new device. Joins the offered rendezvous, shows the check code, and takes its place on the networks the user confirms. */
class JoinerPairing(
    private val identity: E2eIdentity,
    private val networks: PairNetworks,
    private val code: PairCode,
    private val scope: CoroutineScope,
    private val paired: (slug: String) -> Unit = {},
) {
    private val _state = MutableStateFlow(JoinerState())
    val state: StateFlow<JoinerState> = _state

    private var step = "joining" // joining code confirm registering joined completing over
    private var channel: PairingChannel? = null
    private var link: EstablishedEcies? = null
    private val offered = LinkedHashMap<String, String>() // slug -> master
    private class Registered(val slot: String, val master: String, val did: String)
    private val registered = LinkedHashMap<String, Registered>()
    private val outcomes = HashMap<String, String>()
    private val mu = Mutex()

    private fun results() = offered.keys.map { NetworkResult(it, outcomes[it] ?: PairRefusal.FAILED) }
    private fun set(f: (JoinerState) -> JoinerState) = _state.update(f)

    private suspend fun abandon() {
        val left = registered.toMap(); registered.clear()
        for ((slug, r) in left) identity.abandonJoin(slug, r.did)
    }

    private suspend fun end(ended: String) {
        if (step == "completing" || step == "over") return
        step = "over"
        set { it.copy(stage = JoinerStage.ENDED, ended = ended) }
        channel?.close()
        link?.close(); link = null
        abandon()
    }

    fun attach(ch: PairingChannel) {
        channel = ch
        scope.launch { mu.withLock { if (step == "over") ch.close() else if (step == "joining") open(ch) } }
    }

    fun onPayload(payload: String) { scope.launch { mu.withLock { handle(payload) } } }
    fun onClosed(reason: String) { scope.launch { mu.withLock { end(reason) } } }
    fun cancel() { scope.launch { mu.withLock { end("closed") } } }

    /** The user tapped "Pair this device": register on each offered network, then tell the holder. */
    fun confirm() {
        scope.launch {
            mu.withLock {
                if (step != "confirm") return@withLock
                step = "registering"
                set { it.copy(stage = JoinerStage.JOINING_NETWORKS) }
                try { join() } catch (e: Exception) { Log.w(TAG, "a pairing step failed", e); end("failed") }
            }
        }
    }

    private suspend fun open(ch: PairingChannel) {
        var inbound: uniffi.vodozemac_android.EciesOutbound? = null
        try {
            inbound = Ecies(PAIRING_INFO).establishOutboundChannel(code.curve, encodePairMessage(PairMsg.Hello))
        } catch (e: Exception) { Log.w(TAG, "no channel opens to the offered key", e) }
        link = inbound?.channel
        val initial = inbound?.message
        val l = link
        if (initial == null || l == null) return end("failed")
        step = "code"
        set { it.copy(stage = JoinerStage.CODE, checkCode = pad2(l.checkCode().toInt())) }
        try { ch.send(wrapInitial(initial)) } catch (e: Exception) { Log.w(TAG, "sending the hello failed", e); end("failed") }
    }

    private suspend fun handle(payload: String) {
        if (step == "completing" || step == "over") return
        val m = read("joiner", link, payload)
        if (step == "code" && m is PairMsg.Offer) {
            for ((n, master) in m.networks) offered.putIfAbsent(n, master)
            if (offered.isEmpty()) { set { it.copy(full = m.full) }; return end("nothing-to-pair") }
            step = "confirm"
            set { it.copy(stage = JoinerStage.CONFIRM, offered = offered.keys.toList(), full = m.full) }
            return
        }
        if (step == "joined" && m is PairMsg.Signed) {
            return try { complete(m) } catch (e: Exception) { Log.w(TAG, "a pairing step failed", e); end("failed") }
        }
        end("failed")
    }

    private suspend fun join() {
        val devices = mutableListOf<Pair<String, IdBlock>>()
        val skipped = mutableListOf<Refused>()
        val known = networks().toMap()
        for ((slug, master) in offered) {
            val slot = known[slug]
            if (slot == null) { Log.w(TAG, "$slug was offered and is not among this device's networks"); skipped += Refused(slug, PairRefusal.FAILED); outcomes[slug] = PairRefusal.FAILED; continue }
            val prepared = try { identity.prepareJoin(slot, slug, master) } catch (e: Exception) { Log.w(TAG, "registering on $slug failed", e); E2eIdentity.PrepareResult.Refused(PairRefusal.FAILED) }
            if (prepared is E2eIdentity.PrepareResult.Prepared) registered[slug] = Registered(slot, master, prepared.did)
            if (step != "registering") return abandon()
            when (prepared) {
                is E2eIdentity.PrepareResult.Refused -> { skipped += Refused(slug, prepared.reason); outcomes[slug] = prepared.reason }
                is E2eIdentity.PrepareResult.Prepared -> devices += slug to prepared.id
            }
        }
        step = "joined"
        link(channel, link, PairMsg.Join(devices, skipped))
    }

    private suspend fun complete(signed: PairMsg.Signed) {
        step = "completing"
        try {
            val pairedNets = mutableListOf<String>()
            for ((slug, r) in registered.toMap()) {
                val entry = signed.devices.firstOrNull { it.network == slug }
                val placed = entry != null && runCatching { identity.completeJoin(r.slot, slug, r.master, entry.cert, entry.list) }
                    .onFailure { Log.w(TAG, "completing the join on $slug failed", it) }.getOrDefault(false)
                if (placed) {
                    outcomes[slug] = "added"; pairedNets += slug
                    runCatching { paired(slug) }
                } else {
                    outcomes[slug] = if (entry != null) PairRefusal.FAILED else signed.refused.firstOrNull { it.network == slug }?.reason ?: PairRefusal.FAILED
                    identity.abandonJoin(slug, r.did)
                }
            }
            registered.clear()
            link(channel, link, PairMsg.Done(pairedNets))
        } finally {
            step = "over"
            set { it.copy(stage = JoinerStage.DONE, results = results()) }
            link?.close(); link = null
        }
    }
}
