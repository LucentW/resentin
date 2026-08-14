package pm.antani.resentin.irc

/** Plain case-insensitive substring match of [nick] in [body] — the same "did this
 * message mention me" signal NotificationRouter.shouldNotify() uses to decide whether
 * to notify, reused here (rather than reimplemented) so a message highlighted in chat
 * and a message that actually fired a notification can never drift apart. Word-boundary
 * matching, like the server's own `Grappa.Mentions.mentioned?/3`, would be more precise
 * but isn't implemented client-side yet — this mirrors the existing simpler behavior
 * instead of introducing a second, subtly different notion of "mention". */
fun containsMention(body: String, nick: String): Boolean = body.contains(nick, ignoreCase = true)
