package pm.antani.resentin.net

import java.util.concurrent.TimeUnit
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import pm.antani.resentin.BuildConfig

private val USER_AGENT = "Resentin/${BuildConfig.VERSION_NAME} (Android)"

object HttpClients {

    fun okHttpClient(tokenProvider: () -> String? = { null }, onUnauthorized: () -> Unit = {}): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            // WebSocket-only: makes OkHttp itself notice a silently-dead connection
            // (e.g. network dropped without a clean close) via native ping/pong,
            // rather than relying solely on our own Phoenix-level heartbeat timeout.
            .pingInterval(20, TimeUnit.SECONDS)
            .addInterceptor { chain ->
                val token = tokenProvider()
                val request = if (token != null) {
                    chain.request().newBuilder()
                        .header("Authorization", "Bearer $token")
                        .build()
                } else {
                    chain.request()
                }
                chain.proceed(request)
            }
            // A 401 on an authenticated call means the bearer is already dead
            // server-side (expired, revoked, account/session deleted) — no retry
            // fixes that. Only wired for calls made with a real token (AuthRepository.api());
            // the unauthenticated login/verify calls pass the default no-op.
            .addInterceptor { chain ->
                val response = chain.proceed(chain.request())
                if (response.code == 401) onUnauthorized()
                response
            }
            // Without this, OkHttp's own default ("okhttp/4.12.0") is what shows up
            // as the row label in the push-subscriptions device list — meaningless to
            // a user trying to tell their Android phone apart from other devices.
            .addInterceptor { chain ->
                chain.proceed(chain.request().newBuilder().header("User-Agent", USER_AGENT).build())
            }
            .addInterceptor(logging)
            .build()
    }

    fun retrofit(host: String, okHttpClient: OkHttpClient): Retrofit {
        return Retrofit.Builder()
            .baseUrl("https://$host/")
            .client(okHttpClient)
            .addConverterFactory(AppJson.asConverterFactory("application/json".toMediaType()))
            .build()
    }
}
