import { createOrgMemoryTegami } from "./tegami-config.mts";

const paper = createOrgMemoryTegami({
  verifyRemote: true,
  verifyArtifacts: false,
  verifyCurrentMain: false,
  statusOnly: true,
});
const { status, reason } = await paper.getPublishStatus();
console.log(`Publish lock status: ${status}${reason ? ` (${reason})` : ""}`);
process.exitCode = status === "success" ? 0 : status === "pending" ? 2 : 3;
