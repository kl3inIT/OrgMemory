package com.orgmemory.connectors.slack;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns Slack's message markup into the text a reader would see.
 *
 * <p>Slack stores mentions as {@code <@U024BE7LH>} and channel links as
 * {@code <#C024BE7LR|general>}. Left alone, those identifiers reach the full-text index and the
 * embedding, where they are noise at best: nobody searches for {@code U024BE7LH}, and a name
 * that only ever appears as an opaque id cannot be matched against a question that uses it.
 * Onyx cleans the same patterns for the same reason.
 */
final class SlackTextCleaner {

    // The id charset is deliberately not spelled out. Slack has issued U, W, and B prefixes and
    // longer Enterprise Grid forms over the years, and a pattern that guesses wrong does not fail
    // loudly — it silently leaves raw markup in the index.
    private static final Pattern USER_MENTION = Pattern.compile("<@([^<>|]+)(?:\\|[^>]*)?>");
    private static final Pattern CHANNEL_MENTION = Pattern.compile("<#[^<>|]+\\|([^>]*)>");
    private static final Pattern LINK_WITH_LABEL = Pattern.compile("<(https?://[^|>]+)\\|([^>]+)>");
    private static final Pattern BARE_LINK = Pattern.compile("<(https?://[^>]+)>");
    private static final Pattern SPECIAL_WITH_LABEL = Pattern.compile("<!([^|>]+)\\|([^>]+)>");

    private SlackTextCleaner() {
    }

    /**
     * @param text        the raw message text as Slack stored it
     * @param displayNames user id to display name for everyone the crawl observed
     */
    static String clean(String text, Map<String, String> displayNames) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String cleaned = replaceUserMentions(text, displayNames);
        cleaned = CHANNEL_MENTION.matcher(cleaned).replaceAll(match -> "#" + Matcher.quoteReplacement(match.group(1)));
        // A labelled link keeps both halves: the words carry the meaning and the address is
        // sometimes the answer itself.
        cleaned = LINK_WITH_LABEL.matcher(cleaned)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(2) + " (" + match.group(1) + ")"));
        cleaned = BARE_LINK.matcher(cleaned).replaceAll(match -> Matcher.quoteReplacement(match.group(1)));
        // <!subteam^S123|@platform> and friends: the label is the readable half.
        cleaned = SPECIAL_WITH_LABEL.matcher(cleaned)
                .replaceAll(match -> Matcher.quoteReplacement(match.group(2)));
        cleaned = cleaned.replace("<!channel>", "@channel")
                .replace("<!here>", "@here")
                .replace("<!everyone>", "@everyone");
        return unescape(cleaned).strip();
    }

    private static String replaceUserMentions(String text, Map<String, String> displayNames) {
        return USER_MENTION.matcher(text).replaceAll(match -> {
            String userId = match.group(1);
            String name = displayNames.get(userId);
            // An unknown id still reads better as a mention than as markup, and keeping the id
            // means a deleted account's messages stay attributable.
            return Matcher.quoteReplacement("@" + (name == null || name.isBlank() ? userId : name));
        });
    }

    /** Slack escapes these three characters in message text and nothing else. */
    private static String unescape(String text) {
        return text.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
    }
}
