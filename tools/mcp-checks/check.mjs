import assert from 'node:assert/strict';
import { createChecks } from './server.mjs';

async function run(server) {
  let id = 0;
  let session;
  async function rpc(method, params) {
    const response = await server.fetch(new Request('http://localhost/mcp', {
      method: 'POST', headers: {
        'Content-Type': 'application/json', Accept: 'application/json, text/event-stream',
        ...(session ? { 'mcp-session-id': session } : {}),
      }, body: JSON.stringify({ jsonrpc: '2.0', id: ++id, method, params }),
    }));
    session ||= response.headers.get('mcp-session-id');
    const text = await response.text();
    const message = text.startsWith('data:') || text.includes('\ndata:')
      ? JSON.parse(text.split('\n').find(line => line.startsWith('data:')).slice(5)) : JSON.parse(text);
    assert.equal(message.error, undefined, JSON.stringify(message.error));
    return message.result;
  }
  try {
    await rpc('initialize', { protocolVersion: '2025-03-26', capabilities: {}, clientInfo: { name: 'oralyvo-check', version: '1.0.0' } });
    const tools = await rpc('tools/list', {});
    assert.equal(tools.tools.length, 2);
    const source = await rpc('tools/call', { name: 'inspect-ios-entry', arguments: {} });
    assert.equal(source.structuredContent.placeholder, false);
    const health = await rpc('tools/call', { name: 'check-backend', arguments: {} });
    return health;
  } finally { await server.close(); }
}

const live = process.argv.includes('--live');
const server = createChecks(live ? {} : { fetchHealth: async () => Response.json({
  ok: true, accounts: true, aiAvailable: true, liveAI: false, turnstileConfigured: true,
  supabaseUrl: 'must-not-be-returned', supabasePublishableKey: 'must-not-be-returned',
}) });
const health = await run(server);
if (live) {
  console.log(JSON.stringify(health));
  if (health.isError || !health.structuredContent?.ok) process.exitCode = 1;
} else {
  assert.deepEqual(health.structuredContent, { ok: true, accounts: true, aiAvailable: true, liveAI: false, turnstileConfigured: true });
  assert.ok(!JSON.stringify(health).includes('must-not-be-returned'));
  const unavailable = await run(createChecks({ fetchHealth: async () => { throw new Error('private diagnostic'); } }));
  assert.equal(unavailable.isError, true);
  assert.ok(!JSON.stringify(unavailable).includes('private diagnostic'));
  console.log('MCP protocol checks passed: tools, source inspection, safe health output, network failure.');
}
