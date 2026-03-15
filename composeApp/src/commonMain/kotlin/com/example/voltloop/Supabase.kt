package com.example.voltloop

import io.github.jan.supabase.createSupabaseClient
import io.github.jan.supabase.auth.Auth
import io.github.jan.supabase.postgrest.Postgrest
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.storage.Storage

val supabase = createSupabaseClient(
    supabaseUrl = Secrets.SUPABASE_URL,
    supabaseKey = Secrets.SUPABASE_ANON_KEY
) {
    install(Auth) {
        scheme = "voltloop"
        host = "login-callback"
    }
    install(Postgrest)
    install(Realtime)
    install(Storage)
}
