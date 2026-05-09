// PENDO: Secure M-Pesa Callback Handler
// This function receives the success/fail notification from Safaricom and updates Supabase

import { serve } from "https://deno.land/std@0.168.0/http/server.ts"
import { createClient } from "https://esm.sh/@supabase/supabase-js@2"

serve(async (req) => {
  const url = new URL(req.url)
  const booking_id = url.searchParams.get('booking_id')

  try {
    const body = await req.json()
    const result = body.Body.stkCallback

    // 1. Initialize Supabase Admin Client
    const supabase = createClient(
      Deno.env.get('SUPABASE_URL') ?? '',
      Deno.env.get('SUPABASE_SERVICE_ROLE_KEY') ?? '' // PENDO: Use Service Role to bypass RLS for updates
    )

    if (result.ResultCode === 0) {
      // 2. SUCCESS: Update booking status to CONFIRMED
      const { error } = await supabase
        .from('bookings')
        .update({ status: 'CONFIRMED' })
        .eq('id', booking_id)

      if (error) throw error
      console.log(`Booking ${booking_id} confirmed via M-Pesa.`)
    } else {
      // 3. FAILURE: Update booking status to CANCELLED or log failure
      console.log(`M-Pesa payment failed for booking ${booking_id}: ${result.ResultDesc}`)
      await supabase
        .from('bookings')
        .update({ status: 'CANCELLED' })
        .eq('id', booking_id)
    }

    return new Response(JSON.stringify({ status: 'ok' }), { status: 200 })

  } catch (error) {
    console.error('Callback Error:', error.message)
    return new Response(JSON.stringify({ error: error.message }), { status: 400 })
  }
})
