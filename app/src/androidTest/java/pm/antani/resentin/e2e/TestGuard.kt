package pm.antani.resentin.e2e

import org.junit.Assume.assumeTrue
import pm.antani.resentin.net.HttpClients
import pm.antani.resentin.net.rest.MeApi

/**
 * The live tests wipe the E2E identity of the account they run as (`DELETE …/e2e/keys`) to start
 * clean. They must never do that to a real account: skip unless the token is a throwaway visitor.
 */
suspend fun requireVisitor(host: String, token: String) {
    val me = HttpClients.retrofit(host, HttpClients.okHttpClient({ token })).create(MeApi::class.java).getMe().body()
    assumeTrue("refusing to wipe E2E keys of a non-visitor account", me?.kind == "visitor")
}
