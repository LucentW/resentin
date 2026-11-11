package pm.antani.resentin.e2e

import android.util.Log
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import pm.antani.resentin.net.dto.E2eKeysStatusDto
import pm.antani.resentin.net.dto.E2eListDto
import pm.antani.resentin.net.dto.E2eRegisterDeviceDto
import pm.antani.resentin.net.rest.E2eApi
import retrofit2.HttpException
import uniffi.vodozemac_android.Account
import uniffi.vodozemac_android.verifyEd25519

/**
 * Why grappa holds an identity this device cannot speak for (cicchetto `ElsewhereReason`):
 * no-keys (never paired or wiped), replaced (another device made a new master), left-out (the
 * list omits this device), no-list (grappa dropped the list and this device lacks the master).
 */
enum class ElsewhereReason { NO_KEYS, REPLACED, LEFT_OUT, NO_LIST }

sealed interface IdentityState {
    data object Ready : IdentityState
    data class Elsewhere(val reason: ElsewhereReason) : IdentityState
}

/** The token grappa puts in an error body (`{"error":"master_mismatch"}`), if [e] is such a refusal. */
fun apiErrorCode(e: Throwable): String? {
    if (e !is HttpException) return null
    val body = runCatching { e.response()?.errorBody()?.string() }.getOrNull() ?: return null
    return runCatching { Json.parseToJsonElement(body).jsonObject["error"]?.jsonPrimitive?.contentOrNull }.getOrNull()
}

private class Elsewhere(val reason: ElsewhereReason) : Exception(reason.name)

private val NOTHING_HELD = E2eKeysStatusDto(oneTimeKeys = 0, fallback = false)

/**
 * Sets up, or keeps up, this device's place in the identity grappa holds on [slug]
 * (port of cicchetto `identity.ts` `syncIdentity`). An identity is NEVER replaced silently:
 * when grappa holds one this device cannot speak for, the result is Elsewhere and nothing is
 * written. Only [replaceIdentity], on an explicit user action, starts over.
 */
class E2eIdentity(private val store: E2eAccountStore, private val api: () -> E2eApi) {
    private val running = HashMap<String, CompletableDeferred<IdentityState>>()
    private val guard = Mutex()

    /** A second call for a slot already syncing joins that sync instead of queueing another. */
    suspend fun sync(slot: String, slug: String): IdentityState {
        val mine = CompletableDeferred<IdentityState>()
        val inFlight = guard.withLock { running[slot] ?: run { running[slot] = mine; null } }
        if (inFlight != null) return inFlight.await()
        try {
            mine.complete(runSync(slot, slug))
        } catch (e: Throwable) {
            mine.completeExceptionally(e)
        } finally {
            guard.withLock { running.remove(slot) }
        }
        return mine.await()
    }

    private suspend fun runSync(slot: String, slug: String): IdentityState = try {
        store.withAccount(slot) { session ->
            val held = getList(slug)
            val own = session.own
            if (own == null) {
                if (held != null) throw Elsewhere(ElsewhereReason.NO_KEYS)
                try {
                    bootstrap(session, slug)
                } catch (e: HttpException) {
                    // Another device put its list between our look and our put: its identity stands.
                    throw if (apiErrorCode(e) == "master_mismatch") Elsewhere(ElsewhereReason.NO_KEYS) else e
                }
                return@withAccount
            }
            var current = own
            when {
                held == null -> current = relist(session, own, slug)
                held.master != own.master -> throw Elsewhere(ElsewhereReason.REPLACED)
                session.account.ed25519Key() !in held.devices -> throw Elsewhere(ElsewhereReason.LEFT_OUT)
                held.ver > own.list.ver -> current = adopt(session, own, held)
            }
            topUpKeys(session, slug, current.did, api().getKeys(slug, current.did))
        }
        IdentityState.Ready
    } catch (e: Elsewhere) {
        IdentityState.Elsewhere(e.reason)
    }

    /** DESTRUCTIVE: drops the whole identity on grappa and makes a new one. Explicit user action only. */
    suspend fun replaceIdentity(slot: String, slug: String) {
        store.withAccount(slot) { session ->
            api().clearKeys(slug)
            session.account = Account()
            session.master = null
            session.own = null
            // The old identity is gone from grappa: so is it here, before anything else can fail.
            session.persist()
            bootstrap(session, slug)
        }
    }

    private suspend fun getList(slug: String): E2eListDto? {
        val r = api().getList(slug)
        if (r.code() == 404) return null
        if (!r.isSuccessful) throw HttpException(r)
        return r.body()
    }

    private suspend fun bootstrap(session: AccountSession, slug: String) {
        val device = session.account
        val master = Account()
        val (did, list) = enlist(device, master, slug, 1)
        session.master = master
        session.own = OwnIdentity(
            master = master.ed25519Key(),
            cert = master.sign(certSigText(list.ver, device.ed25519Key())),
            certVer = list.ver,
            list = list,
            did = did,
        )
        topUpKeys(session, slug, did, NOTHING_HELD)
    }

    // grappa dropped the list (and its devices) while this device holds the identity: register
    // again and list one version above the last list. The certificate stays.
    private suspend fun relist(session: AccountSession, own: OwnIdentity, slug: String): OwnIdentity {
        val master = session.master ?: throw Elsewhere(ElsewhereReason.NO_LIST)
        val (did, list) = enlist(session.account, master, slug, own.list.ver + 1)
        return own.copy(list = list, did = did).also { session.own = it }
    }

    // grappa holds our master's list above the stored version, still naming this device: a relist
    // whose answer was lost. Taken once it verifies, so the next relist goes above it.
    private suspend fun adopt(session: AccountSession, own: OwnIdentity, held: E2eListDto): OwnIdentity {
        if (!verifyEd25519(held.master, listSigText(held.ver, held.devices), held.sig)) return own
        return own.copy(list = SignedList(held.ver, held.devices, held.sig)).also {
            session.own = it
            session.persist()
        }
    }

    // A device registered and never listed holds one of DEVICE_CAP rooms on grappa for good, so a
    // failed put drops it again (best effort).
    private suspend fun enlist(device: Account, master: Account, slug: String, ver: Int): Pair<String, SignedList> {
        val did = register(slug, idBlock(device, master.ed25519Key()))
        try {
            val sorted = listOf(device.ed25519Key()).sorted()
            val list = SignedList(ver, sorted, master.sign(listSigText(ver, sorted)))
            api().putList(slug, E2eListDto(master.ed25519Key(), list.ver, list.devices, list.sig))
            return did to list
        } catch (e: Throwable) {
            runCatching { api().removeDevice(slug, did) }
                .onFailure { Log.w(TAG, "could not drop the unlisted device $did on $slug", it) }
            throw e
        }
    }

    // With no list held, every device grappa holds is unlisted: an orphan. When they fill every
    // room they are dropped and the registration tried once more.
    private suspend fun register(slug: String, id: IdBlock): String = try {
        api().registerDevice(slug, E2eRegisterDeviceDto(id.ed25519, id.curve25519, id.sig)).did
    } catch (e: HttpException) {
        if (apiErrorCode(e) != "too_many_devices") throw e
        for (orphan in api().listDevices(slug).devices) {
            runCatching { api().removeDevice(slug, orphan.did) }
                .onFailure { Log.w(TAG, "could not drop the orphan device ${orphan.did} on $slug", it) }
        }
        api().registerDevice(slug, E2eRegisterDeviceDto(id.ed25519, id.curve25519, id.sig)).did
    }

    // The record is stored with the new keys BEFORE they leave, so grappa never holds a key whose
    // private half was not kept; they are marked published once grappa has them.
    private suspend fun topUpKeys(session: AccountSession, slug: String, did: String, held: E2eKeysStatusDto) {
        topUp(session.account, held)?.let {
            session.persist()
            api().uploadKeys(slug, did, it)
        }
        session.account.markKeysAsPublished()
    }

    private companion object {
        const val TAG = "E2eIdentity"
    }
}
