package pm.antani.resentin.ui.chat

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SlashCommandTest {
    @Test
    fun parsesWhoisQueryAndJoinArguments() {
        assertParsed("/whois mario", "whois", listOf("mario"))
        assertParsed("/query mario", "query", listOf("mario"))
        assertParsed("/join #linux", "join", listOf("#linux"))
    }

    @Test
    fun acceptsOptionalAwayAndPartArguments() {
        assertParsed("/away", "away", emptyList())
        assertParsed("/part #linux", "part", listOf("#linux"))
    }

    @Test
    fun recognizesQueryAlias() {
        assertParsed("/q mario", "query", listOf("mario"))
    }

    @Test
    fun reportsUnknownIncompleteAndMissingArguments() {
        assertTrue(parseSlashCommand("/not-a-command") is SlashCommandParseResult.Invalid)
        assertTrue(parseSlashCommand("/") is SlashCommandParseResult.Incomplete)
        assertTrue(parseSlashCommand("/whois") is SlashCommandParseResult.Invalid)
    }

    @Test
    fun ignoresSlashInsideNormalText() {
        assertEquals(SlashCommandParseResult.NotACommand, parseSlashCommand("hello /whois mario"))
    }

    @Test
    fun filtersSuggestionsOnlyBeforeArguments() {
        assertEquals(listOf("whois", "whowas", "who"), suggestSlashCommands("/wh").map { it.name })
        assertEquals(slashCommandCatalog.size, suggestSlashCommands("/").size)
        assertTrue(suggestSlashCommands("/whois ").isEmpty())
        assertTrue(suggestSlashCommands("hello /").isEmpty())
        assertEquals(listOf("notify"), suggestSlashCommands("/not").map { it.name })
    }

    @Test
    fun parsesQuotedArgumentsAndSuggestsDynamicValues() {
        assertParsed("/kick mario \"via chat\"", "kick", listOf("mario", "via chat"))
        assertEquals(listOf("luigi", "mario"), suggestSlashArguments("/whois ", listOf("mario", "luigi"), emptyList(), emptyList()).map { it.value })
        assertEquals(listOf("#linux"), suggestSlashArguments("/join #l", emptyList(), listOf("#linux", "#offtopic"), emptyList()).map { it.value })
        assertEquals(listOf("grappa"), suggestSlashArguments("/connect g", emptyList(), emptyList(), listOf("grappa", "libera")).map { it.value })
    }

    @Test
    fun expandsUserAliasWithoutShadowingBuiltIns() {
        assertEquals("/query mario", expandUserSlashAlias("/dm mario", mapOf("dm" to "/query " + '$' + "1")))
        assertEquals(null, expandUserSlashAlias("/query mario", mapOf("query" to "/whois " + '$' + "1")))
    }

    @Test
    fun formatsMeAsCtcpActionBody() {
        assertEquals("\u0001ACTION waves hello\u0001", ctcpActionBody("waves hello"))
    }

    @Test
    fun completionAddsSpaceKeepsSuffixAndPlacesCursorBeforeSuffix() {
        val whois = slashCommandCatalog.first { it.name == "whois" }

        assertEquals(
            SlashCommandCompletion("/whois ", 7),
            completeSlashCommandInput("/wh", whois),
        )
        assertEquals(
            SlashCommandCompletion("/whois mario", 7),
            completeSlashCommandInput("/wh mario", whois),
        )
    }

    private fun assertParsed(input: String, name: String, arguments: List<String>) {
        val result = parseSlashCommand(input)
        assertTrue(result is SlashCommandParseResult.Parsed)
        result as SlashCommandParseResult.Parsed
        assertEquals(name, result.command.name)
        assertEquals(arguments, result.command.arguments)
    }
}