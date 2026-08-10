package com.orgmemory.core.knowledge.sourceledger;

import com.orgmemory.core.knowledge.evidence.GovernedFileUpload;
import com.orgmemory.core.knowledge.evidence.GovernedFileUploadCommand;
import com.orgmemory.core.knowledge.evidence.GovernedFileUploadResult;
import java.io.InputStream;
import org.springframework.stereotype.Service;

@Service
class SourceLedgerGovernedFileUpload implements GovernedFileUpload {

    private final SourceUploadService uploads;

    SourceLedgerGovernedFileUpload(SourceUploadService uploads) {
        this.uploads = uploads;
    }

    @Override
    public GovernedFileUploadResult upload(
            GovernedFileUploadCommand command,
            InputStream content) {
        SourceUploadResult result = uploads.uploadWithIdentity(
                new CreateUploadSourceCommand(
                        command.actor(),
                        command.fileName(),
                        command.contentLength(),
                        command.classification(),
                        command.knowledgeSpaceId()),
                content);
        return new GovernedFileUploadResult(
                result.summary().id(),
                result.sourceRevisionId(),
                result.knowledgeSpaceId(),
                result.summary().fileName());
    }
}
