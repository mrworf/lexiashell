package nu.sensenet.lexiashell

import java.net.HttpURLConnection
import java.net.URL

class CspPolicyFetcher {
    fun fetch(url: String): CspNavigationPolicy {
        val csp = fetchCsp(url, method = "HEAD")
            ?: fetchCsp(url, method = "GET")

        return CspNavigationPolicy.fromCsp(csp, url)
    }

    private fun fetchCsp(url: String, method: String): String? =
        runCatching {
            val connection = URL(url).openConnection() as HttpURLConnection
            try {
                connection.requestMethod = method
                connection.instanceFollowRedirects = true
                connection.connectTimeout = TIMEOUT_MILLIS
                connection.readTimeout = TIMEOUT_MILLIS
                connection.inputStream.close()
                connection.getHeaderField(CSP_HEADER)
            } finally {
                connection.disconnect()
            }
        }.getOrNull()

    companion object {
        private const val CSP_HEADER = "Content-Security-Policy"
        private const val TIMEOUT_MILLIS = 5_000
    }
}
