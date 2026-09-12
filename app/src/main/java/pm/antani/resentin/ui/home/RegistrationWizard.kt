package pm.antani.resentin.ui.home

// Guided NickServ registration wizard - cicchetto #349 parity.
//
// Six steps for one network:
//   1. INTRO    - "why register matters" copy.
//   2. EMAIL    - collect + format-validate the email.
//   3. PASSWORD - collect + length-validate the password.
//   4. REGISTER - send buildRegister(pw, email) to NickServ, mirror the raw
//                 NOTICE replies; USER-advanced (+ a timeout guard), because
//                 REGISTER has no structural success terminator.
//   5. CODE     - collect the emailed confirmation code.
//   6. VERIFY   - send buildVerify(nick, code), mirror replies, and
//                 auto-complete when the server's normalized identity verdict
//                 (session_identity_changed -> identified) flips - the same
//                 signal that hides the launcher. No optimistic success, no
//                 client-side parse of NickServ text.
//
// SECURITY: email + password live in the wizard state for the dialog's
// lifetime ONLY - closing drops the whole state. They are never logged and
// never channel-echoed: the REGISTER send goes wire-only via sendServiceMessage
// (the server's services-target path persists nothing). The credential save
// itself is server-side (commit-on-+r): this client sends the verbs and mirrors
// the verdict, nothing more.

enum class WizardStep {
    INTRO,
    EMAIL,
    PASSWORD,
    REGISTER,
    CODE,
    VERIFY
}

// Per-flavor NickServ REGISTER / verify command bodies (PRIVMSG payloads).
// Single source of truth so the dialog never open-codes a command string.
// Field docs: servicesNick is the nick the wizard messages + mirrors replies
// from; buildRegister takes (password, email) - password FIRST, the ordering
// is load-bearing (azzurra/services source-verified); buildVerify takes
// (nick, code) - nick is kept for future flavors needing it in the verb,
// Azzurra AUTH takes the code alone.
data class RegistrationTemplate(
    val servicesNick: String,
    val buildRegister: (String, String) -> String,
    val buildVerify: (String, String) -> String
)

// Flavors with a working template AND an observable success signal. Only
// "azzurra" for now: atheme needs its VERIFY REGISTER entry, OFTC confirms
// via an out-of-band web link with no shipped verifier.
private val REGISTRATION_TEMPLATES: Map<String, RegistrationTemplate> = mapOf(
    "azzurra" to RegistrationTemplate(
        servicesNick = "NickServ",
        buildRegister = { password, email -> "REGISTER $password $email" },
        buildVerify = { _, code -> "AUTH $code" }
    )
)

// True only for a flavor this client can actually register against - every
// other value (atheme, oft, unknown, null) hides the wizard button.
fun registerableFlavor(flavor: String?): Boolean = flavor in REGISTRATION_TEMPLATES

fun templateForFlavor(flavor: String?): RegistrationTemplate? =
    if (registerableFlavor(flavor)) REGISTRATION_TEMPLATES[flavor] else null

// Naive email shape check (local@domain.tld) - a client-side foolproof guard,
// NOT authoritative validation (NickServ / the mail server is).
private val EMAIL_RE = Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")

const val WIZARD_MIN_PASSWORD = 5
const val WIZARD_MAX_PASSWORD = 32

// Bounded per-step reply wait: a silent NickServ must never hang the flow.
const val WIZARD_STEP_TIMEOUT_MS = 15_000L

// Auto-close delay after step-6 success, so the celebration is actually seen.
const val WIZARD_SUCCESS_CLOSE_MS = 1_600L

fun isValidWizardEmail(email: String): Boolean = EMAIL_RE.matches(email.trim())

fun isValidWizardPassword(password: String): Boolean =
    password.length in WIZARD_MIN_PASSWORD..WIZARD_MAX_PASSWORD

// Transient wizard state - null when closed. stepSinceId is the display bound
// for the NOTICE mirror (replies to THIS send-step only, a structural id bound,
// never a text parse). timedOut arms the retry hint; succeeded latches the
// step-6 celebration.
data class RegistrationWizardState(
    val networkSlug: String,
    val networkId: Int?,
    val servicesNick: String,
    val step: WizardStep = WizardStep.INTRO,
    val email: String = "",
    val password: String = "",
    val code: String = "",
    val stepSinceId: Long = 0L,
    val pending: Boolean = false,
    val timedOut: Boolean = false,
    val succeeded: Boolean = false,
    val error: String? = null
)
