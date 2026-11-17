import { createHandler, type EnvironmentReader, type Fetcher } from "./index.ts";
function equal(actual: unknown, expected: unknown, label: string) {
  if (actual !== expected) throw new Error(label + ": expected " + String(expected) + ", got " + String(actual));
}
function assert(value: unknown, label: string): asserts value { if (!value) throw new Error(label); }
const env = (overrides: Record<string,string> = {}): EnvironmentReader => {
  const values: Record<string,string> = {
    SUPABASE_URL: "https://supabase.test",
    SUPABASE_PUBLISHABLE_KEYS: JSON.stringify({default:"sb_publishable_test"}),
    SUPABASE_SECRET_KEYS: JSON.stringify({default:"sb_secret_test"}),
    ELEVENLABS_API_KEY: "test-elevenlabs-secret",
    ...overrides,
  };
  return (name) => values[name];
};
const body = JSON.stringify({text:"Hello from Miss Minutes"});
function request(payload = body, headers: Record<string,string> = {
  authorization:"Bearer user-jwt", "content-type":"application/json",
}) {
  return new Request("https://function.test/miss-minutes-tts", {method:"POST",headers,body:payload});
}
function fakeFetcher(options: {auth?:number; quota?:boolean; upstream?:number} = {}): Fetcher {
  return async (input, init) => {
    const url = String(input);
    if (url === "https://supabase.test/auth/v1/user") {
      return Response.json({id:"user-1"},{status:options.auth ?? 200});
    }
    if (url === "https://supabase.test/rest/v1/rpc/consume_miss_minutes_tts_rate_limit") {
      const headers = new Headers(init?.headers);
      equal(headers.get("apikey"),"sb_secret_test","service key header");
      equal(headers.get("authorization"),null,"sb_secret must not be Bearer");
      const args = JSON.parse(String(init?.body));
      equal(args.p_user_id,"user-1","quota user");
      equal(args.p_limit,5,"quota limit");
      return Response.json(options.quota ?? true);
    }
    if (url.startsWith("https://api.elevenlabs.io/v1/text-to-speech/")) {
      const headers = new Headers(init?.headers);
      equal(headers.get("xi-api-key"),"test-elevenlabs-secret","ElevenLabs server key");
      const payload = JSON.parse(String(init?.body));
      equal(payload.model_id,"eleven_multilingual_v2","model");
      equal(payload.text,"Hello from Miss Minutes","text");
      return new Response(new Uint8Array([1,2,3]),{
        status:options.upstream ?? 200,headers:{"content-type":"audio/mpeg"},
      });
    }
    throw new Error("Unexpected URL " + url);
  };
}
Deno.test("requires bearer authentication", async () => {
  const handler = createHandler({env:env(),fetcher:fakeFetcher()});
  equal((await handler(request(body,{"content-type":"application/json"}))).status,401,"status");
});
Deno.test("rejects invalid Supabase sessions", async () => {
  const handler = createHandler({env:env(),fetcher:fakeFetcher({auth:401})});
  equal((await handler(request())).status,401,"status");
});
Deno.test("validates text input and character bounds", async () => {
  const handler = createHandler({env:env(),fetcher:fakeFetcher()});
  equal((await handler(request(JSON.stringify({text:"  "})))) .status,400,"empty");
  equal((await handler(request(JSON.stringify({text:"x".repeat(3001)})))).status,400,"oversize text");
});
Deno.test("requires ElevenLabs secret before consuming quota", async () => {
  let quotaCalls = 0;
  const fetcher: Fetcher = async (input) => {
    if (String(input) === "https://supabase.test/auth/v1/user") return Response.json({id:"user-1"});
    if (String(input).includes("/rpc/")) quotaCalls++;
    throw new Error("unexpected provider call");
  };
  const handler = createHandler({env:env({ELEVENLABS_API_KEY:""}),fetcher});
  equal((await handler(request())).status,503,"status");
  equal(quotaCalls,0,"quota must not be consumed");
});
Deno.test("returns MP3 bytes and does not expose credentials", async () => {
  const handler = createHandler({env:env(),fetcher:fakeFetcher()});
  const response = await handler(request());
  equal(response.status,200,"status");
  equal(response.headers.get("content-type"),"audio/mpeg","content type");
  equal((await response.arrayBuffer()).byteLength,3,"audio bytes");
});
Deno.test("enforces quota before calling ElevenLabs", async () => {
  let providerCalls = 0;
  const fetcher: Fetcher = async (input) => {
    const url = String(input);
    if (url === "https://supabase.test/auth/v1/user") return Response.json({id:"user-1"});
    if (url.includes("/rpc/")) return Response.json(false);
    if (url.includes("api.elevenlabs.io")) providerCalls++;
    throw new Error("unexpected URL");
  };
  const handler = createHandler({env:env(),fetcher});
  equal((await handler(request())).status,429,"status");
  equal(providerCalls,0,"provider must not be called");
});
Deno.test("normalizes provider errors", async () => {
  const handler = createHandler({env:env(),fetcher:fakeFetcher({upstream:401})});
  const response = await handler(request());
  equal(response.status,502,"status");
  assert(!(await response.text()).includes("test-elevenlabs-secret"),"no secret in response");
});
Deno.test("rejects oversized request bodies", async () => {
  const handler = createHandler({env:env(),fetcher:fakeFetcher()});
  equal((await handler(request(" ".repeat(16001)))).status,413,"status");
});
Deno.test("handles CORS preflight without calling services", async () => {
  const handler = createHandler({env:env(),fetcher:async()=>{throw new Error("unexpected fetch");}});
  equal((await handler(new Request("https://function.test",{method:"OPTIONS"}))).status,204,"status");
});
