import type {
  OAuthClientInformationMixed,
  OAuthClientMetadata,
  OAuthTokens,
} from "@modelcontextprotocol/sdk/shared/auth.js"
import type {
  OAuthClientProvider,
  OAuthDiscoveryState,
} from "@modelcontextprotocol/sdk/client/auth.js"
import { createHash, randomBytes } from "node:crypto"
import { mkdir, readFile, rename, rm, writeFile } from "node:fs/promises"
import { createServer, type Server } from "node:http"
import { homedir, platform } from "node:os"
import { dirname, join } from "node:path"
import { spawn } from "node:child_process"

type PersistedOAuthState = {
  clientInformation?: OAuthClientInformationMixed
  tokens?: OAuthTokens
  discovery?: OAuthDiscoveryState
}

export class FileOAuthClientProvider implements OAuthClientProvider {
  readonly clientMetadata: OAuthClientMetadata
  private readonly stateFile: string
  private readonly callback: OAuthCallback
  private persisted: PersistedOAuthState | undefined
  private verifier: string | undefined
  private expectedState: string | undefined

  constructor(
    readonly serverUrl: URL,
    callbackPort: number,
  ) {
    this.callback = new OAuthCallback(callbackPort, () => this.expectedState)
    this.stateFile = oauthStatePath(serverUrl)
    this.clientMetadata = {
      client_name: "OrgMemory CLI",
      redirect_uris: [String(this.redirectUrl)],
      grant_types: ["authorization_code", "refresh_token"],
      response_types: ["code"],
      token_endpoint_auth_method: "none",
      scope: "assets:read",
      software_id: "orgmemory-cli",
      software_version: "0.1.0",
    }
  }

  get redirectUrl(): string {
    return this.callback.url
  }

  async state(): Promise<string> {
    this.expectedState = randomBytes(32).toString("base64url")
    return this.expectedState
  }

  async clientInformation(): Promise<OAuthClientInformationMixed | undefined> {
    return (await this.load()).clientInformation
  }

  async saveClientInformation(clientInformation: OAuthClientInformationMixed): Promise<void> {
    const state = await this.load()
    state.clientInformation = clientInformation
    await this.save(state)
  }

  async tokens(): Promise<OAuthTokens | undefined> {
    return (await this.load()).tokens
  }

  async saveTokens(tokens: OAuthTokens): Promise<void> {
    const state = await this.load()
    state.tokens = tokens
    await this.save(state)
  }

  async redirectToAuthorization(authorizationUrl: URL): Promise<void> {
    await this.callback.start()
    if (!(await openBrowser(authorizationUrl))) {
      process.stderr.write(`Open this URL to sign in:\n${authorizationUrl.toString()}\n`)
    }
  }

  saveCodeVerifier(codeVerifier: string): void {
    this.verifier = codeVerifier
  }

  codeVerifier(): string {
    if (!this.verifier) {
      throw new Error("OAuth PKCE verifier is unavailable")
    }
    return this.verifier
  }

  async saveDiscoveryState(discovery: OAuthDiscoveryState): Promise<void> {
    const state = await this.load()
    state.discovery = discovery
    await this.save(state)
  }

  async discoveryState(): Promise<OAuthDiscoveryState | undefined> {
    return (await this.load()).discovery
  }

  async invalidateCredentials(
    scope: "all" | "client" | "tokens" | "verifier" | "discovery",
  ): Promise<void> {
    const state = await this.load()
    if (scope === "all" || scope === "client") delete state.clientInformation
    if (scope === "all" || scope === "tokens") delete state.tokens
    if (scope === "all" || scope === "discovery") delete state.discovery
    if (scope === "all" || scope === "verifier") this.verifier = undefined
    await this.save(state)
  }

  waitForAuthorizationCode(): Promise<string> {
    return this.callback.wait()
  }

  async closeCallback(): Promise<void> {
    await this.callback.close()
  }

  async accessToken(): Promise<string> {
    const tokens = await this.tokens()
    if (!tokens?.access_token) {
      throw new Error("OAuth access token is unavailable")
    }
    return tokens.access_token
  }

  private async load(): Promise<PersistedOAuthState> {
    if (this.persisted) return this.persisted
    try {
      const source = JSON.parse(await readFile(this.stateFile, "utf8")) as unknown
      if (!source || typeof source !== "object" || Array.isArray(source)) {
        throw new Error("OAuth state is not an object")
      }
      this.persisted = source as PersistedOAuthState
    } catch (error) {
      if (
        error instanceof Error &&
        "code" in error &&
        (error as NodeJS.ErrnoException).code === "ENOENT"
      ) {
        this.persisted = {}
      } else {
        throw new Error(
          `OrgMemory OAuth state is unreadable at ${this.stateFile}. Remove it and sign in again.`,
          { cause: error },
        )
      }
    }
    return this.persisted
  }

  private async save(state: PersistedOAuthState): Promise<void> {
    this.persisted = state
    await mkdir(dirname(this.stateFile), { recursive: true, mode: 0o700 })
    const temporary = `${this.stateFile}.${process.pid}.${randomBytes(6).toString("hex")}.tmp`
    try {
      await writeFile(temporary, `${JSON.stringify(state, null, 2)}\n`, {
        encoding: "utf8",
        mode: 0o600,
      })
      await rename(temporary, this.stateFile)
    } finally {
      await rm(temporary, { force: true })
    }
  }
}

class OAuthCallback {
  private server: Server | undefined
  private codePromise: Promise<string> | undefined
  private resolveCode: ((code: string) => void) | undefined
  private rejectCode: ((error: Error) => void) | undefined

  constructor(
    private readonly port: number,
    private readonly expectedState: () => string | undefined,
  ) {}

  get url(): string {
    return `http://127.0.0.1:${this.port}/oauth/callback`
  }

  async start(): Promise<void> {
    if (this.server) return
    this.codePromise = new Promise<string>((resolve, reject) => {
      this.resolveCode = resolve
      this.rejectCode = reject
    })
    this.server = createServer((request, response) => {
      const url = new URL(request.url ?? "/", this.url)
      if (request.method !== "GET" || url.pathname !== "/oauth/callback") {
        response.writeHead(404).end()
        return
      }
      const error = url.searchParams.get("error")
      const code = url.searchParams.get("code")
      const state = url.searchParams.get("state")
      if (error) {
        response.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" })
        response.end("OrgMemory authorization failed. Return to the terminal.")
        this.rejectCode?.(new Error(`OAuth authorization failed: ${error}`))
        return
      }
      if (!code || !state || state !== this.expectedState()) {
        response.writeHead(400, { "Content-Type": "text/plain; charset=utf-8" })
        response.end("Invalid OrgMemory authorization callback.")
        this.rejectCode?.(new Error("OAuth callback state or code is invalid"))
        return
      }
      response.writeHead(200, { "Content-Type": "text/html; charset=utf-8" })
      response.end(
        "<!doctype html><title>OrgMemory connected</title><p>OrgMemory is connected. You can close this window.</p>",
      )
      this.resolveCode?.(code)
    })
    await new Promise<void>((resolve, reject) => {
      this.server?.once("error", reject)
      this.server?.listen(this.port, "127.0.0.1", () => resolve())
    })
  }

  async wait(): Promise<string> {
    if (!this.codePromise) {
      throw new Error("OAuth callback server was not started")
    }
    return this.codePromise
  }

  async close(): Promise<void> {
    if (!this.server) return
    const server = this.server
    this.server = undefined
    await new Promise<void>((resolve, reject) => {
      server.close((error) => (error ? reject(error) : resolve()))
    })
  }
}

function oauthStatePath(serverUrl: URL): string {
  const identity = createHash("sha256").update(serverUrl.origin).digest("hex").slice(0, 16)
  return join(userStateDirectory(), `oauth-${identity}.json`)
}

function userStateDirectory(): string {
  if (platform() === "win32") {
    return join(process.env.LOCALAPPDATA || join(homedir(), "AppData", "Local"), "OrgMemory", "cli")
  }
  if (platform() === "darwin") {
    return join(homedir(), "Library", "Application Support", "OrgMemory", "cli")
  }
  return join(process.env.XDG_STATE_HOME || join(homedir(), ".local", "state"), "orgmemory")
}

async function openBrowser(url: URL): Promise<boolean> {
  const command =
    platform() === "win32"
      ? { file: "rundll32.exe", args: ["url.dll,FileProtocolHandler", url.toString()] }
      : platform() === "darwin"
        ? { file: "open", args: [url.toString()] }
        : { file: "xdg-open", args: [url.toString()] }
  return new Promise((resolve) => {
    const child = spawn(command.file, command.args, {
      detached: true,
      stdio: "ignore",
      windowsHide: true,
    })
    child.once("error", () => resolve(false))
    child.once("spawn", () => {
      child.unref()
      resolve(true)
    })
  })
}
