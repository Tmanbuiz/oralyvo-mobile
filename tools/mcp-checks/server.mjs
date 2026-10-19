// Supported mcp-use opt-out, set before loading the SDK.
process.env.MCP_USE_ANONYMIZED_TELEMETRY = 'false';
const { MCPServer } = await import('mcp-use');
import { z } from 'zod';
import { readFile } from 'node:fs/promises';

// Local development tools only. No account tokens, transcripts, writes or
// caller-controlled URLs are accepted by this server.
export function createChecks({ fetchHealth = () => fetch('https://oralyvo.app/api/health', { signal: AbortSignal.timeout(15000) }) } = {}) {
  const server = new MCPServer({ name: 'oralyvo-development-checks', version: '1.0.0', host: '127.0.0.1' });
  server.tool({
    name: 'check-backend', description: 'Read public Oralyvo deployment readiness without returning account configuration.',
    inputSchema: z.object({}).strict(),
    annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: true },
  }, async () => {
    try {
      const response = await fetchHealth();
      if (!response.ok) throw new Error('Backend unavailable');
      const health = await response.json();
      const safe = Object.fromEntries(['ok', 'accounts', 'aiAvailable', 'liveAI', 'turnstileConfigured'].map(key => [key, health[key] === true]));
      return { content: [{ type: 'text', text: JSON.stringify(safe) }], structuredContent: safe };
    } catch {
      return { isError: true, content: [{ type: 'text', text: 'Could not reach the Oralyvo health endpoint. Check the deployment and network.' }] };
    }
  });
  server.tool({
    name: 'inspect-ios-entry', description: 'Report the iPhone entry screen and available Swift source files in this checkout.',
    inputSchema: z.object({}).strict(),
    annotations: { readOnlyHint: true, destructiveHint: false, openWorldHint: false },
  }, async () => {
    const files = ['ContentView.swift', 'SignInView.swift', 'Services/OralyvoAPI.swift', 'Services/AudioRecorder.swift'];
    const sources = await Promise.all(files.map(path => readFile(new URL('../../ios/OralyvoApp/' + path, import.meta.url), 'utf8')));
    const result = { placeholder: sources[0].includes('iOS app scaffold'), files, validation: 'Source inspection only. Run the macOS iOS build and device checklist for runtime verification.' };
    return { content: [{ type: 'text', text: JSON.stringify(result) }], structuredContent: result };
  });
  return server;
}

export default createChecks();
