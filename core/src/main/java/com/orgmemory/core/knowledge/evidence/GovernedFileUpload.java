package com.orgmemory.core.knowledge.evidence;

import java.io.InputStream;

/** Registers bytes through the canonical governed Source pipeline. */
public interface GovernedFileUpload {

    GovernedFileUploadResult upload(
            GovernedFileUploadCommand command,
            InputStream content);
}
