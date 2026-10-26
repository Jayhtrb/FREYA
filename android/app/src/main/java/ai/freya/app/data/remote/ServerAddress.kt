package ai.freya.app.data.remote

import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response

/**
 * The FREYA backend address. Defaults to the build-time URL (the emulator's 10.0.2.2 in debug),
 * and can be changed in the app (sign-in screen or Settings) so a real phone can point at a
 * computer on the same Wi-Fi without rebuilding. Every request is rewritten by [Rewrite].
 */
object ServerAddress {
    const val DEFAULT_PORT = 5001

    @Volatile private var override: HttpUrl? = null

    /** The address requests actually go to, without a trailing slash. */
    val current: String get() = override?.toString()?.trimEnd('/') ?: NetworkModule.defaultBaseUrl.trimEnd('/')

    val isCustom: Boolean get() = override != null

    fun set(raw: String?) {
        override = raw?.let { normalize(it) }
    }

    /**
     * Accepts "192.168.1.20", "192.168.1.20:5001", "http://192.168.1.20:5001" or an https URL.
     * A bare host with no scheme and no port gets the backend's default port (5001).
     * Returns null for blank or unparseable input.
     */
    fun normalize(raw: String): HttpUrl? {
        var s = raw.trim().trimEnd('/')
        if (s.isEmpty()) return null
        if (!s.contains("://")) {
            if (!s.substringBefore('/').contains(':')) s = s.substringBefore('/') + ":$DEFAULT_PORT"
            s = "http://$s"
        }
        val url = s.toHttpUrlOrNull() ?: return null
        if (url.host.isBlank()) return null
        return url.newBuilder().encodedPath("/").query(null).fragment(null).build()
    }

    /** Rewrites scheme/host/port of every request to the chosen server (path kept). */
    object Rewrite : Interceptor {
        override fun intercept(chain: Interceptor.Chain): Response {
            val o = override ?: return chain.proceed(chain.request())
            val req = chain.request()
            val url = req.url.newBuilder().scheme(o.scheme).host(o.host).port(o.port).build()
            return chain.proceed(req.newBuilder().url(url).build())
        }
    }
}
