package com.example.voltloop

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime

val supabase = createSupabaseClient(
    supabaseUrl = Secrets.SUPABASE_URL,
    supabaseKey = Secrets.SUPABASE_ANON_KEY
) {
    install(Auth)
    install(Postgrest)
    install(Realtime)
}
