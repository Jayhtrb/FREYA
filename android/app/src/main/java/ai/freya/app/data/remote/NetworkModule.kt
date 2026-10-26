package ai.freya.app.data.remote

import ai.freya.app.BuildConfig
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit

/** Builds the OkHttp/Retrofit stack. Default URL is set at build time; [ServerAddress] can override it at runtime. */
object NetworkModule {

    val defaultBaseUrl: String = BuildConfig.API_BASE_URL

    fun buildOkHttpClient(authToken: () -> String?): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .addInterceptor(ServerAddress.Rewrite)
            .addInterceptor(AuthInterceptor(authToken))
            .addInterceptor(logging)
            .connectTimeout(30, TimeUnit.SECONDS)
            .writeTimeout(120, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .build()
    }

    /** SSE streams must not time out while idle; use a dedicated client for them. */
    fun buildSseOkHttpClient(authToken: () -> String?): OkHttpClient {
        return OkHttpClient.Builder()
            .addInterceptor(ServerAddress.Rewrite)
            .addInterceptor(AuthInterceptor(authToken))
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.MILLISECONDS)
            .build()
    }

    fun buildRetrofit(client: OkHttpClient, baseUrl: String): Retrofit =
        Retrofit.Builder()
            .baseUrl(baseUrl)
            .client(client)
            .addConverterFactory(
                FreyaJson.json.asConverterFactory("application/json".toMediaType())
            )
            .build()

    fun buildApi(retrofit: Retrofit): FreyaApi = retrofit.create(FreyaApi::class.java)
}
