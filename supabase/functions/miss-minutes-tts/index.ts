export type EnvironmentReader = (name: string) => string | undefined;
export type Fetcher = (input: RequestInfo | URL, init?: RequestInit) => Promise<Response>;
type Dependencies = { env: EnvironmentReader; fetcher: Fetcher };

const VOICE_ID = "onwK4e9ZLuTAKqWW03F9"; // Daniel, natural male voice
const MODEL_ID = "eleven_multilingual_v2";
const MAX_TEXT_CHARS = 3000;
const MAX_BODY_BYTES = 12000;
const MAX_AUDIO_BYTES = 5 * 1024 * 1024;
const CORS = {
  "access-control-allow-origin": "*",
  "access-control-allow-headers": "authorization, apikey, content-type",
  "access-control-allow-methods": "POST, OPTIONS",
  "cache-control": "no-store",
};
function json(status: number, value: unknown): Response {
  return new Response(JSON.stringify(value), {
    status, headers: { ...CORS, "content-type": "application/json; charset=utf-8" },
  });
}
function publishable(env: EnvironmentReader): string | undefined {
  const raw = env("SUPABASE_PUBLISHABLE_KEYS");
  if (raw) {
    try {
      const key = (JSON.parse(raw) as Record<string, unknown>).default;
      if (typeof key === "string" && key) return key;
    } catch { return undefined; }
  }
  return env("SUPABASE_ANON_KEY");
}
function serviceKey(env: EnvironmentReader): string | undefined {
  const raw = env("SUPABASE_SECRET_KEYS");
  if (raw) {
    try {
      const key = (JSON.parse(raw) as Record<string, unknown>).default;
      if (typeof key === "string" && key) return key;
    } catch { return undefined; }
  }
  return env("SUPABASE_SERVICE_ROLE_KEY");
}
function serviceHeaders(key: string): Headers {
  const headers = new Headers({ apikey: key, "content-type": "application/json" });
  if (!key.startsWith("sb_secret_")) headers.set("authorization", "Bearer " + key);
  return headers;
}
function bearer(value: string | null): string | undefined {
  return value ? /^Bearer\s+([^\s]+)$/i.exec(value.trim())?.[1] : undefined;
}
async function readBody(request: Request): Promise<string | undefined> {
  if (!request.body) return "";
  const reader = request.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const item = await reader.read();
      if (item.done) break;
      total += item.value.byteLength;
      if (total > MAX_BODY_BYTES) { await reader.cancel(); return undefined; }
      chunks.push(item.value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return new TextDecoder().decode(bytes);
}
async function readAudio(response: Response): Promise<Uint8Array | undefined> {
  if (!response.body) return undefined;
  const declared = Number(response.headers.get("content-length") || "0");
  if (declared > MAX_AUDIO_BYTES) return undefined;
  const reader = response.body.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;
  try {
    while (true) {
      const item = await reader.read();
      if (item.done) break;
      total += item.value.byteLength;
      if (total > MAX_AUDIO_BYTES) { await reader.cancel(); return undefined; }
      chunks.push(item.value);
    }
  } finally { reader.releaseLock(); }
  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) { bytes.set(chunk, offset); offset += chunk.byteLength; }
  return bytes;
}
export function createHandler(deps: Dependencies): (request: Request) => Promise<Response> {
  return async (request) => {
    if (request.method === "OPTIONS") return new Response(null, { status: 204, headers: CORS });
    if (request.method !== "POST") return json(405, { error: "method_not_allowed" });
    const token = bearer(request.headers.get("authorization"));
    if (!token) return json(401, { error: "authentication_required" });
    const url = deps.env("SUPABASE_URL");
    const publicKey = publishable(deps.env);
    if (!url || !publicKey) return json(503, { error: "backend_auth_not_configured" });

    let raw: string | undefined;
    try { raw = await readBody(request); } catch { return json(400, { error: "invalid_request_body" }); }
    if (raw === undefined) return json(413, { error: "request_too_large" });
    let parsed: unknown;
    try { parsed = JSON.parse(raw); } catch { return json(400, { error: "invalid_json" }); }
    if (!parsed || typeof parsed !== "object") return json(400, { error: "invalid_request" });
    const text = (parsed as Record<string, unknown>).text;
    if (typeof text !== "string" || !text.trim() || text.trim().length > MAX_TEXT_CHARS) {
      return json(400, { error: "invalid_text" });
    }

    let auth: Response;
    try {
      auth = await deps.fetcher(new URL("/auth/v1/user", url), {
        method: "GET",
        headers: { authorization: "Bearer " + token, apikey: publicKey },
        signal: AbortSignal.timeout(10000),
      });
    } catch { return json(503, { error: "authentication_service_unavailable" }); }
    if (!auth.ok) return json(401, { error: "invalid_session" });
    let user: { id?: unknown };
    try { user = await auth.json(); } catch { return json(401, { error: "invalid_session" }); }
    if (typeof user.id !== "string" || !user.id) return json(401, { error: "invalid_session" });

    const elevenKey = deps.env("ELEVENLABS_API_KEY");
    if (!elevenKey) return json(503, { error: "tts_provider_not_configured" });
    const service = serviceKey(deps.env);
    if (!service) return json(503, { error: "tts_quota_not_configured" });
    let quota: Response;
    try {
      quota = await deps.fetcher(new URL("/rest/v1/rpc/consume_miss_minutes_tts_rate_limit", url), {
        method: "POST",
        headers: serviceHeaders(service),
        body: JSON.stringify({ p_user_id: user.id, p_limit: 5 }),
        signal: AbortSignal.timeout(10000),
      });
    } catch { return json(503, { error: "tts_quota_unavailable" }); }
    if (!quota.ok) return json(503, { error: "tts_quota_unavailable" });
    let allowed: unknown;
    try { allowed = await quota.json(); } catch { return json(503, { error: "tts_quota_unavailable" }); }
    if (allowed !== true) return json(429, { error: "tts_rate_limited", retry_after_seconds: 60 });

    try {
      const upstream = await deps.fetcher(
        "https://api.elevenlabs.io/v1/text-to-speech/" + VOICE_ID + "?output_format=mp3_44100_128",
        {
          method: "POST",
          headers: { "xi-api-key": elevenKey, "content-type": "application/json", accept: "audio/mpeg" },
          body: JSON.stringify({
            text: text.trim(),
            model_id: MODEL_ID,
            voice_settings: { stability: 0.48, similarity_boost: 0.82, style: 0.18, use_speaker_boost: true },
          }),
          signal: AbortSignal.timeout(60000),
        },
      );
      if (upstream.status === 429) return json(429, { error: "tts_provider_rate_limited" });
      if (!upstream.ok) return json(502, { error: "tts_provider_unavailable" });
      if (!(upstream.headers.get("content-type") || "").toLowerCase().startsWith("audio/")) {
        return json(502, { error: "invalid_tts_response" });
      }
      const audio = await readAudio(upstream);
      if (!audio || audio.length === 0) return json(502, { error: "invalid_tts_response" });
      const audioBuffer = new ArrayBuffer(audio.byteLength);
      new Uint8Array(audioBuffer).set(audio);
      return new Response(audioBuffer, {
        status: 200,
        headers: { ...CORS, "content-type": "audio/mpeg", "content-length": String(audio.length) },
      });
    } catch (error) {
      if (error instanceof Error && error.name === "TimeoutError") return json(504, { error: "tts_provider_timeout" });
      return json(502, { error: "tts_provider_unavailable" });
    }
  };
}
if (import.meta.main) Deno.serve(createHandler({ env: (name) => Deno.env.get(name), fetcher: globalThis.fetch }));
