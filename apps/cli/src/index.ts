#!/usr/bin/env node

import { Command, Option } from "commander"

import {
  orgMemoryUuidSchema,
  namespaceSchema,
  parseSkillCoordinate,
  parseSkillReference,
  parseSkillVersion,
  resolvePackageUrl,
  type SkillManifestLink,
  skillManifestLinkSchema,
  skillSearchSchema,
} from "./contracts.js"
import {
  installSkill,
  listInstalled,
  readBoundedPackage,
  removeInstalledSkill,
  type Agent,
  updateInstalledSkill,
  verifyInstalled,
} from "./install.js"
import { OrgMemoryMcpClient } from "./mcp.js"
import {
  governanceUrl,
  publishSkillDraft,
  type SkillClassification,
} from "./publish.js"
import { buildLocalSkillPackage } from "./skill-package.js"
import { CLI_VERSION } from "./version.js"

const DEFAULT_SERVER = "https://om.kl3in.tech/mcp"
const DEFAULT_CALLBACK_PORT = 53_682
const PACKAGE_DOWNLOAD_TIMEOUT_MS = 60_000
const SKILL_PUBLISH_SCOPE = "assets:read assets:write"

const program = new Command()
  .name("orgmemory")
  .description("Discover and install governed OrgMemory Skills")
  .version(CLI_VERSION)
  .option(
    "--server <url>",
    "OrgMemory MCP server URL",
    process.env.ORGMEMORY_MCP_URL || DEFAULT_SERVER,
  )
  .option(
    "--oauth-callback-port <port>",
    "Local OAuth callback port",
    parsePort,
    DEFAULT_CALLBACK_PORT,
  )

const skill = program.command("skill").description("Discover and install Skills")

skill
  .command("validate")
  .description("Validate and deterministically package one local Skill folder")
  .argument("<folder>", "folder containing root SKILL.md")
  .option("--json", "print machine-readable JSON")
  .action(async (folder: string, options: { json?: boolean }) => {
    const localPackage = await buildLocalSkillPackage(folder)
    writePackageSummary(localPackage, Boolean(options.json))
  })

skill
  .command("publish")
  .description("Create a governed OrgMemory Skill Draft from a local folder")
  .argument("<folder>", "folder containing root SKILL.md")
  .requiredOption("--namespace <namespace>", "company-local Asset namespace")
  .requiredOption("--knowledge-space <uuid>", "governed parent Knowledge Space")
  .addOption(
    new Option("--classification <classification>", "knowledge classification")
      .choices(["PUBLIC", "INTERNAL", "CONFIDENTIAL", "RESTRICTED"])
      .default("INTERNAL"),
  )
  .option("--dry-run", "validate and build without signing in or publishing")
  .option("--json", "print machine-readable JSON")
  .action(
    async (
      folder: string,
      options: {
        namespace: string
        knowledgeSpace: string
        classification: SkillClassification
        dryRun?: boolean
        json?: boolean
      },
    ) => {
      if (!namespaceSchema.safeParse(options.namespace).success) {
        throw new Error("The Skill namespace is invalid")
      }
      if (!orgMemoryUuidSchema.safeParse(options.knowledgeSpace).success) {
        throw new Error("The Knowledge Space ID must be a UUID")
      }
      const localPackage = await buildLocalSkillPackage(folder)
      if (options.dryRun) {
        const result = {
          status: "would-create-draft",
          namespace: options.namespace,
          knowledgeSpaceId: options.knowledgeSpace,
          classification: options.classification,
          ...packageSummary(localPackage),
        }
        if (options.json) {
          process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
        } else {
          process.stdout.write(
            `Validated ${localPackage.name}: ${localPackage.fileCount} files, ${localPackage.archiveBytes.byteLength} archive bytes\n` +
              `Would create ${options.namespace}/${localPackage.name} as an OrgMemory Skill Draft\n` +
              `SHA-256 ${localPackage.packageDigest}\n`,
          )
        }
        return
      }
      await withClient(
        program.opts(),
        async (client, serverUrl) => {
          const published = await publishSkillDraft({
            serverUrl,
            token: await client.accessToken(),
            skillPackage: localPackage,
            namespace: options.namespace,
            knowledgeSpaceId: options.knowledgeSpace,
            classification: options.classification,
          })
          const result = {
            status: "draft-created",
            assetId: published.id,
            draftId: published.draft.id,
            coordinate: `${published.namespace}/${published.slug}`,
            lockVersion: published.draft.lockVersion,
            governanceUrl: governanceUrl(serverUrl, published.id).toString(),
            ...packageSummary(localPackage),
          }
          if (options.json) {
            process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
          } else {
            process.stdout.write(
              `Created Skill Draft ${result.coordinate}\n` +
                `Asset ${result.assetId}\n` +
                `SHA-256 ${result.packageDigest}\n` +
                `Continue in Governance: ${result.governanceUrl}\n`,
            )
          }
        },
        SKILL_PUBLISH_SCOPE,
      )
    },
  )

skill
  .command("search")
  .description("Search released Skills you may currently use")
  .argument("[query]", "title, summary, namespace, or slug", "")
  .option("--json", "print machine-readable JSON")
  .action(async (query: string, options: { json?: boolean }) => {
    await withClient(program.opts(), async (client) => {
      const result = await client.call(
        "search_assets",
        { query, type: "SKILL" },
        skillSearchSchema,
      )
      if (options.json) {
        process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
        return
      }
      if (result.assets.length === 0) {
        process.stdout.write("No released Skills are available to this identity.\n")
        return
      }
      for (const item of result.assets) {
        process.stdout.write(
          `${item.asset.namespace}/${item.asset.slug}@${item.asset.versionLabel}\t${item.asset.title}\n`,
        )
      }
    })
  })

skill
  .command("add")
  .alias("install")
  .description("Install one exact released Skill")
  .argument("<skill>", "<namespace>/<slug>@<version>")
  .addOption(
    new Option("--agent <agent>", "target AI coding agent")
      .choices(["claude-code", "codex"])
      .makeOptionMandatory(),
  )
  .option("--global", "install for the current user instead of this project")
  .action(
    async (
      reference: string,
      options: { agent: Agent; global?: boolean },
    ) => {
      const parsed = parseSkillReference(reference)
      await withClient(program.opts(), async (client, serverUrl) => {
        const { manifestLink: link, packageBytes } = await loadSkillRelease(
          client,
          serverUrl,
          parsed,
        )
        const installed = await installSkill({
          manifestLink: link,
          packageBytes,
          agent: options.agent,
          global: Boolean(options.global),
          cwd: process.cwd(),
        })
        process.stdout.write(
          `Installed ${link.manifest.coordinate}@${link.manifest.version} for ${options.agent}\n${installed.target}\n`,
        )
      })
    },
  )

skill
  .command("verify")
  .description("Verify installed Skill files against token-free local receipts")
  .argument("[skill]", "optional <namespace>/<slug>")
  .addOption(
    new Option("--agent <agent>", "target AI coding agent").choices([
      "claude-code",
      "codex",
    ]),
  )
  .option("--global", "verify current-user installations")
  .option("--json", "print machine-readable JSON")
  .action(
    async (
      reference: string | undefined,
      options: { agent?: Agent; global?: boolean; json?: boolean },
    ) => {
      const coordinate = reference
        ? parseSkillCoordinate(reference).coordinate
        : undefined
      const result = await verifyInstalled({
        global: Boolean(options.global),
        cwd: process.cwd(),
        ...(coordinate ? { coordinate } : {}),
        ...(options.agent ? { agent: options.agent } : {}),
      })
      const entries = Object.values(result)
      const unverified = entries.some((entry) => entry.status !== "verified")
      if (options.json) {
        process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
        if (unverified) {
          process.exitCode = 2
        }
        return
      }
      if (entries.length === 0) {
        process.stdout.write("No matching OrgMemory Skill installations were found.\n")
        return
      }
      for (const item of entries) {
        process.stdout.write(
          `${item.coordinate}@${item.version}\t${item.agent}\t${item.status}${item.reason ? `\t${item.reason}` : ""}\n`,
        )
      }
      if (unverified) {
        process.exitCode = 2
      }
    },
  )

skill
  .command("update")
  .description("Update one verified installation to an exact release")
  .argument("<skill>", "<namespace>/<slug>")
  .requiredOption("--to <version>", "exact destination version")
  .addOption(
    new Option("--agent <agent>", "target AI coding agent")
      .choices(["claude-code", "codex"])
      .makeOptionMandatory(),
  )
  .option("--global", "update the current-user installation")
  .action(
    async (
      reference: string,
      options: { to: string; agent: Agent; global?: boolean },
    ) => {
      const parsed = parseSkillCoordinate(reference)
      const version = parseSkillVersion(options.to)
      await withClient(program.opts(), async (client, serverUrl) => {
        const updated = await updateInstalledSkill({
          coordinate: parsed.coordinate,
          agent: options.agent,
          global: Boolean(options.global),
          cwd: process.cwd(),
          loadRelease: () =>
            loadSkillRelease(client, serverUrl, {
              namespace: parsed.namespace,
              slug: parsed.slug,
              version,
            }),
        })
        process.stdout.write(
          `Updated ${parsed.coordinate}@${updated.version} for ${options.agent}\n${updated.target}\n`,
        )
      })
    },
  )

skill
  .command("remove")
  .description("Remove one verified OrgMemory-managed Skill installation")
  .argument("<skill>", "<namespace>/<slug>")
  .addOption(
    new Option("--agent <agent>", "target AI coding agent")
      .choices(["claude-code", "codex"])
      .makeOptionMandatory(),
  )
  .option("--global", "remove the current-user installation")
  .action(
    async (
      reference: string,
      options: { agent: Agent; global?: boolean },
    ) => {
      const parsed = parseSkillCoordinate(reference)
      await removeInstalledSkill({
        coordinate: parsed.coordinate,
        agent: options.agent,
        global: Boolean(options.global),
        cwd: process.cwd(),
      })
      process.stdout.write(`Removed ${parsed.coordinate} for ${options.agent}\n`)
    },
  )

skill
  .command("list")
  .description("List exact OrgMemory Skill installation receipts")
  .option("--global", "read current-user receipts")
  .option("--json", "print machine-readable JSON")
  .action(async (options: { global?: boolean; json?: boolean }) => {
    const installed = await listInstalled({
      global: Boolean(options.global),
      cwd: process.cwd(),
    })
    if (options.json) {
      process.stdout.write(`${JSON.stringify(installed, null, 2)}\n`)
      return
    }
    const entries = Object.values(installed)
    if (entries.length === 0) {
      process.stdout.write("No OrgMemory Skills are installed in this scope.\n")
      return
    }
    for (const item of entries) {
      process.stdout.write(
        `${item.coordinate}@${item.version}\t${item.agent}\t${item.target}\n`,
      )
    }
  })

program.parseAsync().catch((error: unknown) => {
  const message = error instanceof Error ? error.message : "Unexpected OrgMemory CLI error"
  process.stderr.write(`Error: ${message}\n`)
  process.exitCode = 1
})

async function withClient(
  options: { server: string; oauthCallbackPort: number },
  action: (client: OrgMemoryMcpClient, serverUrl: URL) => Promise<void>,
  scope = "assets:read",
): Promise<void> {
  const serverUrl = requireServerUrl(options.server)
  await using client = new OrgMemoryMcpClient(
    serverUrl,
    options.oauthCallbackPort,
    scope,
  )
  await client.connect()
  await action(client, serverUrl)
}

function packageSummary(localPackage: Awaited<ReturnType<typeof buildLocalSkillPackage>>) {
  return {
    name: localPackage.name,
    description: localPackage.description,
    folder: localPackage.folder,
    fileCount: localPackage.fileCount,
    contentBytes: localPackage.contentBytes,
    archiveBytes: localPackage.archiveBytes.byteLength,
    packageDigest: localPackage.packageDigest,
    files: localPackage.files,
  }
}

function writePackageSummary(
  localPackage: Awaited<ReturnType<typeof buildLocalSkillPackage>>,
  json: boolean,
): void {
  const result = { status: "valid", ...packageSummary(localPackage) }
  if (json) {
    process.stdout.write(`${JSON.stringify(result, null, 2)}\n`)
    return
  }
  process.stdout.write(
    `Valid Skill ${localPackage.name}: ${localPackage.fileCount} files, ` +
      `${localPackage.contentBytes} content bytes, ${localPackage.archiveBytes.byteLength} archive bytes\n` +
      `SHA-256 ${localPackage.packageDigest}\n`,
  )
}

function requireServerUrl(value: string): URL {
  const url = new URL(value)
  if (
    (url.protocol !== "https:" && url.hostname !== "localhost" && url.hostname !== "127.0.0.1") ||
    !url.pathname.endsWith("/mcp")
  ) {
    throw new Error("The MCP server must be HTTPS (or loopback) and end in /mcp")
  }
  return url
}

function parsePort(value: string): number {
  const port = Number.parseInt(value, 10)
  if (!Number.isInteger(port) || port < 1024 || port > 65_535) {
    throw new Error("OAuth callback port must be between 1024 and 65535")
  }
  return port
}

async function loadSkillRelease(
  client: OrgMemoryMcpClient,
  serverUrl: URL,
  reference: { namespace: string; slug: string; version: string },
): Promise<{ manifestLink: SkillManifestLink; packageBytes: Uint8Array }> {
  const manifestLink = await client.call(
    "resolve_skill",
    reference,
    skillManifestLinkSchema,
  )
  const token = await client.accessToken()
  const packageUrl = resolvePackageUrl(serverUrl, manifestLink)
  const controller = new AbortController()
  const timeout = setTimeout(() => controller.abort(), PACKAGE_DOWNLOAD_TIMEOUT_MS)
  try {
    const response = await fetch(packageUrl, {
      headers: { Authorization: `Bearer ${token}` },
      redirect: "error",
      signal: controller.signal,
    })
    return { manifestLink, packageBytes: await readBoundedPackage(response) }
  } finally {
    clearTimeout(timeout)
  }
}
