import server from './server.mjs';
const { url } = await server.listen(3000, { host: '127.0.0.1' });
console.log(`Oralyvo development MCP: ${url}`);
