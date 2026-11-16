import { withSupabase } from "npm:@supabase/server@1";
import { createDeepSeekProxyHandler } from "./core.ts";

export default {
  fetch: withSupabase({ auth: "user" }, async (request, context) => {
    const userId = context.userClaims?.sub;
    const providerKey = Deno.env.get("DEEPSEEK_API_KEY");

    const handler = createDeepSeekProxyHandler({
      userId,
      providerKey,
      consumeRateLimit: async (authenticatedUserId) => {
        const { data, error } = await context.supabaseAdmin.rpc(
          "consume_ai_request_limit",
          {
            p_user_id: authenticatedUserId,
            p_limit: 20,
            p_window_seconds: 60,
          },
        );
        if (error) throw new Error("rate_limit_unavailable");
        return data === true;
      },
    });

    return handler(request);
  }),
};
