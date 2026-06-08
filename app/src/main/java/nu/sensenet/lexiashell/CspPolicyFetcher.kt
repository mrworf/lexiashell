package nu.sensenet.lexiashell

import java.net.HttpURLConnection
import java.net.URL

class CspPolicyFetcher(
    private val logger: LexiaLogger = AndroidLexiaLogger,
    private val headerSource: CspHeaderSource = HttpCspHeaderSource(),
) {
    fun fetch(url: String): CspFetchResult {
        val headCsp = fetchCsp(url, method = "HEAD")
        if (headCsp.header == null) {
            logger.debug("No CSP header from HEAD $url; falling back to GET")
        }

        val getCsp = if (headCsp.header == null) {
            fetchCsp(url, method = "GET")
        } else {
            CspHeaderFetchResult.notAttempted()
        }
        val csp = headCsp.header ?: getCsp.header
        if (csp == null) {
            logger.debug("No CSP header found for $url; using bootstrap-origin-only policy")
        } else {
            logger.debug("Fetched CSP header for $url (${csp.length} chars)")
        }

        val status = when {
            headCsp.transportFailed || getCsp.transportFailed -> CspFetchStatus.TRANSPORT_FAILED
            csp == null -> CspFetchStatus.HEADER_MISSING
            else -> CspFetchStatus.HEADER_FOUND
        }

        return CspFetchResult(
            policy = CspNavigationPolicy.fromCsp(csp, url),
            status = status,
        )
    }

    private fun fetchCsp(url: String, method: String): CspHeaderFetchResult =
        try {
            logger.debug("Fetching CSP header with $method $url")
            CspHeaderFetchResult(headerSource.fetch(url = url, method = method, headerName = CSP_HEADER))
        } catch (exception: Exception) {
            logger.error(
                "CSP $method fetch failed for $url: " +
                    "${exception::class.java.simpleName}: ${exception.message}",
                exception,
            )
            CspHeaderFetchResult(header = null, transportFailed = true)
        }

    companion object {
        private const val CSP_HEADER = "Content-Security-Policy"
    }
}

data class CspFetchResult(
    val policy: CspNavigationPolicy,
    val status: CspFetchStatus,
) {
    val hasTransportFailure: Boolean = status == CspFetchStatus.TRANSPORT_FAILED
}

enum class CspFetchStatus {
    HEADER_FOUND,
    HEADER_MISSING,
    TRANSPORT_FAILED,
}

private data class CspHeaderFetchResult(
    val header: String?,
    val transportFailed: Boolean = false,
) {
    companion object {
        fun notAttempted(): CspHeaderFetchResult = CspHeaderFetchResult(header = null)
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
