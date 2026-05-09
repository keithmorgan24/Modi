package com.keith.modi

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

object Supabase {
    private const val SUPABASE_URL = "https://beztonodgfvlrxzyxkxb.supabase.co"
    
    // TODO: Replace with your actual Anon Key from Supabase Dashboard -> Settings -> API
    private const val SUPABASE_ANON_KEY = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6ImJlenRvbm9kZ2Z2bHJ4enl4a3hiIiwicm9sZSI6ImFub24iLCJpYXQiOjE3NzgyMjkyMDYsImV4cCI6MjA5MzgwNTIwNn0.DdIC1FaWOrNGd5tFmnIVkq9jg4yVdyJHsiNXJKPahdc"

    val client = createSupabaseClient(
        supabaseUrl = SUPABASE_URL,
        supabaseKey = SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Auth)
        install(Realtime)
        install(Storage)
    }
}
