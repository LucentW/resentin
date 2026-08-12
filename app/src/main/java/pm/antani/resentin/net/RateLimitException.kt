package pm.antani.resentin.net

class RateLimitException(val retryAfterMs: Long?) : Exception(
    if (retryAfterMs != null) "Troppe richieste, riprova tra ${retryAfterMs / 1000}s" else "Troppe richieste",
)
