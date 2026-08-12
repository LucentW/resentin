package pm.antani.resentin.net

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonNamingStrategy

val AppJson: Json = Json {
    ignoreUnknownKeys = true
    explicitNulls = false
    // kotlinx.serialization omits a field whose runtime value equals the class's
    // declared default (e.g. Map<String,String> = emptyMap()) unless told otherwise —
    // for REST bodies that means "no change" and "explicitly cleared to empty" become
    // indistinguishable, which the server correctly rejects. Always encode explicitly.
    encodeDefaults = true
    isLenient = true
    namingStrategy = JsonNamingStrategy.SnakeCase
}
