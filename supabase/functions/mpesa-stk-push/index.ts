// PENDO: High-Resilience M-Pesa Integration (v2.0)
import { serve } from "https://deno.land/std@0.168.0/http/server.ts"

const corsHeaders = {
  'Access-Control-Allow-Origin': '*',
  'Access-Control-Allow-Headers': 'authorization, x-client-info, apikey, content-type',
}

serve(async (req) => {
  if (req.method === 'OPTIONS') return new Response('ok', { headers: corsHeaders })

  try {
    const { phone, amount, booking_id } = await req.json()

    // 1. Validate Secrets
    const consumerKey = Deno.env.get('MPESA_CONSUMER_KEY')
    const consumerSecret = Deno.env.get('MPESA_CONSUMER_SECRET')
    const shortCode = Deno.env.get('MPESA_SHORTCODE')
    const passkey = Deno.env.get('MPESA_PASSKEY')

    if (!consumerKey || !consumerSecret) {
        throw new Error("Safaricom Keys are missing in Supabase Secrets!")
    }

    let formattedPhone = phone.replace(/\D/g, '')
    if (formattedPhone.startsWith('0')) formattedPhone = '254' + formattedPhone.substring(1)

    // 2. Get OAuth Token
    const auth = btoa(`${consumerKey}:${consumerSecret}`)
    const tokenRes = await fetch("https://sandbox.safaricom.co.ke/oauth/v1/generate?grant_type=client_credentials", {
      headers: { Authorization: `Basic ${auth}` }
    })

    const tokenData = await tokenRes.json()
    if (!tokenRes.ok) {
        throw new Error(`Safaricom Auth Failed: ${tokenData.errorMessage || tokenRes.statusText}`)
    }

    const access_token = tokenData.access_token

    // 3. Trigger STK Push
    const timestamp = new Date().toISOString().replace(/[^0-9]/g, '').slice(0, 14)
    const password = btoa(`${shortCode}${passkey}${timestamp}`)

    console.log(`[PENDO] Sending STK Push to ${formattedPhone}`)

    const stkRes = await fetch("https://sandbox.safaricom.co.ke/mpesa/stkpush/v1/processrequest", {
      method: "POST",
      headers: {
          Authorization: `Bearer ${access_token}`,
          "Content-Type": "application/json"
      },
      body: JSON.stringify({
        BusinessShortCode: shortCode,
        Password: password,
        Timestamp: timestamp,
        TransactionType: "CustomerPayBillOnline",
        Amount: Math.max(1, Math.round(amount)),
        PartyA: formattedPhone,
        PartyB: shortCode,
        PhoneNumber: formattedPhone,
        CallBackURL: `https://${Deno.env.get('MODI_PROJECT_REF')}.supabase.co/functions/v1/mpesa-callback?booking_id=${booking_id}`,
        AccountReference: "ModiBooking",
        TransactionDesc: "Payment for Modi Stay"
      })
    })

    const result = await stkRes.json()
    console.log("[Safaricom STK Response]:", JSON.stringify(result))

    return new Response(JSON.stringify(result), {
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
