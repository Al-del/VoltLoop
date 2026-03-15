package com.example.voltloop

import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.storage.storage

/**
 * Uploads [imageBytes] (JPEG/PNG) to the Supabase `avatars` bucket at path
 * `{userId}/avatar.jpg` and updates the user's profile row with the public URL.
 *
 * Returns the public URL on success, null on failure.
 */
suspend fun uploadAvatarAndSave(imageBytes: ByteArray, userId: String): String? {
    return try {
        val objectPath = "$userId/avatar.jpg"
        
        // Use the official Supabase Storage SDK to upload
        val bucket = supabase.storage["avatars"]
        bucket.upload(objectPath, imageBytes) {
            upsert = true
        }

        // Public URL pattern for Supabase Storage
        val supabaseUrl = Secrets.SUPABASE_URL
        // Append a timestamp to the URL so that Coil bypasses the cache and re-downloads the new image
        val publicUrl = "$supabaseUrl/storage/v1/object/public/avatars/$objectPath?t=${kotlinx.datetime.Clock.System.now().toEpochMilliseconds()}"

        // Persist to profiles table
        supabase.postgrest["profiles"].update({
            set("avatar_url", publicUrl)
        }) {
            filter { eq("id", userId) }
        }

        publicUrl
    } catch (e: Throwable) {
        // Catching Throwable instead of Exception to catch CancellationExceptions
        // or other KMP specific errors
        println("AVATAR_UPLOAD_EXCEPTION: ${e.message}")
        null
    }
}
