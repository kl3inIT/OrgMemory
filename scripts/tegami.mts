import { createCli } from "tegami/cli";
import { createOrgMemoryTegami } from "./tegami-config.mts";

void createCli(createOrgMemoryTegami()).parseAsync();
