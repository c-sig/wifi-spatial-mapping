package com.statproj.wifispatial.network

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit
import java.util.concurrent.ConcurrentHashMap
import java.security.SecureRandom
import java.security.cert.X509Certificate
import javax.net.ssl.SSLContext
import javax.net.ssl.TrustManager
import javax.net.ssl.X509TrustManager
import org.json.JSONObject
import org.json.JSONArray

enum class SpeedTestServer(val displayName: String) {
    LOCAL("Local"),
    CLOUDFLARE("Cloudflare"),
    LINODE_SG("Linode Singapore"),
    OVH("OVH")
}

/**
 * Result of a combined download + upload speed test.
 *
 * @property dlMbps Download throughput in Mbps, or `null` if the test failed.
 * @property ulMbps Upload throughput in Mbps, or `null` if the test failed.
 * @property success `true` when at least one of the two measurements succeeded.
 */
data class SpeedTestResult(
    val dlMbps: Double?,
    val ulMbps: Double?,
    val success: Boolean
)

/**
 * Speed-test engine that measures throughput against Cloudflare's
 * public speed-test endpoints using a 25 MB payload.
 *
 * All public functions are **suspend** and execute their I/O on
 * [Dispatchers.IO].
 */
object SpeedTestEngine {

    private const val TAG = "SpeedTestEngine"

    private val _cooldownSeconds = MutableStateFlow(0)
    val cooldownSeconds: StateFlow<Int> = _cooldownSeconds.asStateFlow()

    private val engineScope = CoroutineScope(Dispatchers.Default)
    private var cooldownJob: Job? = null

    private fun startCooldownCountdown(seconds: Int) {
        cooldownJob?.cancel()
        cooldownJob = engineScope.launch {
            for (sec in seconds downTo 0) {
                _cooldownSeconds.value = sec
                delay(1000)
            }
        }
    }

    /** Payload size used for both download and upload tests. */
    private const val TEST_BYTES = 25_000_000L

    var selectedServer: SpeedTestServer = SpeedTestServer.LOCAL

    /** Escalating backoff tiers for 429 rate limits (in seconds). */
    private val BACKOFF_TIERS = longArrayOf(120, 180, 300, 600) // 2min, 3min, 5min, 10min
    private var rateLimitHitCount = 0

    /** Local endpoints. */
    private const val LOCAL_DOWNLOAD_URL = "https://speedtest.rafandj.us/downloading?disableFastRender=true"
    private const val LOCAL_UPLOAD_URL = "https://speedtest.rafandj.us/upload"

    /** Cloudflare endpoints. */
    private const val CLOUDFLARE_DOWNLOAD_BASE = "https://speed.cloudflare.com/__down?bytes="
    private const val CLOUDFLARE_UPLOAD_URL = "https://speed.cloudflare.com/__up"

    /** OVH endpoints. */
    private const val OVH_DOWNLOAD_URL = "https://proof.ovh.net/files/10Mb.dat"
    
    /** Linode Singapore endpoints. */
    private const val LINODE_SG_DOWNLOAD_URL = "https://speedtest.singapore.linode.com/100MB-singapore.bin"

    private const val MAX_RETRIES = 3

    private const val TIMEOUT_SECONDS = 60L

    private val bootstrapClient: OkHttpClient by lazy {
        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        OkHttpClient.Builder()
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(5, TimeUnit.SECONDS)
            .build()
    }

    private fun resolveViaDoh(hostname: String): List<java.net.InetAddress> {
        // 1. Try Google DNS (8.8.8.8)
        try {
            val url = "https://8.8.8.8/resolve?name=$hostname&type=A"
            val request = Request.Builder()
                .url(url)
                .get()
                .build()

            bootstrapClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val json = JSONObject(bodyString)
                        val status = json.optInt("Status", -1)
                        if (status == 0) {
                            val answer = json.optJSONArray("Answer")
                            if (answer != null) {
                                val addresses = mutableListOf<java.net.InetAddress>()
                                for (i in 0 until answer.length()) {
                                    val obj = answer.getJSONObject(i)
                                    val type = obj.optInt("type", -1)
                                    if (type == 1) { // A record
                                        val ip = obj.optString("data")
                                        if (!ip.isNullOrEmpty()) {
                                            try {
                                                addresses.add(java.net.InetAddress.getByName(ip))
                                            } catch (e: Exception) {
                                                // ignore invalid IP format
                                            }
                                        }
                                    }
                                }
                                if (addresses.isNotEmpty()) {
                                    return addresses
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            com.statproj.wifispatial.debug.DebugLogger.log("⚠️ Google DoH failed for $hostname: ${e.message}")
        }

        // 2. Try Cloudflare DNS (1.1.1.1) as fallback
        try {
            val url = "https://1.1.1.1/dns-query?name=$hostname&type=A"
            val request = Request.Builder()
                .url(url)
                .header("Accept", "application/dns-json")
                .get()
                .build()

            bootstrapClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val bodyString = response.body?.string()
                    if (!bodyString.isNullOrEmpty()) {
                        val json = JSONObject(bodyString)
                        val status = json.optInt("Status", -1)
                        if (status == 0) {
                            val answer = json.optJSONArray("Answer")
                            if (answer != null) {
                                val addresses = mutableListOf<java.net.InetAddress>()
                                for (i in 0 until answer.length()) {
                                    val obj = answer.getJSONObject(i)
                                    val type = obj.optInt("type", -1)
                                    if (type == 1) { // A record
                                        val ip = obj.optString("data")
                                        if (!ip.isNullOrEmpty()) {
                                            try {
                                                addresses.add(java.net.InetAddress.getByName(ip))
                                            } catch (e: Exception) {
                                                // ignore invalid IP format
                                            }
                                        }
                                    }
                                }
                                if (addresses.isNotEmpty()) {
                                    return addresses
                                }
                            }
                        }
                    }
                }
            }
        } catch (e: Exception) {
            com.statproj.wifispatial.debug.DebugLogger.log("⚠️ Cloudflare DoH failed for $hostname: ${e.message}")
        }

        return emptyList()
    }

    @Volatile
    private var client: OkHttpClient = buildNewClient()

    @Synchronized
    private fun rebuildClient() {
        try {
            client.dispatcher.cancelAll()
            client.connectionPool.evictAll()
        } catch (e: Exception) {
            // ignore
        }
        client = buildNewClient()
        com.statproj.wifispatial.debug.DebugLogger.log("🔄 OkHttpClient rebuilt successfully.")
    }

    private fun buildNewClient(): OkHttpClient {
        // Disable Java's negative DNS cache — prevents caching "hostname not found" errors
        java.security.Security.setProperty("networkaddress.cache.negative.ttl", "0")

        val trustAllCerts = arrayOf<TrustManager>(object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun checkServerTrusted(chain: Array<out X509Certificate>?, authType: String?) {}
            override fun getAcceptedIssuers() = arrayOf<X509Certificate>()
        })

        val sslContext = SSLContext.getInstance("SSL")
        sslContext.init(null, trustAllCerts, SecureRandom())
        val sslSocketFactory = sslContext.socketFactory

        val cookieJar = object : CookieJar {
            private val cookieStore = ConcurrentHashMap<String, MutableList<Cookie>>()
            override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
                val host = url.host
                val existing = cookieStore[host] ?: mutableListOf()
                existing.addAll(cookies)
                cookieStore[host] = existing
            }
            override fun loadForRequest(url: HttpUrl): List<Cookie> {
                return cookieStore[url.host] ?: emptyList()
            }
        }

        return OkHttpClient.Builder()
            .cookieJar(cookieJar)
            .addInterceptor { chain ->
                val request = chain.request().newBuilder()
                    .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8,application/signed-exchange;v=b3;q=0.7")
                    .header("Accept-Language", "en-US,en;q=0.9")
                    .header("Sec-Ch-Ua", "\"Not_A Brand\";v=\"8\", \"Chromium\";v=\"120\", \"Google Chrome\";v=\"120\"")
                    .header("Sec-Ch-Ua-Mobile", "?0")
                    .header("Sec-Ch-Ua-Platform", "\"Windows\"")
                    .header("Sec-Fetch-Dest", "document")
                    .header("Sec-Fetch-Mode", "navigate")
                    .header("Sec-Fetch-Site", "none")
                    .header("Sec-Fetch-User", "?1")
                    .header("Upgrade-Insecure-Requests", "1")
                    .build()
                chain.proceed(request)
            }
            .sslSocketFactory(sslSocketFactory, trustAllCerts[0] as X509TrustManager)
            .hostnameVerifier { _, _ -> true }
            .connectTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .dns(object : okhttp3.Dns {
                override fun lookup(hostname: String): List<java.net.InetAddress> {
                    try {
                        return java.net.InetAddress.getAllByName(hostname).toList()
                    } catch (e: java.net.UnknownHostException) {
                        com.statproj.wifispatial.debug.DebugLogger.log("⚠️ DNS lookup failed for $hostname, trying DoH fallback...")
                        val resolved = resolveViaDoh(hostname)
                        if (resolved.isNotEmpty()) {
                            com.statproj.wifispatial.debug.DebugLogger.log("✅ DoH resolved $hostname to ${resolved.map { it.hostAddress }}")
                            return resolved
                        }
                        throw e
                    }
                }
            })
            .retryOnConnectionFailure(true)
            .build()
    }

    // ── Download ─────────────────────────────────────────────────────────────

    /**
     * Execute a download speed test by fetching [TEST_BYTES] from Cloudflare.
     *
     * @return Throughput in **Mbps** (megabits per second), or `null` on failure.
     */
    suspend fun runDownloadTest(): Double? = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            // Randomize payload size between 24.5MB and 25.5MB for Cloudflare evasion
            val randomizedBytes = TEST_BYTES + (Math.random() * 1_000_000 - 500_000).toLong()
            
            val urlToUse = when (selectedServer) {
                SpeedTestServer.LOCAL -> LOCAL_DOWNLOAD_URL
                SpeedTestServer.CLOUDFLARE -> "$CLOUDFLARE_DOWNLOAD_BASE$randomizedBytes"
                SpeedTestServer.OVH -> OVH_DOWNLOAD_URL
                SpeedTestServer.LINODE_SG -> LINODE_SG_DOWNLOAD_URL
            }
            
            val serverName = selectedServer.displayName
            com.statproj.wifispatial.debug.DebugLogger.log("Download test attempt $attempt/$MAX_RETRIES ($serverName)")

            try {
                val request = Request.Builder()
                    .url(urlToUse)
                    .get()
                    .build()

                val startNanos = System.nanoTime()

                val result = client.newCall(request).execute().use { response ->
                    if (!response.isSuccessful) {
                        if (response.code == 429) {
                            val tier = rateLimitHitCount.coerceAtMost(BACKOFF_TIERS.size - 1)
                            val waitSeconds = BACKOFF_TIERS[tier]
                            rateLimitHitCount++
                            val retryAfter = response.header("Retry-After")
                            com.statproj.wifispatial.debug.DebugLogger.log("⚠️ HTTP 429 Rate Limited! (hit #${rateLimitHitCount}, CF claims ${retryAfter ?: "??"}s)")
                            com.statproj.wifispatial.debug.DebugLogger.log("   Rebuilding OkHttpClient to clear cached connections and route blacklist...")
                            rebuildClient()
                            com.statproj.wifispatial.debug.DebugLogger.log("   Backing off ${waitSeconds}s (${waitSeconds/60}min)...")
                            startCooldownCountdown(waitSeconds.toInt())
                            delay(waitSeconds * 1000)
                            com.statproj.wifispatial.debug.DebugLogger.log("   Cooldown complete, resuming...")
                        } else {
                            com.statproj.wifispatial.debug.DebugLogger.log("Download failed: HTTP ${response.code}")
                        }
                        return@use null
                    }

                    val body = response.body
                    if (body == null) {
                        com.statproj.wifispatial.debug.DebugLogger.log("Download failed: Empty body")
                        return@use null
                    }

                    val bytesRead = body.source().use { source ->
                        var total = 0L
                        val buffer = okio.Buffer()
                        val loopStartNanos = System.nanoTime()
                        while (true) {
                            val read = source.read(buffer, 1_048_576) // 1MB buffer to avoid syscall bottleneck
                            if (read == -1L) break
                            total += read
                            buffer.clear()
                            
                            // Cap the test at 5 seconds maximum to prevent hanging on slow connections or infinite streams
                            if (System.nanoTime() - loopStartNanos > 5_000_000_000L) {
                                break
                            }
                        }
                        total
                    }

                    val elapsedNanos = System.nanoTime() - startNanos
                    val elapsedSeconds = elapsedNanos / 1_000_000_000.0

                    if (elapsedSeconds <= 0.0) {
                        com.statproj.wifispatial.debug.DebugLogger.log("Download failed: Time <= 0")
                        return@use null
                    }

                    val mbps = (bytesRead * 8) / (elapsedSeconds * 1_000_000)
                    com.statproj.wifispatial.debug.DebugLogger.log("Download success: ${"%.2f".format(mbps)} Mbps")
                    rateLimitHitCount = 0 // Reset backoff on success
                    mbps
                }

                if (result != null) return@withContext result

            } catch (e: java.net.UnknownHostException) {
                com.statproj.wifispatial.debug.DebugLogger.log("🚫 DNS BLOCKED — server refuses to resolve hostname for your IP")
                com.statproj.wifispatial.debug.DebugLogger.log("   Rebuilding OkHttpClient to clear cached connections and route blacklist...")
                rebuildClient()
                com.statproj.wifispatial.debug.DebugLogger.log("   Ban clears on its own in ~30-60 min. Switch servers or wait.")
                return@withContext null
            } catch (e: Exception) {
                lastException = e
                com.statproj.wifispatial.debug.DebugLogger.log("Download attempt $attempt failed: ${e.message}")
            }
        }
        
        Log.e(TAG, "Download test failed after $MAX_RETRIES attempts", lastException)
        com.statproj.wifispatial.debug.DebugLogger.log("Download test fully failed.")
        null
    }

    // ── Upload ───────────────────────────────────────────────────────────────

    /**
     * Execute an upload speed test by POSTing a 25 MB payload to Cloudflare.
     *
     * @return Throughput in **Mbps** (megabits per second), or `null` on failure.
     */
    suspend fun runUploadTest(): Double? = withContext(Dispatchers.IO) {
        var lastException: Exception? = null
        for (attempt in 1..MAX_RETRIES) {
            val serverName = selectedServer.displayName
            com.statproj.wifispatial.debug.DebugLogger.log("Upload test attempt $attempt/$MAX_RETRIES ($serverName)")

            try {
                // We use a dummy payload for the upload
                val dummyPayload = ByteArray(TEST_BYTES.toInt())
                val requestBody = dummyPayload.toRequestBody("application/octet-stream".toMediaType())

                val urlToUse = when (selectedServer) {
                    SpeedTestServer.LOCAL -> LOCAL_UPLOAD_URL
                    SpeedTestServer.CLOUDFLARE -> CLOUDFLARE_UPLOAD_URL
                    else -> LOCAL_UPLOAD_URL // Fast and OVH don't easily support simple POST upload testing, fallback to local
                }

                val request = Request.Builder()
                    .url(urlToUse)
                    .post(requestBody)
                    .build()

                val startNanos = System.nanoTime()

                val result = client.newCall(request).execute().use { response ->
                    val elapsedNanos = System.nanoTime() - startNanos
                    val elapsedSeconds = elapsedNanos / 1_000_000_000.0

                    if (!response.isSuccessful) {
                        com.statproj.wifispatial.debug.DebugLogger.log("Upload failed: HTTP ${response.code}")
                        return@use null
                    }

                    if (elapsedSeconds <= 0.0) {
                        com.statproj.wifispatial.debug.DebugLogger.log("Upload failed: Time <= 0")
                        return@use null
                    }

                    val mbps = (TEST_BYTES * 8) / (elapsedSeconds * 1_000_000)
                    com.statproj.wifispatial.debug.DebugLogger.log("Upload success: ${"%.2f".format(mbps)} Mbps")
                    mbps
                }

                if (result != null) return@withContext result
                
            } catch (e: Exception) {
                lastException = e
                com.statproj.wifispatial.debug.DebugLogger.log("Upload attempt $attempt failed: ${e.message}")
                if (e is java.net.UnknownHostException) {
                    com.statproj.wifispatial.debug.DebugLogger.log("   Rebuilding OkHttpClient to clear cached connections and route blacklist...")
                    rebuildClient()
                }
            }
        }
        
        Log.e(TAG, "Upload test failed after $MAX_RETRIES attempts", lastException)
        com.statproj.wifispatial.debug.DebugLogger.log("Upload test fully failed.")
        null
    }

    // ── Full test ────────────────────────────────────────────────────────────

    /**
     * Run a complete speed test: download followed by upload.
     *
     * Each leg is independently wrapped in error handling so a failure
     * in one does not prevent the other from running.
     *
     * @return [SpeedTestResult] containing whichever measurements succeeded.
     */
    suspend fun runFullTest(): SpeedTestResult = withContext(Dispatchers.IO) {
        val dlMbps: Double? = try {
            runDownloadTest()
        } catch (e: Exception) {
            Log.e(TAG, "Download leg of full test failed", e)
            null
        }

        val ulMbps: Double? = try {
            runUploadTest()
        } catch (e: Exception) {
            Log.e(TAG, "Upload leg of full test failed", e)
            null
        }

        val success = dlMbps != null || ulMbps != null
        SpeedTestResult(dlMbps = dlMbps, ulMbps = ulMbps, success = success)
    }
}
