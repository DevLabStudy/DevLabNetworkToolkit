import { createClient } from "https://esm.sh/@supabase/supabase-js@2";

const corsHeaders = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Headers": "authorization, x-client-info, apikey, content-type",
  "Access-Control-Allow-Methods": "POST, OPTIONS",
};

const json = (body: Record<string, unknown>, status = 200) =>
  new Response(JSON.stringify(body), {
    status,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });

Deno.serve(async (request) => {
  if (request.method === "OPTIONS") {
    return new Response("ok", { headers: corsHeaders });
  }

  if (request.method !== "POST") {
    return json({ message: "Metoda niedozwolona." }, 405);
  }

  const { code, device_id: deviceId } = await request.json().catch(() => ({}));
  const normalizedCode = typeof code === "string" ? code.trim().toUpperCase() : "";

  if (!normalizedCode || typeof deviceId !== "string" || !deviceId.trim()) {
    return json({ message: "Kod licencji i identyfikator urządzenia są wymagane." }, 400);
  }

  const admin = createClient(
    Deno.env.get("SUPABASE_URL")!,
    Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!,
    { auth: { autoRefreshToken: false, persistSession: false } },
  );

  const { data: existing, error: lookupError } = await admin
    .from("license_keys")
    .select("code, is_used, device_id")
    .eq("code", normalizedCode)
    .maybeSingle();

  if (lookupError) {
    return json({ message: "Nie udało się sprawdzić licencji." }, 500);
  }

  if (!existing) {
    return json({ message: "Nieprawidłowy kod licencji." }, 404);
  }

  if (existing.is_used) {
    return existing.device_id === deviceId.trim()
      ? json({ message: "Przywrócono licencję PRO." })
      : json({ message: "Ten klucz został już użyty na innym urządzeniu." }, 409);
  }

  const { data: activated, error: updateError } = await admin
    .from("license_keys")
    .update({
      is_used: true,
      device_id: deviceId.trim(),
      activated_at: new Date().toISOString(),
    })
    .eq("code", normalizedCode)
    .eq("is_used", false)
    .select("code")
    .maybeSingle();

  if (updateError) {
    return json({ message: "Nie udało się aktywować licencji." }, 500);
  }

  if (!activated) {
    return json({ message: "Klucz został właśnie aktywowany na innym urządzeniu." }, 409);
  }

  return json({ message: "Licencja PRO aktywowana pomyślnie." });
});
