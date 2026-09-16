package pm.antani.resentin.ui.chat

import pm.antani.resentin.R

enum class SlashArgumentKind { NONE, TEXT, CHANNEL, NICK, NICKS, NETWORK, MODES, MASK, COMMAND, ALIAS }
enum class SlashCommandAvailability { SUPPORTED, RECOGNIZED_UNSUPPORTED }
enum class SlashCommandHandler { ACTION, JOIN, PART, CYCLE, TOPIC, NICK, MESSAGE, QUERY, WHOIS, WHOWAS, WHO, NAMES, LUSERS, LINKS, PRIVILEGE, KICK, KICKBAN, BAN, UNBAN, BANLIST, INVITE, USER_MODE, CHANNEL_MODE, SERVICE, AWAY, NOTIFY, HILIGHT, IGNORE, UNIGNORE, ALIAS, UNALIAS, CREDITS, CONNECT, DISCONNECT, RECONNECT, QUIT }

data class SlashCommandSpec(
    val name: String,
    val aliases: Set<String> = emptySet(),
    val syntaxRes: Int,
    val descriptionRes: Int,
    val argumentKind: SlashArgumentKind = SlashArgumentKind.NONE,
    val minArguments: Int = 0,
    val availability: SlashCommandAvailability = SlashCommandAvailability.RECOGNIZED_UNSUPPORTED,
    val handler: SlashCommandHandler,
) { val requiresArgument: Boolean get() = minArguments > 0 }

data class SlashCommand(val spec: SlashCommandSpec, val arguments: List<String>) { val name: String get() = spec.name }
enum class SlashCommandError { UnknownCommand, MissingArgument, InvalidArgument }
sealed interface SlashCommandParseResult {
    data object NotACommand : SlashCommandParseResult
    data object Incomplete : SlashCommandParseResult
    data class Parsed(val command: SlashCommand) : SlashCommandParseResult
    data class Invalid(val error: SlashCommandError, val token: String) : SlashCommandParseResult
}

private fun supported(
    name: String,
    syntaxRes: Int,
    descriptionRes: Int,
    aliases: Set<String> = emptySet(),
    argumentKind: SlashArgumentKind = SlashArgumentKind.NONE,
    minArguments: Int = 0,
    handler: SlashCommandHandler,
) = SlashCommandSpec(name, aliases, syntaxRes, descriptionRes, argumentKind, minArguments, SlashCommandAvailability.SUPPORTED, handler)

private fun unsupported(
    name: String,
    syntaxRes: Int,
    descriptionRes: Int,
    aliases: Set<String> = emptySet(),
    argumentKind: SlashArgumentKind = SlashArgumentKind.NONE,
    minArguments: Int = 0,
    handler: SlashCommandHandler,
) = SlashCommandSpec(name, aliases, syntaxRes, descriptionRes, argumentKind, minArguments, SlashCommandAvailability.RECOGNIZED_UNSUPPORTED, handler)

val slashCommandCatalog: List<SlashCommandSpec> = listOf(
    supported("me", R.string.chat_slash_syntax_me, R.string.chat_slash_description_me, argumentKind = SlashArgumentKind.TEXT, minArguments = 1, handler = SlashCommandHandler.ACTION),
    supported("join", R.string.chat_slash_syntax_join, R.string.chat_slash_description_join, argumentKind = SlashArgumentKind.CHANNEL, minArguments = 1, handler = SlashCommandHandler.JOIN),
    supported("part", R.string.chat_slash_syntax_part, R.string.chat_slash_description_part, argumentKind = SlashArgumentKind.CHANNEL, handler = SlashCommandHandler.PART),
    supported("cycle", R.string.chat_slash_syntax_cycle, R.string.chat_slash_description_cycle, argumentKind = SlashArgumentKind.CHANNEL, handler = SlashCommandHandler.CYCLE),
    supported("topic", R.string.chat_slash_syntax_topic, R.string.chat_slash_description_topic, argumentKind = SlashArgumentKind.TEXT, minArguments = 1, handler = SlashCommandHandler.TOPIC),
    supported("nick", R.string.chat_slash_syntax_nick, R.string.chat_slash_description_nick, argumentKind = SlashArgumentKind.NICK, minArguments = 1, handler = SlashCommandHandler.NICK),
    supported("msg", R.string.chat_slash_syntax_msg, R.string.chat_slash_description_msg, argumentKind = SlashArgumentKind.NICK, minArguments = 2, handler = SlashCommandHandler.MESSAGE),
    supported("query", R.string.chat_slash_syntax_query, R.string.chat_slash_description_query, aliases = setOf("q"), argumentKind = SlashArgumentKind.NICK, minArguments = 1, handler = SlashCommandHandler.QUERY),
    supported("whois", R.string.chat_slash_syntax_whois, R.string.chat_slash_description_whois, argumentKind = SlashArgumentKind.NICK, minArguments = 1, handler = SlashCommandHandler.WHOIS),
    supported("whowas", R.string.chat_slash_syntax_whowas, R.string.chat_slash_description_whowas, argumentKind = SlashArgumentKind.NICK, minArguments = 1, handler = SlashCommandHandler.WHOWAS),
    supported("who", R.string.chat_slash_syntax_who, R.string.chat_slash_description_who, argumentKind = SlashArgumentKind.CHANNEL, handler = SlashCommandHandler.WHO),
    supported("names", R.string.chat_slash_syntax_names, R.string.chat_slash_description_names, argumentKind = SlashArgumentKind.CHANNEL, minArguments = 1, handler = SlashCommandHandler.NAMES),
    supported("lusers", R.string.chat_slash_syntax_lusers, R.string.chat_slash_description_lusers, handler = SlashCommandHandler.LUSERS),
    supported("links", R.string.chat_slash_syntax_links, R.string.chat_slash_description_links, argumentKind = SlashArgumentKind.MASK, handler = SlashCommandHandler.LINKS),
    supported("op", R.string.chat_slash_syntax_privilege, R.string.chat_slash_description_op, argumentKind = SlashArgumentKind.NICKS, minArguments = 1, handler = SlashCommandHandler.PRIVILEGE),
    supported("deop", R.string.chat_slash_syntax_privilege, R.string.chat_slash_description_deop, argumentKind = SlashArgumentKind.NICKS, minArguments = 1, handler = SlashCommandHandler.PRIVILEGE),
    supported("voice", R.string.chat_slash_syntax_privilege, R.string.chat_slash_description_voice, argumentKind = SlashArgumentKind.NICKS, minArguments = 1, handler = SlashCommandHandler.PRIVILEGE),
    supported("devoice", R.string.chat_slash_syntax_privilege, R.string.chat_slash_description_devoice, argumentKind = SlashArgumentKind.NICKS, minArguments = 1, handler = SlashCommandHandler.PRIVILEGE),
    supported("kick", R.string.chat_slash_syntax_kick, R.string.chat_slash_description_kick, argumentKind = SlashArgumentKind.NICK, minArguments = 1, handler = SlashCommandHandler.KICK),
    supported("kb", R.string.chat_slash_syntax_kickban, R.string.chat_slash_description_kickban, aliases = setOf("kickban"), argumentKind = SlashArgumentKind.NICK, minArguments = 1, handler = SlashCommandHandler.KICKBAN),
    supported("ban", R.string.chat_slash_syntax_ban, R.string.chat_slash_description_ban, argumentKind = SlashArgumentKind.MASK, minArguments = 1, handler = SlashCommandHandler.BAN),
    supported("unban", R.string.chat_slash_syntax_unban, R.string.chat_slash_description_unban, argumentKind = SlashArgumentKind.MASK, minArguments = 1, handler = SlashCommandHandler.UNBAN),
    supported("banlist", R.string.chat_slash_syntax_banlist, R.string.chat_slash_description_banlist, handler = SlashCommandHandler.BANLIST),
    supported("ignore", R.string.chat_slash_syntax_ignore, R.string.chat_slash_description_ignore, argumentKind = SlashArgumentKind.MASK, minArguments = 1, handler = SlashCommandHandler.IGNORE),
    supported("unignore", R.string.chat_slash_syntax_unignore, R.string.chat_slash_description_unignore, argumentKind = SlashArgumentKind.MASK, minArguments = 1, handler = SlashCommandHandler.UNIGNORE),
    supported("invite", R.string.chat_slash_syntax_invite, R.string.chat_slash_description_invite, argumentKind = SlashArgumentKind.NICK, minArguments = 1, handler = SlashCommandHandler.INVITE),
    supported("umode", R.string.chat_slash_syntax_umode, R.string.chat_slash_description_umode, argumentKind = SlashArgumentKind.MODES, handler = SlashCommandHandler.USER_MODE),
    supported("mode", R.string.chat_slash_syntax_mode, R.string.chat_slash_description_mode, argumentKind = SlashArgumentKind.CHANNEL, handler = SlashCommandHandler.CHANNEL_MODE),
    supported("ns", R.string.chat_slash_syntax_service, R.string.chat_slash_description_service, argumentKind = SlashArgumentKind.COMMAND, handler = SlashCommandHandler.SERVICE),
    supported("cs", R.string.chat_slash_syntax_service, R.string.chat_slash_description_service, argumentKind = SlashArgumentKind.COMMAND, handler = SlashCommandHandler.SERVICE),
    supported("ms", R.string.chat_slash_syntax_service, R.string.chat_slash_description_service, argumentKind = SlashArgumentKind.COMMAND, handler = SlashCommandHandler.SERVICE),
    supported("os", R.string.chat_slash_syntax_service, R.string.chat_slash_description_service, argumentKind = SlashArgumentKind.COMMAND, handler = SlashCommandHandler.SERVICE),
    supported("hs", R.string.chat_slash_syntax_service, R.string.chat_slash_description_service, argumentKind = SlashArgumentKind.COMMAND, handler = SlashCommandHandler.SERVICE),
    supported("rs", R.string.chat_slash_syntax_service, R.string.chat_slash_description_service, argumentKind = SlashArgumentKind.COMMAND, handler = SlashCommandHandler.SERVICE),
    supported("away", R.string.chat_slash_syntax_away, R.string.chat_slash_description_away, argumentKind = SlashArgumentKind.TEXT, handler = SlashCommandHandler.AWAY),
    supported("notify", R.string.chat_slash_syntax_notify, R.string.chat_slash_description_notify, aliases = setOf("watch"), argumentKind = SlashArgumentKind.NICKS, minArguments = 1, handler = SlashCommandHandler.NOTIFY),
    supported("hilight", R.string.chat_slash_syntax_hilight, R.string.chat_slash_description_hilight, aliases = setOf("highlight"), argumentKind = SlashArgumentKind.TEXT, minArguments = 1, handler = SlashCommandHandler.HILIGHT),
    supported("dehilight", R.string.chat_slash_syntax_dehilight, R.string.chat_slash_description_dehilight, argumentKind = SlashArgumentKind.TEXT, minArguments = 1, handler = SlashCommandHandler.HILIGHT),
    supported("alias", R.string.chat_slash_syntax_alias, R.string.chat_slash_description_alias, argumentKind = SlashArgumentKind.ALIAS, handler = SlashCommandHandler.ALIAS),
    supported("unalias", R.string.chat_slash_syntax_unalias, R.string.chat_slash_description_unalias, argumentKind = SlashArgumentKind.ALIAS, minArguments = 1, handler = SlashCommandHandler.UNALIAS),
    supported("credits", R.string.chat_slash_syntax_credits, R.string.chat_slash_description_credits, handler = SlashCommandHandler.CREDITS),
    supported("connect", R.string.chat_slash_syntax_connect, R.string.chat_slash_description_connect, argumentKind = SlashArgumentKind.NETWORK, minArguments = 1, handler = SlashCommandHandler.CONNECT),
    supported("disconnect", R.string.chat_slash_syntax_disconnect, R.string.chat_slash_description_disconnect, argumentKind = SlashArgumentKind.NETWORK, handler = SlashCommandHandler.DISCONNECT),
    supported("reconnect", R.string.chat_slash_syntax_reconnect, R.string.chat_slash_description_reconnect, argumentKind = SlashArgumentKind.NETWORK, handler = SlashCommandHandler.RECONNECT),
    supported("quit", R.string.chat_slash_syntax_quit, R.string.chat_slash_description_quit, argumentKind = SlashArgumentKind.TEXT, handler = SlashCommandHandler.QUIT),
)

private fun findSpec(token: String, catalog: List<SlashCommandSpec>): SlashCommandSpec? = catalog.firstOrNull { spec ->
    spec.name.equals(token, ignoreCase = true) || spec.aliases.any { it.equals(token, ignoreCase = true) }
}

/** Small shell-like tokenizer: whitespace separates args, double quotes group them, backslash escapes the next char. */
fun tokenizeSlashArguments(body: String): List<String>? {
    val result = mutableListOf<String>()
    val current = StringBuilder()
    var quoted = false
    var escaped = false
    var tokenStarted = false
    for (char in body.trim()) {
        when {
            escaped -> { current.append(char); escaped = false; tokenStarted = true }
            char == '\\' -> { escaped = true; tokenStarted = true }
            char == '"' -> { quoted = !quoted; tokenStarted = true }
            char.isWhitespace() && !quoted -> {
                if (tokenStarted) { result += current.toString(); current.clear(); tokenStarted = false }
            }
            else -> { current.append(char); tokenStarted = true }
        }
    }
    if (escaped || quoted) return null
    if (tokenStarted) result += current.toString()
    return result
}

fun parseSlashCommand(input: String, catalog: List<SlashCommandSpec> = slashCommandCatalog): SlashCommandParseResult {
    if (!input.startsWith('/')) return SlashCommandParseResult.NotACommand
    val body = input.removePrefix("/").trim()
    if (body.isEmpty()) return SlashCommandParseResult.Incomplete
    val tokens = tokenizeSlashArguments(body) ?: return SlashCommandParseResult.Invalid(SlashCommandError.InvalidArgument, body.substringBefore(' '))
    val token = tokens.firstOrNull().orEmpty()
    val spec = findSpec(token, catalog) ?: return SlashCommandParseResult.Invalid(SlashCommandError.UnknownCommand, token)
    val arguments = tokens.drop(1)
    if (arguments.size < spec.minArguments) return SlashCommandParseResult.Invalid(SlashCommandError.MissingArgument, spec.name)
    if (spec.name == "join" && arguments.firstOrNull()?.firstOrNull() !in setOf('#', '&', '+', '!')) return SlashCommandParseResult.Invalid(SlashCommandError.InvalidArgument, spec.name)
    if (spec.name == "topic" && arguments.firstOrNull() == "-delete" && arguments.size > 1) return SlashCommandParseResult.Invalid(SlashCommandError.InvalidArgument, spec.name)
    return SlashCommandParseResult.Parsed(SlashCommand(spec, arguments))
}

fun suggestSlashCommands(input: String, catalog: List<SlashCommandSpec> = slashCommandCatalog): List<SlashCommandSpec> {
    if (!input.startsWith('/')) return emptyList()
    val query = input.removePrefix("/")
    if (query.any(Char::isWhitespace)) return emptyList()
    return catalog.filter { spec -> spec.name.startsWith(query, ignoreCase = true) || spec.aliases.any { it.startsWith(query, ignoreCase = true) } }
}

data class SlashArgumentSuggestion(val value: String, val label: String = value)

fun suggestSlashArguments(
    input: String,
    members: List<String>,
    channels: List<String>,
    networks: List<String>,
    catalog: List<SlashCommandSpec> = slashCommandCatalog,
): List<SlashArgumentSuggestion> {
    if (!input.startsWith('/')) return emptyList()
    val firstSpace = input.indexOfFirst(Char::isWhitespace)
    if (firstSpace < 0) return emptyList()
    val spec = findSpec(input.substring(1, firstSpace), catalog) ?: return emptyList()
    val prefix = input.substring(firstSpace + 1).takeLastWhile { !it.isWhitespace() }
    val source = when (spec.argumentKind) {
        SlashArgumentKind.CHANNEL -> channels
        SlashArgumentKind.NICK, SlashArgumentKind.NICKS -> members
        SlashArgumentKind.NETWORK -> networks
        else -> emptyList()
    }
    return source.asSequence()
        .filter { prefix.isBlank() || it.contains(prefix, ignoreCase = true) }
        .distinctBy { it.lowercase() }
        .sortedWith(compareBy({ !it.startsWith(prefix, ignoreCase = true) }, { it.lowercase() }))
        .take(8)
        .map(::SlashArgumentSuggestion)
        .toList()
}

/** Expands a user alias using Cicchetto's positional placeholders. */
fun expandUserSlashAlias(input: String, aliases: Map<String, String>): String? {
    if (!input.startsWith("/")) return null
    val tokens = tokenizeSlashArguments(input.removePrefix("/")) ?: return null
    val name = tokens.firstOrNull() ?: return null
    if (findSpec(name, slashCommandCatalog) != null) return null
    val expansion = aliases.entries.firstOrNull { it.key.equals(name, ignoreCase = true) }?.value ?: return null
    val args = tokens.drop(1)
    var result = expansion
    for (index in 1..9) result = result.replace("${'$'}$index", args.getOrNull(index - 1).orEmpty())
    result = result.replace(Regex("\\$(\\d)-")) { match ->
        args.drop(match.groupValues[1].toInt() - 1).joinToString(" ")
    }
    result = result.replace("${'$'}*", args.joinToString(" "))
    if (!expansion.contains("$")) result = listOf(result, args.joinToString(" ")).filter { it.isNotBlank() }.joinToString(" ")
    return result.trim().let { if (it.startsWith("/")) it else "/$it" }
}
data class SlashCommandCompletion(val text: String, val cursor: Int)

fun completeSlashCommandInput(input: String, command: SlashCommandSpec): SlashCommandCompletion {
    val tokenEnd = input.indexOfFirst(Char::isWhitespace).let { if (it < 0) input.length else it }
    val suffix = input.substring(tokenEnd).trimStart()
    val inserted = "/${command.name} "
    return SlashCommandCompletion(text = inserted + suffix, cursor = inserted.length)
}

fun completeSlashArgumentInput(input: String, suggestion: SlashArgumentSuggestion): SlashCommandCompletion {
    val tokenStart = input.indexOfLast { it.isWhitespace() } + 1
    val before = input.substring(0, tokenStart)
    val after = input.substring(tokenStart).takeIf { it.any(Char::isWhitespace) }?.trimStart().orEmpty()
    val inserted = before + suggestion.value + " " + after
    return SlashCommandCompletion(inserted, before.length + suggestion.value.length + 1)
}
