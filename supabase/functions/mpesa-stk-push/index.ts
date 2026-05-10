// PENDO: Ultra-Resilient M-Pesa Integration (Diagnostic v3.5)
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  const projRef = Deno.env.get('MODI_PROJECT_REF') || "beztonodgfvlrxzyxkxb"

  try {
    const { phone, amount, booking_id } = await req.json()

    // 1. Precise Secret Retrieval
    const consumerKey = Deno.env.get('MPESA_CONSUMER_KEY')?.trim()
    const consumerSecret = Deno.env.get('MPESA_CONSUMER_SECRET')?.trim()
    const shortCode = Deno.env.get('MPESA_SHORTCODE')?.trim()
    const passkey = Deno.env.get('MPESA_PASSKEY')?.trim()

    if (!consumerKey || !consumerSecret) throw new Error("Safaricom Secrets (Keys) are missing.")

    let formattedPhone = phone.replace(/\D/g, '')
    if (formattedPhone.startsWith('0')) formattedPhone = '254' + formattedPhone.substring(1)

    // 2. Safaricom OAuth Token (Minimalist Handshake)
    const auth = btoa(`${consumerKey}:${consumerSecret}`)
    const url = "https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials"

    console.log(`[PENDO] Authenticating with Safaricom...`)

    const tokenRes = await fetch(url, {
      method: "GET",
      headers: { "Authorization": `Basic ${auth}` }
    })

    const tokenBody = await tokenRes.text()
    if (!tokenRes.ok) {
        console.error(`[Safaricom Auth Failed] Status: ${tokenRes.status}, Body: ${tokenBody}`)
        throw new Error(`Safaricom Auth Failed (HTTP ${tokenRes.status}). Ensure M-Pesa Express is added to your app on Daraja.`)
    }

    const { access_token } = JSON.parse(tokenBody)

    // 3. Trigger STK Push
    const timestamp = new Date().toISOString().replace(/[^0-9]/g, '').slice(0, 14)
    const password = btoa(`${shortCode}${passkey}${timestamp}`)

    const stkRes = await fetch("https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest", {
      method: "POST",
      headers: {
          "Authorization": `Bearer ${access_token}`,
          "Content-Type": "application/json"
      },
      body: JSON.stringify({
        BusinessShortCode: shortCode,
        Password: password,
        Timestamp: timestamp,
        TransactionType: "CustomerPayBillOnline",
        Amount: Math.round(amount),
        PartyA: formattedPhone,
        PartyB: shortCode,
        PhoneNumber: formattedPhone,
        CallBackURL: `https://${projRef}.supabase.co/functions/v1/mpesa-callback?booking_id=${booking_id}`,
        AccountReference: "ModiBooking",
        TransactionDesc: "Stay Deposit"
      })
    })

    const result = await stkRes.text()
    console.log("[STK Result]:", result)

    return new Response(result, {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: stkRes.ok ? 200 : 400,
    })

  } catch (error) {
    console.error("[PENDO ERROR]:", error.message)
    return new Response(JSON.stringify({ error: error.message }), {
      headers: { ...corsHeaders, 'Content-Type': 'application/json' },
      status: 400,
    })
  }
})
