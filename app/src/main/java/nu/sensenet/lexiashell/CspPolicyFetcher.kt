package nu.sensenet.lexiashell

import java.net.HttpURLConnection
import java.net.URL

class CspPolicyFetcher(
    private val logger: LexiaLogger = AndroidLexiaLogger,
    private val headerSource: CspHeaderSource = HttpCspHeaderSource(),
) {
    fun fetch(url: String): CspNavigationPolicy {
        val headCsp = fetchCsp(url, method = "HEAD")
        if (headCsp == null) {
            logger.debug("No CSP header from HEAD $url; falling back to GET")
        }

        val csp = headCsp ?: fetchCsp(url, method = "GET")
        if (csp == null) {
            logger.debug("No CSP header found for $url; using bootstrap-origin-only policy")
        } else {
            logger.debug("Fetched CSP header for $url (${csp.length} chars)")
        }

        return CspNavigationPolicy.fromCsp(csp, url)
    }

    private fun fetchCsp(url: String, method: String): String? =
        try {
            logger.debug("Fetching CSP header with $method $url")
            headerSource.fetch(url = url, method = method, headerName = CSP_HEADER)
        } catch (exception: Exception) {
            logger.error(
                "CSP $method fetch failed for $url: " +
                    "${exception::class.java.simpleName}: ${exception.message}",
                exception,
            )
            null
        }

    companion object {
        private const val CSP_HEADER = "Content-Security-Policy"
    }
}

fun interface CspHeaderSource {
    fun fetch(url: String, method: String, headerName: String): String?
}

private class HttpCspHeaderSource : CspHeaderSource {
    override fun fetch(url: String, method: String, headerName: String): String? {
        val connection = URL(url).openConnection() as HttpURLConnection
        try {
            connection.requestMethod = method
            connection.instanceFollowRedirects = true
            connection.connectTimeout = TIMEOUT_MILLIS
            connection.readTimeout = TIMEOUT_MILLIS
            connection.inputStream.close()
            return connection.getHeaderField(headerName)
        } finally {
            connection.disconnect()
        }
    }

    private companion object {
        const val TIMEOUT_MILLIS = 5_000
    }
}
