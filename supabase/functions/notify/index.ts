// SmartFamily push sender (Firebase Cloud Messaging, free).
// Called by a database trigger when a chat message is inserted. It sends a *data-only* wake-up
// (chat id, no text) to the other members' phones; the phone syncs and decrypts locally, so
// end-to-end encryption is kept: neither Google nor this function ever sees message content.
import { createClient } from "jsr:@supabase/supabase-js@2";

const SB_URL = Deno.env.get("SUPABASE_URL")!;
const SB_KEY = Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!;
const db = createClient(SB_URL, SB_KEY, { auth: { persistSession: false } });

function b64url(data: ArrayBuffer | Uint8Array | string): string {
  const bytes = typeof data === "string" ? new TextEncoder().encode(data) : new Uint8Array(data);
  let s = ""; for (const b of bytes) s += String.fromCharCode(b);
  return btoa(s).replace(/\+/g, "-").replace(/\//g, "_").replace(/=+$/, "");
}

function serviceAccount(): { project_id: string; client_email: string; private_key: string } | null {
  const raw = Deno.env.get("FCM_SERVICE_ACCOUNT");
  if (!raw) return null;
  try { return JSON.parse(raw); } catch { return null; }
}

let cached: { token: string; exp: number } | null = null;
async function accessToken(sa: { client_email: string; private_key: string }): Promise<string> {
  const now = Math.floor(Date.now() / 1000);
  if (cached && cached.exp > now + 60) return cached.token;
  const header = b64url(JSON.stringify({ alg: "RS256", typ: "JWT" }));
  const claims = b64url(JSON.stringify({
    iss: sa.client_email, scope: "https://www.googleapis.com/auth/firebase.messaging",
    aud: "https://oauth2.googleapis.com/token", iat: now, exp: now + 3600,
  }));
  const pem = sa.private_key.replace(/-----[^-]+-----/g, "").replace(/\s+/g, "");
  const der = Uint8Array.from(atob(pem), (c) => c.charCodeAt(0));
  const key = await crypto.subtle.importKey("pkcs8", der, { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" }, false, ["sign"]);
  const sig = await crypto.subtle.sign("RSASSA-PKCS1-v1_5", key, new TextEncoder().encode(`${header}.${claims}`));
  const jwt = `${header}.${claims}.${b64url(sig)}`;
  const r = await fetch("https://oauth2.googleapis.com/token", {
    method: "POST", headers: { "Content-Type": "application/x-www-form-urlencoded" },
    body: `grant_type=urn:ietf:params:oauth:grant-type:jwt-bearer&assertion=${jwt}`,
  });
  const j = await r.json();
  if (!j.access_token) throw new Error("oauth: " + (j.error_description ?? j.error ?? r.status));
  cached = { token: j.access_token, exp: now + (j.expires_in ?? 3600) };
  return cached.token;
}

Deno.serve(async (req) => {
  const sa = serviceAccount();
  const url = new URL(req.url);
  // health check: says whether the Firebase key is configured, without revealing it
  if (url.searchParams.get("check") === "1") {
    let oauth = "not tried";
    if (sa) { try { await accessToken(sa); oauth = "ok"; } catch (e) { oauth = String(e).slice(0, 120); } }
    const { count } = await db.from("push_tokens").select("id", { count: "exact", head: true }).is("deleted_at", null);
    return Response.json({ fcm_key: sa ? "present" : "missing", firebase_project: sa?.project_id ?? null, oauth, phones: count ?? 0 });
  }
  // only the database trigger may call: it sends the shared hook secret kept in the database
  const hook = req.headers.get("x-sf-hook") ?? "";
  const { data: ok } = await db.rpc("sf_hook_ok", { secret: hook });
  if (ok !== true) return new Response("forbidden", { status: 403 });
  if (!sa) return Response.json({ sent: 0, reason: "FCM_SERVICE_ACCOUNT missing" });

  const body = await req.json();
  const chatId: string = body.chat_id, sender: string = body.sender_id;
  const { data: chat } = await db.from("chats").select("members,family_id").eq("id", chatId).maybeSingle();
  if (!chat) return Response.json({ sent: 0, reason: "no chat" });
  const members = String(chat.members ?? "").split(",").filter((m: string) => m && m !== sender);
  if (members.length === 0) return Response.json({ sent: 0 });
  const { data: tokens } = await db.from("push_tokens").select("id,token").in("user_id", members).is("deleted_at", null);
  const at = await accessToken(sa);
  let sent = 0;
  for (const t of tokens ?? []) {
    const r = await fetch(`https://fcm.googleapis.com/v1/projects/${sa.project_id}/messages:send`, {
      method: "POST", headers: { Authorization: `Bearer ${at}`, "Content-Type": "application/json" },
      body: JSON.stringify({ message: { token: t.token, data: { type: "chat", chat_id: chatId }, android: { priority: "HIGH" } } }),
    });
    if (r.ok) sent++;
    else {
      const err = await r.text();
      // the phone uninstalled the app or the token expired: stop sending to it
      if (err.includes("UNREGISTERED") || err.includes("registration-token-not-registered")) {
        await db.from("push_tokens").update({ deleted_at: Date.now() }).eq("id", t.id);
      }
    }
  }
  return Response.json({ sent, of: tokens?.length ?? 0 });
});
