package pm.antani.resentin.e2e

import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import pm.antani.resentin.net.dto.E2eAckDto
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
) {
    /** Sends [body] as an encrypted text to [nick]; the events say what happened (queued, failed…). */
    suspend fun sendText(nick: String, body: String, tag: String): List<E2eEvent> =
        run(nick = nick) { engine ->
            engine.send(nick, buildJsonObject { put("t", "text"); put("body", body) }, tag)
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

    private suspend fun run(nick: String, step: (E2eEngine) -> Result): List<E2eEvent> =
        store.withAccount(slot) { session ->
            val own = session.own ?: return@withAccount emptyList()
            val engine = E2eEngine(session.account, own, ownNick, session.state)
            val res = step(engine)
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
        }
        session.persist()
    }
}

/** The plain text of a received [E2eEvent.Received], or null for any other inner kind. */
fun E2eEvent.Received.text(): String? =
    if ((inner["t"] as? kotlinx.serialization.json.JsonPrimitive)?.content == "text")
        (inner["body"] as? kotlinx.serialization.json.JsonPrimitive)?.content else null

