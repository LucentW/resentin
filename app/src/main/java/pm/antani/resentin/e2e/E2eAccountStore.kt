package pm.antani.resentin.e2e

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import java.io.File
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.Serializable
import pm.antani.resentin.net.AppJson
import uniffi.vodozemac_android.Account

/** This device's place in the identity on a network (mirrors cicchetto `OwnIdentity`). */
@Serializable
data class OwnIdentity(
    /** The master's Ed25519 key: what peers pin. */
    val master: String,
    /** `master.sign(certSigText(certVer, device ed25519))`: every PKMSG carries it. */
    val cert: String,
    /** The list version the certificate names: the first list that held this device. */
    val certVer: Int,
    /** The list the master last put on grappa, this device in it. */
    val list: SignedList,
    /** This device's did, as grappa derived it. */
    val did: String,
)

/** What is stored per slot, as vodozemac pickles (JSON), sealed as one blob. */
@Serializable
private data class Record(val device: String, val master: String? = null, val own: OwnIdentity? = null, val state: E2eState = E2eState())

/** The live view [E2eAccountStore.withAccount] hands out; the block mutates it, success persists it. */
class AccountSession internal constructor(
    var account: Account,
    var master: Account?,
    var own: OwnIdentity?,
    /** The engine's memory of peers and sessions, stored with the account in one record. */
    val state: E2eState,
    private val save: (AccountSession) -> Unit,
) {
    /** Stores the session now, before a risky step (keys are stored BEFORE they are uploaded). */
    fun persist() = save(this)
}

/** `subject:id:networkId` — one identity per subject and network. */
fun accountSlot(kind: String, id: String, networkId: Int) = "$kind:$id:$networkId"

/**
 * One AES-GCM sealed file per slot under filesDir/e2e, with a key the Android Keystore created
 * non-extractable, so a copy of the app's files alone never yields an account. A block that
 * throws stores nothing, so the slot keeps whatever it held.
 */
class E2eAccountStore(context: Context) {
    private val dir = File(context.applicationContext.filesDir, "e2e").apply { mkdirs() }
    private val locks = HashMap<String, Mutex>()

    private fun lock(slot: String) = synchronized(locks) { locks.getOrPut(slot) { Mutex() } }

    // Slot names carry ':' and could carry anything a subject id does: keep the filename inert.
    private fun file(slot: String) = File(dir, slot.replace(Regex("[^A-Za-z0-9_.-]"), "_") + ".bin")

    suspend fun <T> withAccount(slot: String, block: suspend (AccountSession) -> T): T =
        lock(slot).withLock {
            val rec = load(slot)
            val session = AccountSession(
                account = Account.fromPickle(rec.device),
                master = rec.master?.let { Account.fromPickle(it) },
                own = rec.own,
                state = rec.state,
            ) { s -> store(slot, s) }
            val result = block(session)
            store(slot, session)
            result
        }

    private fun load(slot: String): Record {
        val f = file(slot)
        return if (f.exists()) AppJson.decodeFromString(Record.serializer(), open(f.readBytes()))
        else Record(device = Account().pickle())
    }

    private fun store(slot: String, s: AccountSession) {
        val json = AppJson.encodeToString(Record.serializer(), Record(s.account.pickle(), s.master?.pickle(), s.own, s.state))
        val tmp = File(dir, file(slot).name + ".tmp")
        tmp.writeBytes(seal(json))
        check(tmp.renameTo(file(slot))) { "could not store the E2E account" } // atomic on one filesystem
    }

    /** Reads [slot] without storing anything (not even a record for a slot that has none). */
    suspend fun <T> peek(slot: String, read: (AccountSession) -> T): T = lock(slot).withLock {
        val rec = load(slot)
        read(AccountSession(Account.fromPickle(rec.device), rec.master?.let { Account.fromPickle(it) }, rec.own, rec.state) { })
    }

    fun delete(slot: String) {
        file(slot).delete()
    }

    private fun key(): SecretKey {
        val ks = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        (ks.getKey(ALIAS, null) as? SecretKey)?.let { return it }
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore").run {
            init(
                KeyGenParameterSpec.Builder(ALIAS, KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                    .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                    .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                    .setKeySize(256)
                    .build(),
            )
            generateKey()
        }
    }

    // iv (12) || ciphertext+tag
    private fun seal(text: String): ByteArray {
        val c = Cipher.getInstance("AES/GCM/NoPadding").apply { init(Cipher.ENCRYPT_MODE, key()) }
        return c.iv + c.doFinal(text.toByteArray())
    }

    private fun open(blob: ByteArray): String {
        val c = Cipher.getInstance("AES/GCM/NoPadding")
            .apply { init(Cipher.DECRYPT_MODE, key(), GCMParameterSpec(128, blob, 0, 12)) }
        return String(c.doFinal(blob, 12, blob.size - 12))
    }

    private companion object {
        const val ALIAS = "resentin_e2e_account"
    }
}
