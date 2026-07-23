package com.orgmemory.core.knowledge;

/**
 * Who decides who may read an object. This is the only question the old {@code source_type}
 * column was ever asked, and keeping the source's name in it is what made every new connector
 * a schema change.
 *
 * <p>The distinction is ADR 0009's. It is a fact about custody rather than a policy: a Slack
 * thread's access really is Slack's to decide, and an uploaded file's really is ours. Recorded
 * when an object is ingested and never updated afterwards — an authority that could be flipped
 * would rewrite the access rule for every object already stored under it.
 */
public enum AclAuthority {

    /**
     * OrgMemory decides, through Knowledge Space, review, and publication. There is no external
     * crawl and so no path by which access can widen: what the source allowed at ingestion
     * remains a ceiling, intersected with what is true now. Direct upload and edge capture.
     */
    ORGMEMORY,

    /**
     * The source decides and keeps deciding. Only its latest sealed ACL generation is
     * enforced, because honouring the ingestion-time one would keep access open for somebody
     * the source has since removed. Every connector-crawled object.
     */
    SOURCE
}
