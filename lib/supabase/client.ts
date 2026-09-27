// ※ 지금(실습 단계)은 쓰지 않는다. 나중에 서버(Supabase)를 붙일 때 쓴다 (docs/login-setup.md)
import { createBrowserClient } from "@supabase/ssr";

export function createClient() {
  return createBrowserClient(
    process.env.NEXT_PUBLIC_SUPABASE_URL!,
    process.env.NEXT_PUBLIC_SUPABASE_PUBLISHABLE_KEY!,
  );
}
