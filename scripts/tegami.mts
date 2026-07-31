import { createCli } from "tegami/cli";
import { createOrgMemoryTegami } from "./tegami-config.mts";

createCli(createOrgMemoryTegami())
  .parseAsync()
  .catch((error: unknown) => {
    console.error(error);
    process.exitCode = 1;
  });
