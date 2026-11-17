import { createHandler, type EnvironmentReader, type Fetcher } from "./index.ts";

function assert(condition: unknown, message: string): asserts condition {
  if (!condition) throw new Error(message);
}
function equal(actual: unknown, expected: unknown, message: string): void {
  if (actual !== expected) {
    throw new Error(`${message}: expected ${String(expected)}, got ${String(actual)}`);
  }
}
function env(overrides: Record<string, string> = {}): EnvironmentReader {
  const values: Record<string, string> = {
    SUPABASE_URL: "https://supabase.example",
    SUPABASE_PUBLISHABLE_KEYS: JSON.stringify({ default: "sb_publishable_test" }),
    SUPABASE_SECRET_KEYS: JSON.stringify({ default: "sb_secret_test" }),
    ELEVENLABS_API_KEY: "server-only-elevenlabs-secret",
    ...overrides,
  };
  return (name) => values[name];
}
function request(body: unknown = { text: "Hello from Miss Minutes" }, headers?: Record<string, string>): Request {
  return new Request("https://function.test/miss-minutes-tts", {
    method: "POST",
    headers: headers ?? {
      authorization: "Bearer test-user-jwt",
      "content-type": "application/json",
    },
    body: JSON.stringify(body),
  });
}
function fetcher(options: { authenticated?: boolean; quota?: boolean; upstreamStatus?: number } = {}): Fetcher {
  return async (input, init) => {
    const url = String(input);
    if (url === "https://supabase.example/auth/v1/user") {
      return options.authenticated === false
        ? new Response("unauthorized", { status: 401 })
        : Response.json({ id: "test-user" });
    }
    if (url === "https://supabase.example/rest/v1/rpc/consume_miss_minutes_tts_rate_limit") {
      const headers = new Headers(init?.headers);
      equal(headers.get("apikey"), "sb_secret_test", "service secret key");
      equal(headers.get("authorization"), null, "sb_secret must not be sent as a bearer token");
      const payload = JSON.parse(String(init?.body));
      equal(payload.p_user_id, "test-user", "quota user id");
      equal(payload.p_limit, 5, "per-user quota");
      return Response.json(options.quota ?? true);
    }
    if (url.startsWith("https://api.elevenlabs.io/v1/text-to-speech/")) {
      const headers = new Headers(init?.headers);
      equal(headers.get("xi-api-key"), "server-only-elevenlabs-secret", "server-side provider key");
      const payload = JSON.parse(String(init?.body));
      equal(payload.model_id, "eleven_multilingual_v2", "server-selected model");
      equal(payload.text, "Hello from Miss Minutes", "validated text");
      return new Response(new Uint8Array([1, 2, 3, 4]), {
        status: options.upstreamStatus ?? 200,
        headers: { "content-type": "audio/mpeg" },
      });
    }
    throw new Error(`Unexpected URL: ${url}`);
  };
}

Deno.test("rejects unauthenticated requests", async () => {
  const handler = createHandler({ env: env(), fetcher: fetcher() });
  const response = await handler(request({ text: "Hello" }, { "content-type": "application/json" }));
  equal(response.status, 401, "HTTP status");
});

Deno.test("rejects invalid Supabase sessions before quota or ElevenLabs calls", async () => {
  const handler = createHandler({ env: env(), fetcher: fetcher({ authenticated: false }) });
  const response = await handler(request());
  equal(response.status, 401, "HTTP status");
  equal((await response.json()).error, "invalid_session", "normalized error");
});

Deno.test("validates request text and rejects oversized bodies", async () => {
  const handler = createHandler({ env: env(), fetcher: fetcher() });
  const empty = await handler(request({ text: "  " }));
  equal(empty.status, 400, "empty text status");
  const tooLong = await handler(request({ text: "x".repeat(3001) }));
  equal(tooLong.status, 400, "oversized text status");
});

Deno.test("fails closed when ElevenLabs server secret is absent", async () => {
  const handler = createHandler({
    env: env({ ELEVENLABS_API_KEY: "" }),
    fetcher: fetcher(),
  });
  const response = await handler(request());
  equal(response.status, 503, "HTTP status");
  equal((await response.json()).error, "tts_provider_not_configured", "error code");
});

Deno.test("enforces per-user quota before calling ElevenLabs", async () => {
  let providerCalls = 0;
  const base = fetcher({ quota: false });
  const guarded: Fetcher = async (input, init) => {
    if (String(input).startsWith("https://api.elevenlabs.io/")) providerCalls++;
    return base(input, init);
  };
  const handler = createHandler({ env: env(), fetcher: guarded });
  const response = await handler(request());
  equal(response.status, 429, "HTTP status");
  equal(providerCalls, 0, "provider must not run after quota rejection");
});

Deno.test("returns bounded audio bytes and never exposes provider credentials", async () => {
  const handler = createHandler({ env: env(), fetcher: fetcher() });
  const response = await handler(request());
  equal(response.status, 200, "HTTP status");
  equal(response.headers.get("content-type"), "audio/mpeg", "audio content type");
  const bytes = new Uint8Array(await response.arrayBuffer());
  equal(bytes.length, 4, "audio byte count");
  assert(!new TextDecoder().decode(bytes).includes("server-only-elevenlabs-secret"), "secret must not appear in response");
});

Deno.test("maps upstream provider errors to a normalized response", async () => {
  const handler = createHandler({
    env: env(),
    fetcher: fetcher({ upstreamStatus: 429 }),
  });
  const response = await handler(request());
  equal(response.status, 429, "HTTP status");
  equal((await response.json()).error, "tts_provider_rate_limited", "error code");
});

Deno.test("handles CORS preflight without provider access", async () => {
  const handler = createHandler({
    env: env(),
    fetcher: async () => { throw new Error("Preflight must not call dependencies"); },
  });
  const response = await handler(new Request("https://function.test/miss-minutes-tts", { method: "OPTIONS" }));
  equal(response.status, 204, "HTTP status");
});
