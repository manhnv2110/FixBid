// FCM v1 push sender — triggered by a Supabase Database Webhook whenever a row
// is inserted into `public.notifications`. It reads all of the target user's
// device tokens and sends a *data-only* FCM message so the Android client can
// fully control the channel/visuals (see FixBidMessagingService.kt).
//
// Why data-only? FCM `notification`-style payloads are auto-rendered by the
// system and ignore our app's channel + branding. Data messages let our service
// build the notification through AppNotificationManager so foreground (Realtime)
// and background (FCM) UI is identical.
//
// ── Required environment variables ──────────────────────────────────────────
//   FIREBASE_PROJECT_ID         e.g. "fixbid-12345"
//   FIREBASE_CLIENT_EMAIL       service-account email
//   FIREBASE_PRIVATE_KEY        service-account private key (PEM, with \n preserved)
//   SUPABASE_URL                automatically provided in the Edge runtime
//   SUPABASE_SERVICE_ROLE_KEY   automatically provided in the Edge runtime
//
// ── Webhook payload (Supabase DB webhook, INSERT on public.notifications) ──
//   { type: "INSERT", record: { id, user_id, title, body, type, reference_id, ... } }

// deno-lint-ignore-file no-explicit-any

import { create, getNumericDate } from "https://deno.land/x/djwt@v3.0.2/mod.ts";
import { createClient } from "https://esm.sh/@supabase/supabase-js@2.49.4";

// ─── Webhook payload typing ──────────────────────────────────────────────────
interface NotificationRow {
    id: string;
    user_id: string;
    title: string;
    body: string;
    type: string;
    reference_id: string | null;
    is_read: boolean;
    created_at: string;
}

interface WebhookPayload {
    type: "INSERT" | "UPDATE" | "DELETE";
    table: string;
    record: NotificationRow;
}

// ─── Service-account OAuth token (cached in-process per cold start) ─────────
let cachedAccessToken: { token: string; expiresAt: number } | null = null;

async function getAccessToken(): Promise<string> {
    const now = Math.floor(Date.now() / 1000);
    if (cachedAccessToken && cachedAccessToken.expiresAt - 60 > now) {
        return cachedAccessToken.token;
    }

    const clientEmail = Deno.env.get("FIREBASE_CLIENT_EMAIL");
    const privateKeyPem = Deno.env.get("FIREBASE_PRIVATE_KEY");
    if (!clientEmail || !privateKeyPem) {
        throw new Error("Missing FIREBASE_CLIENT_EMAIL / FIREBASE_PRIVATE_KEY env vars");
    }

    // The Supabase secrets UI escapes newlines as \n; restore them.
    const pem = privateKeyPem.replace(/\\n/g, "\n");
    const privateKey = await importPkcs8(pem);

    const jwt = await create(
        { alg: "RS256", typ: "JWT" },
        {
            iss: clientEmail,
            scope: "https://www.googleapis.com/auth/firebase.messaging",
            aud: "https://oauth2.googleapis.com/token",
            iat: now,
            exp: getNumericDate(60 * 60),
        },
        privateKey
    );

    const tokenResp = await fetch("https://oauth2.googleapis.com/token", {
        method: "POST",
        headers: { "Content-Type": "application/x-www-form-urlencoded" },
        body: new URLSearchParams({
            grant_type: "urn:ietf:params:oauth:grant-type:jwt-bearer",
            assertion: jwt,
        }),
    });
    if (!tokenResp.ok) {
        throw new Error(`Token exchange failed: ${tokenResp.status} ${await tokenResp.text()}`);
    }
    const json = await tokenResp.json();
    cachedAccessToken = {
        token: json.access_token,
        expiresAt: now + Number(json.expires_in ?? 3600),
    };
    return cachedAccessToken.token;
}

async function importPkcs8(pem: string): Promise<CryptoKey> {
    const pkcs8 = pem
        .replace(/-----BEGIN PRIVATE KEY-----/g, "")
        .replace(/-----END PRIVATE KEY-----/g, "")
        .replace(/\s+/g, "");
    const der = Uint8Array.from(atob(pkcs8), (c) => c.charCodeAt(0));
    return await crypto.subtle.importKey(
        "pkcs8",
        der,
        { name: "RSASSA-PKCS1-v1_5", hash: "SHA-256" },
        false,
        ["sign"]
    );
}

// ─── FCM HTTP v1 send ────────────────────────────────────────────────────────
async function sendToToken(
    projectId: string,
    accessToken: string,
    token: string,
    notification: NotificationRow
): Promise<{ ok: boolean; status: number; body: string }> {
    const url = `https://fcm.googleapis.com/v1/projects/${projectId}/messages:send`;

    // Data-only payload — keys map 1:1 to FixBidMessagingService.onMessageReceived.
    const message = {
        message: {
            token,
            data: {
                notification_id: notification.id,
                title: notification.title,
                body: notification.body,
                type: notification.type,
                reference_id: notification.reference_id ?? "",
            },
            android: {
                // High priority wakes the device for time-sensitive alerts.
                priority: "HIGH",
                ttl: "3600s",
            },
        },
    };

    const resp = await fetch(url, {
        method: "POST",
        headers: {
            "Authorization": `Bearer ${accessToken}`,
            "Content-Type": "application/json",
        },
        body: JSON.stringify(message),
    });
    return { ok: resp.ok, status: resp.status, body: await resp.text() };
}

// ─── HTTP entrypoint ─────────────────────────────────────────────────────────
Deno.serve(async (req: Request) => {
    if (req.method !== "POST") {
        return new Response("Method not allowed", { status: 405 });
    }

    let payload: WebhookPayload;
    try {
        payload = (await req.json()) as WebhookPayload;
    } catch (_) {
        return new Response("Invalid JSON", { status: 400 });
    }

    if (payload.type !== "INSERT" || payload.table !== "notifications") {
        // Ignore everything but new notifications.
        return new Response(JSON.stringify({ skipped: true }), { status: 200 });
    }

    const notification = payload.record;
    if (!notification?.user_id) {
        return new Response("Missing user_id", { status: 400 });
    }

    const projectId = Deno.env.get("FIREBASE_PROJECT_ID");
    if (!projectId) {
        return new Response("Missing FIREBASE_PROJECT_ID", { status: 500 });
    }

    // Read tokens with the service role so RLS doesn't filter them out.
    const supabase = createClient(
        Deno.env.get("SUPABASE_URL")!,
        Deno.env.get("SUPABASE_SERVICE_ROLE_KEY")!
    );

    const { data: tokenRows, error } = await supabase
        .from("fcm_tokens")
        .select("token")
        .eq("user_id", notification.user_id);

    if (error) {
        console.error("Token fetch failed:", error);
        return new Response(JSON.stringify({ error: error.message }), { status: 500 });
    }
    if (!tokenRows || tokenRows.length === 0) {
        return new Response(JSON.stringify({ sent: 0, reason: "no tokens" }), { status: 200 });
    }

    const accessToken = await getAccessToken();

    const results = await Promise.all(
        tokenRows.map((row: { token: string }) =>
            sendToToken(projectId, accessToken, row.token, notification)
        )
    );

    // Clean up tokens FCM rejected as invalid (UNREGISTERED / INVALID_ARGUMENT)
    // so subsequent sends don't fan out to dead devices.
    const stale: string[] = [];
    results.forEach((res, idx) => {
        if (res.status === 404 || res.status === 400) {
            // Best-effort detection — UNREGISTERED returns 404, NOT_FOUND/INVALID
            // can return 400 with errorCode UNREGISTERED.
            const lower = res.body.toLowerCase();
            if (lower.includes("unregistered") || lower.includes("invalid_argument") ||
                lower.includes("not_found")) {
                stale.push(tokenRows[idx].token);
            }
        }
    });
    if (stale.length > 0) {
        await supabase.from("fcm_tokens").delete().in("token", stale);
    }

    const okCount = results.filter((r) => r.ok).length;
    return new Response(
        JSON.stringify({
            target_user: notification.user_id,
            tried: tokenRows.length,
            sent: okCount,
            cleaned: stale.length,
        }),
        { status: 200, headers: { "Content-Type": "application/json" } }
    );
});
