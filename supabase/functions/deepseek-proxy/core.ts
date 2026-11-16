export type ChatMessage = {
  role: "user" | "assistant";
  content: string;
};

type Dependencies = {
  userId?: string;
  providerKey?: string;
  consumeRateLimit: (userId: string) => Promise<boolean>;
  fetchImpl?: typeof fetch;
};

const PROVIDER_URL = "https://api.deepseek.com/chat/completions";
const MODEL = "deepseek-flash";
const MAX_REQUEST_BYTES = 16 * 1024;
const MAX_RESPONSE_BYTES = 32 * 1024;
const MAX_MESSAGES = 20;
const MAX_MESSAGE_CHARS = 4_000;
const MAX_TOTAL_CHARS = 12_000;
const MAX_OUTPUT_TOKENS = 1_024;
const UPSTREAM_TIMEOUT_MS = 25_000;

function json(body: unknown, status: number): Response {
  return new Response(JSON.stringify(body), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
    },
  });
}

async function readLimited(stream: ReadableStream<Uint8Array> | null, limit: number): Promise<string> {
  if (!stream) return "";
  const reader = stream.getReader();
  const chunks: Uint8Array[] = [];
  let total = 0;

  try {
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      total += value.byteLength;
      if (total > limit) {
        await reader.cancel();
        throw new RangeError("body_too_large");
      }
      chunks.push(value);
    }
  } finally {
    reader.releaseLock();
  }

  const bytes = new Uint8Array(total);
  let offset = 0;
  for (const chunk of chunks) {
    bytes.set(chunk, offset);
    offset += chunk.byteLength;
  }
  return new TextDecoder("utf-8", { fatal: true }).decode(bytes);
}

function validateMessages(value: unknown): ChatMessage[] | null {
  if (!Array.isArray(value) || value.length < 1 || value.length > MAX_MESSAGES) return null;

  const messages: ChatMessage[] = [];
  let totalChars = 0;
  let hasUserMessage = false;

  for (const item of value) {
    if (!item || typeof item !== "object" || Array.isArray(item)) return null;
    const candidate = item as Record<string, unknown>;
    if (candidate.role !== "user" && candidate.role !== "assistant") return null;
    if (typeof candidate.content !== "string") return null;

    const content = candidate.content;
    if (!content.trim() || content.length > MAX_MESSAGE_CHARS) return null;
    totalChars += content.length;
    if (totalChars > MAX_TOTAL_CHARS) return null;

    if (candidate.role === "user") hasUserMessage = true;
    messages.push({ role: candidate.role, content });
  }

  return hasUserMessage ? messages : null;
}

export function createDeepSeekProxyHandler(dependencies: Dependencies) {
  const fetchImpl = dependencies.fetchImpl ?? fetch;

  return async (request: Request): Promise<Response> => {
    if (request.method !== "POST") return json({ error: "method_not_allowed" }, 405);
    if (!dependencies.userId) return json({ error: "authentication_required" }, 401);

    const contentType = request.headers.get("content-type")?.split(";")[0].trim().toLowerCase();
    if (contentType !== "application/json") return json({ error: "content_type_must_be_json" }, 415);

    let rawBody: string;
    try {
      rawBody = await readLimited(request.body, MAX_REQUEST_BYTES);
    } catch (error) {
      if (error instanceof RangeError) return json({ error: "request_too_large" }, 413);
      return json({ error: "invalid_body" }, 400);
    }

    let input: Record<string, unknown>;
    try {
      const parsed: unknown = JSON.parse(rawBody);
      if (!parsed || typeof parsed !== "object" || Array.isArray(parsed)) {
        return json({ error: "invalid_payload" }, 400);
      }
      input = parsed as Record<string, unknown>;
    } catch {
      return json({ error: "invalid_json" }, 400);
    }

    const messages = validateMessages(input.messages);
    if (!messages) return json({ error: "invalid_messages" }, 400);
    if (!dependencies.providerKey) return json({ error: "provider_not_configured" }, 503);

    try {
      const allowed = await dependencies.consumeRateLimit(dependencies.userId);
      if (!allowed) return json({ error: "rate_limit_exceeded" }, 429);
    } catch {
      return json({ error: "rate_limit_unavailable" }, 503);
    }

    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), UPSTREAM_TIMEOUT_MS);
    const abortFromCaller = () => controller.abort();
    request.signal.addEventListener("abort", abortFromCaller, { once: true });

    try {
      const upstream = await fetchImpl(PROVIDER_URL, {
        method: "POST",
        redirect: "manual",
        signal: controller.signal,
        headers: {
          "authorization": `Bearer ${dependencies.providerKey}`,
          "content-type": "application/json",
          "accept": "application/json",
        },
        body: JSON.stringify({
          model: MODEL,
          messages,
          max_tokens: MAX_OUTPUT_TOKENS,
          temperature: 0.7,
          stream: false,
        }),
      });

      if (upstream.status >= 300 && upstream.status < 400) {
        return json({ error: "provider_redirect_rejected" }, 502);
      }
      if (!upstream.ok) return json({ error: "provider_unavailable" }, 502);

      let responseText: string;
      try {
        responseText = await readLimited(upstream.body, MAX_RESPONSE_BYTES);
      } catch {
        return json({ error: "provider_response_too_large" }, 502);
      }

      let providerResponse: unknown;
      try {
        providerResponse = JSON.parse(responseText);
      } catch {
        return json({ error: "invalid_provider_response" }, 502);
      }

      const choices = (providerResponse as { choices?: unknown })?.choices;
      const first = Array.isArray(choices) ? choices[0] : null;
      const message = first && typeof first === "object"
        ? (first as { message?: unknown }).message
        : null;
      const reply = message && typeof message === "object"
        ? (message as { content?: unknown }).content
        : null;

      if (typeof reply !== "string" || !reply.trim() || reply.length > 16_000) {
        return json({ error: "invalid_provider_response" }, 502);
      }

      return json({ reply, model: MODEL }, 200);
    } catch (error) {
      if (controller.signal.aborted || (error instanceof DOMException && error.name === "AbortError")) {
        return json({ error: "provider_timeout" }, 504);
      }
      return json({ error: "provider_unavailable" }, 502);
    } finally {
      clearTimeout(timeout);
      request.signal.removeEventListener("abort", abortFromCaller);
    }
  };
}
