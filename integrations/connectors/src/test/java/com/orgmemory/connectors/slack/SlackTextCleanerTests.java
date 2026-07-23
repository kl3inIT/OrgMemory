package com.orgmemory.connectors.slack;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Slack stores a message as markup, not as the text anybody read. Indexing the markup puts
 * identifiers nobody would ever search for into the full-text index and, worse, into the
 * embedding, where an opaque id cannot match the name a question would use.
 */
class SlackTextCleanerTests {

    private static final Map<String, String> NAMES = Map.of("U024BE7LH", "Mai", "U7GHJ2K", "Lan");

    @Test
    void resolvesAMentionToTheNameSomebodyWouldAskAbout() {
        assertEquals(
                "@Mai can you review this",
                SlackTextCleaner.clean("<@U024BE7LH> can you review this", NAMES));
    }

    @Test
    void keepsAnUnknownMentionAttributableRatherThanDroppingIt() {
        String cleaned = SlackTextCleaner.clean("<@U0DELETED> left this note", NAMES);

        assertEquals("@U0DELETED left this note", cleaned,
                "a deleted account's messages stay attributable to something");
    }

    @Test
    void readsAChannelLinkAsItsName() {
        assertEquals("see #general for details", SlackTextCleaner.clean("see <#C024BE7LR|general> for details", NAMES));
    }

    @Test
    void keepsBothHalvesOfALabelledLink() {
        String cleaned = SlackTextCleaner.clean("read the <https://wiki.example/runbook|deploy runbook>", NAMES);

        assertEquals("read the deploy runbook (https://wiki.example/runbook)", cleaned,
                "the words carry the meaning and the address is sometimes the answer");
    }

    @Test
    void unwrapsABareLink() {
        assertEquals("https://wiki.example/x", SlackTextCleaner.clean("<https://wiki.example/x>", NAMES));
    }

    @Test
    void readsGroupAndBroadcastMentions() {
        assertEquals("@platform please look", SlackTextCleaner.clean("<!subteam^S123|@platform> please look", NAMES));
        assertEquals("@here standup in five", SlackTextCleaner.clean("<!here> standup in five", NAMES));
        assertEquals("@channel outage", SlackTextCleaner.clean("<!channel> outage", NAMES));
    }

    @Test
    void restoresTheThreeCharactersSlackEscapes() {
        assertEquals("a < b && c > d", SlackTextCleaner.clean("a &lt; b &amp;&amp; c &gt; d", NAMES));
    }

    @Test
    void leavesNoSlackMarkupBehindInARealisticMessage() {
        String cleaned = SlackTextCleaner.clean(
                "<@U024BE7LH> the <#C024BE7LR|incidents> runbook is at "
                        + "<https://wiki.example/rb|the wiki> &amp; <!here> should read it",
                NAMES);

        assertFalse(cleaned.contains("<"), () -> "markup survived: " + cleaned);
        assertFalse(cleaned.contains("U024BE7LH"), () -> "an id survived: " + cleaned);
        assertEquals(
                "@Mai the #incidents runbook is at the wiki (https://wiki.example/rb) & @here should read it",
                cleaned);
    }

    @Test
    void treatsAnAbsentMessageAsEmpty() {
        assertEquals("", SlackTextCleaner.clean(null, NAMES));
        assertEquals("", SlackTextCleaner.clean("   ", NAMES));
    }
}
