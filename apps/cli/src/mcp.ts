import { Client } from "@modelcontextprotocol/sdk/client/index.js"
import { UnauthorizedError } from "@modelcontextprotocol/sdk/client/auth.js"
import { StreamableHTTPClientTransport } from "@modelcontextprotocol/sdk/client/streamableHttp.js"
import type { z } from "zod"

import { FileOAuthClientProvider } from "./oauth.js"
import { CLI_VERSION } from "./version.js"

export class OrgMemoryMcpClient implements AsyncDisposable {
  private client: Client | undefined
  private transport: StreamableHTTPClientTransport | undefined
  readonly oauth: FileOAuthClientProvider

  constructor(
    readonly serverUrl: URL,
    callbackPort: number,
    scope = "assets:read",
  ) {
    this.oauth = new FileOAuthClientProvider(serverUrl, callbackPort, scope)
  }

  async connect(): Promise<void> {
    await this.connectAttempt(true)
  }

  async call<T extends z.ZodType>(
    name: string,
    args: Record<string, unknown>,
    schema: T,
  ): Promise<z.output<T>> {
    if (!this.client) throw new Error("OrgMemory MCP is not connected")
    const result = await this.client.callTool({ name, arguments: args })
    if ("toolResult" in result) {
      throw new Error("OrgMemory returned an unsupported task-based tool result")
    }
    if (result.isError) {
      throw new Error(toolError(result))
    }
    return schema.parse(toolPayload(result))
  }

  async accessToken(): Promise<string> {
    return this.oauth.accessToken()
  }

  async close(): Promise<void> {
    await this.oauth.closeCallback()
    if (this.transport) await this.transport.close()
    this.transport = undefined
    this.client = undefined
  }

  async [Symbol.asyncDispose](): Promise<void> {
    await this.close()
  }

  private async connectAttempt(allowAuthorization: boolean): Promise<void> {
    const client = new Client(
      { name: "orgmemory-cli", version: CLI_VERSION },
      { capabilities: {} },
    )
    const transport = new StreamableHTTPClientTransport(this.serverUrl, {
      authProvider: this.oauth,
    })
    try {
      await client.connect(transport)
      this.client = client
      this.transport = transport
    } catch (error) {
      if (!(error instanceof UnauthorizedError) || !allowAuthorization) {
        await closeWithoutMasking(transport)
        throw error
      }
      try {
        const code = await this.oauth.waitForAuthorizationCode()
        await transport.finishAuth(code)
      } catch (authorizationFailure) {
        await closeOAuthCallbackWithoutMasking(this.oauth)
        await closeWithoutMasking(transport)
        throw authorizationFailure
      }
      try {
        await this.oauth.closeCallback()
      } catch (callbackCloseFailure) {
        await closeWithoutMasking(transport)
        throw callbackCloseFailure
      }
      await closeWithoutMasking(transport)
      await this.connectAttempt(false)
    }
  }
}

async function closeWithoutMasking(
  transport: StreamableHTTPClientTransport,
): Promise<void> {
  try {
    await transport.close()
  } catch {
    // Preserve the connection or authorization failure that caused cleanup.
  }
}

async function closeOAuthCallbackWithoutMasking(
  oauth: FileOAuthClientProvider,
): Promise<void> {
  try {
    await oauth.closeCallback()
  } catch {
    // Preserve the authorization failure that caused callback cleanup.
  }
}

type ToolResult = {
  content: Array<{ type: string; text?: string }>
  structuredContent?: Record<string, unknown>
}

function toolPayload(result: ToolResult): unknown {
  if (result.structuredContent) return result.structuredContent
  const text = result.content.find((item) => item.type === "text")
  if (!text?.text) throw new Error("OrgMemory returned no structured tool result")
  try {
    return JSON.parse(text.text) as unknown
  } catch (error) {
    throw new Error("OrgMemory returned an invalid tool result", { cause: error })
  }
}

function toolError(result: ToolResult): string {
  const text = result.content.find((item) => item.type === "text")
  return text?.text ?? "OrgMemory rejected the tool request"
}
