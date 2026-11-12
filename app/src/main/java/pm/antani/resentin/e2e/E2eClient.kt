package pm.antani.resentin.e2e

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pm.antani.resentin.net.dto.E2eAcceptDto
import pm.antani.resentin.net.dto.E2eAckDto
import pm.antani.resentin.net.dto.E2eAskDto
import pm.antani.resentin.net.dto.E2eRequestDto
import pm.antani.resentin.net.dto.E2eSendDto
import pm.antani.resentin.net.rest.E2eApi

/**
 * Glue between grappa's doors and the [E2eEngine] for one (subject, network) slot (port of the
 * relevant part of cicchetto `client.ts`): drains this device's inbox, feeds whole messages to the
 * engine, hands its lines to the send door, and acknowledges last.
 *
 * Delivery is AT LEAST ONCE: dedupe by inbox id (the watermark `state.inbox`, stored with what each
 * message changed, in one record). The record is stored BEFORE a line leaves for grappa and
 * before the ack, so a crash resends or re-reads, never loses a ratchet step.
 *
 * Not handled yet: `own` copies from sibling devices and channel messages (v63). They are
 * skipped past, not read.
 */
class E2eClient(
    private val store: E2eAccountStore,
    private val api: () -> E2eApi,
    private val slot: String,
    private val slug: String,
    private val ownNick: () -> String,
    /** Called with a message's tag once grappa took every line of it (not before: it may wait for keys). */
    private val onSent: (String) -> Unit = {},
) {
    /** Sends [body] as an encrypted text to [nick]; the events say what happened (queued, failed…). */
    suspend fun sendText(nick: String, body: String, tag: String): List<E2eEvent> =
        run(nick = nick) { engine, _ ->
            engine.send(nick, buildJsonObject { put("t", "text"); put("body", body) }, tag)
        }

    /**
     * Asks [peer] to start an encrypted chat (§4i). Consent, not trust: the peer sees a request and
     * accepts or refuses; only after `accepted` does the key exchange start ([sendText]).
     */
    suspend fun askToChat(peer: String): E2eRequestDto? = store.withAccount(slot) { session ->
        val own = session.own ?: return@withAccount null
        val hint = buildJsonObject { put("v", WIRE_VERSION); put("master", own.master) }.toString()
        api().ask(slug, E2eAskDto(peer, own.did, hint)).request
    }

    /** Accepts [req] (§4i) and starts the key exchange: ACCEPT goes out, then a KEYREQ for the peer. */
    suspend fun accept(req: E2eRequestDto): List<E2eEvent> = run(nick = req.peer) { engine, own ->
        api().acceptRequest(slug, req.id, E2eAcceptDto(own.did, buildJsonObject { put("v", WIRE_VERSION); put("master", own.master) }.toString()))
        engine.askKeys(req.peer)
    }

    /** The user takes the key an alarm showed: it is pinned (unverified), a SAS that ran against the old one ends, and its keys are asked for. */
    suspend fun acceptAlarm(nick: String): List<E2eEvent> = run(nick = nick) { engine, _ ->
        val alarm = engine.state.alarms[foldNick(nick)] ?: return@run Result()
        if (engine.pin(nick, alarm.presented, false) != "pinned") return@run Result()
        val verifier = E2eVerifier(engine, engine.state).also { it.repinned(nick) }
        val res = engine.askKeys(nick)
        res.events += verifier.take().events; res.outbound += verifier.take().outbound
        res
    }

    /** The user compared the fingerprints out of band: [nick]'s pinned key is marked verified. */
    suspend fun markVerified(nick: String) { run(nick = nick) { engine, _ -> engine.pinned(nick)?.let { engine.verify(nick, it.master) }; Result() } }

    /** The user keeps the old key. */
    suspend fun dismissAlarm(nick: String) { run(nick = nick) { engine, _ -> engine.dismissAlarm(nick); Result() } }

    /** The alarms standing on this network. */
    suspend fun alarms(): Map<String, AlarmRec> = store.peek(slot) { it.state.alarms.toMap() }

    /** Starts the key exchange with a peer whose consent [askToChat] got. */
    suspend fun askKeys(peer: String): List<E2eEvent> = run(nick = peer) { engine, _ -> engine.askKeys(peer) }

    /** A 401 for [nick] (§4j): nothing will answer its KEYREQ, so what waits for its keys fails now. */
    suspend fun absent(nick: String): List<E2eEvent> = run(nick = nick) { engine, _ -> engine.absent(nick) }

    /** The consent requests grappa holds for this network; a GET on every (re)connect. */
    suspend fun requests(): List<E2eRequestDto> = api().requests(slug).requests

    /** Removes an answer once shown, or an expired request. */
    suspend fun dismissRequest(id: Long) {
        api().dismissRequest(slug, id)
    }

    /** Reads everything waiting in this device's inbox, in order. The events are the app's to show. */
    suspend fun drain(): List<E2eEvent> {
        val events = mutableListOf<E2eEvent>()
        while (true) {
            val more = store.withAccount(slot) { session ->
                val own = session.own ?: return@withAccount false
                val page = api().inbox(slug, own.did, session.state.inbox).messages
                if (page.isEmpty()) return@withAccount false
                val engine = E2eEngine(session.account, own, ownNick, session.state)
                val done = mutableListOf<Long>()
                for (m in page) {
                    if (m.toDid != own.did) continue // the push goes to every client of the account
                    if (m.id > session.state.inbox && !m.own && m.channel == null) {
                        val res = engine.receive(m.from, m.verb, m.fromDid, m.msgid, m.payload)
                        routeSas(engine, res)
                        events += res.events
                        session.state.inbox = m.id
                        session.persist()
                        transmit(own, engine, res, session)
                    } else {
                        session.state.inbox = maxOf(session.state.inbox, m.id)
                    }
                    done += m.id
                }
                session.persist()
                if (done.isNotEmpty()) api().ack(slug, own.did, E2eAckDto(done.takeLast(100)))
                page.size >= 100
            }
            if (!more) return events
        }
    }

    // t:sas.* inner messages are the verifier's, not the chat's: hand them over and merge what it says back.
    private fun routeSas(engine: E2eEngine, res: Result) {
        val sas = res.events.filterIsInstance<E2eEvent.Received>().filter { (it.inner["t"] as? kotlinx.serialization.json.JsonPrimitive)?.content?.startsWith("sas.") == true }
        if (sas.isEmpty()) return
        val verifier = E2eVerifier(engine, engine.state)
        res.events.removeAll(sas.toSet())
        sas.forEach { verifier.receive(it.nick, it.master, it.device, it.inner) }
        val more = verifier.take()
        res.events += more.events
        res.outbound += more.outbound
    }

    /** The user's move in a verification (start | accept | match | mismatch | cancel). */
    suspend fun sasStep(nick: String, step: String): List<E2eEvent> = run(nick = nick) { engine, _ ->
        E2eVerifier(engine, engine.state).also { it.step(nick, step) }.take()
    }

    /** Ends what timed out, or what only a previous run of the app could have finished. */
    suspend fun sasTick(): List<E2eEvent> = run(nick = "") { engine, _ ->
        E2eVerifier(engine, engine.state).also { it.tick() }.take()
    }

    /** What the UI shows of the verification with [nick], if one runs. */
    suspend fun sasView(nick: String): SasView? = store.withAccount(slot) { session ->
        val own = session.own ?: return@withAccount null
        E2eVerifier(E2eEngine(session.account, own, ownNick, session.state), session.state).view(nick)
    }

    private suspend fun run(nick: String, step: suspend (E2eEngine, OwnIdentity) -> Result): List<E2eEvent> =
        store.withAccount(slot) { session ->
            val own = session.own ?: return@withAccount emptyList()
            val engine = E2eEngine(session.account, own, ownNick, session.state)
            val res = step(engine, own)
            session.persist()
            transmit(own, engine, res, session)
            res.events
        }

    // Hands every line to the send door, recording the msgid grappa names so a NACK finds it. A
    // refusal (413, a full queue) is an answer the user must see, not a hiccup to retry blindly.
    private suspend fun transmit(own: OwnIdentity, engine: E2eEngine, res: Result, session: AccountSession) {
        for (o in res.outbound) {
            val sent = api().send(slug, E2eSendDto(o.target, o.verb, o.from, o.to, o.payload, o.priority))
            o.ref?.let { engine.sent(it, sent.msgid) }
            o.tag?.let(onSent)
        }
        session.persist()
    }
}

/** The plain text of a received [E2eEvent.Received], or null for any other inner kind. */
fun E2eEvent.Received.text(): String? =
    if ((inner["t"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "text")
        (inner["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content else null

