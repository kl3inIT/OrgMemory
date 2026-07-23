package com.orgmemory.core.knowledge;

/**
 * One content object a crawl found — a Slack message or thread rendered to text. The
 * {@code contentRevision} is the source's opaque revision marker (edit timestamp, hash);
 * it is the sole idempotency key on the content path, so an unchanged revision never
 * re-materializes chunks or embeddings.
 *
 * @param externalObjectId the source's stable object id (channel/message key)
 * @param title            a short human title for the object
 * @param body             the rendered text to normalize, chunk, and embed
 * @param contentRevision  the source revision marker used for content idempotency
 */
public record ConnectorContentItem(
        String externalObjectId,
        String title,
        String body,
        String contentRevision) {

    public ConnectorContentItem {
        externalObjectId = requireText(externalObjectId, "externalObjectId");
        title = requireText(title, "title");
        body = requireText(body, "body");
        contentRevision = requireText(contentRevision, "contentRevision");
    }

    private static String requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("connector content " + field + " is required");
        }
        return value.trim();
    }
}
