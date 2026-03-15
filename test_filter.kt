import io.github.jan.supabase.postgrest.query.filter.FilterBuilder
import io.github.jan.supabase.postgrest.query.filter.PostgrestFilterBuilder

fun test(filter: FilterBuilder) {
    filter.isIn("user_id", listOf("A", "B"))
}
