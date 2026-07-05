package com.orgmemory.core.capability;

import com.orgmemory.core.shared.BaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.util.UUID;

@Entity
@Table(name = "asset_versions")
public class AssetVersion extends BaseEntity {

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "version_number", nullable = false)
    private int versionNumber;

    @Column(name = "prompt_template", columnDefinition = "text")
    private String promptTemplate;

    @Column(name = "workflow_steps_json", columnDefinition = "text")
    private String workflowStepsJson;

    @Column(name = "input_schema_json", columnDefinition = "text")
    private String inputSchemaJson;

    @Column(name = "output_schema_json", columnDefinition = "text")
    private String outputSchemaJson;

    @Column(name = "example_input", columnDefinition = "text")
    private String exampleInput;

    @Column(name = "example_output", columnDefinition = "text")
    private String exampleOutput;

    @Column(name = "change_note")
    private String changeNote;

    @Column(name = "created_by_user_id")
    private UUID createdByUserId;

    protected AssetVersion() {
    }

    public AssetVersion(UUID assetId, int versionNumber, CreateCapabilityAssetCommand command) {
        super(UUID.randomUUID());
        this.assetId = assetId;
        this.versionNumber = versionNumber;
        this.promptTemplate = command.promptTemplate();
        this.workflowStepsJson = command.workflowStepsJson();
        this.inputSchemaJson = command.inputSchemaJson();
        this.outputSchemaJson = command.outputSchemaJson();
        this.exampleInput = command.exampleInput();
        this.exampleOutput = command.exampleOutput();
        this.changeNote = "Initial version";
        this.createdByUserId = command.createdByUserId();
    }

    public UUID getAssetId() {
        return assetId;
    }

    public int getVersionNumber() {
        return versionNumber;
    }

    public String getPromptTemplate() {
        return promptTemplate;
    }

    public String getWorkflowStepsJson() {
        return workflowStepsJson;
    }

    public String getInputSchemaJson() {
        return inputSchemaJson;
    }

    public String getOutputSchemaJson() {
        return outputSchemaJson;
    }

    public String getExampleInput() {
        return exampleInput;
    }

    public String getExampleOutput() {
        return exampleOutput;
    }

    public String getChangeNote() {
        return changeNote;
    }

    public UUID getCreatedByUserId() {
        return createdByUserId;
    }
}
