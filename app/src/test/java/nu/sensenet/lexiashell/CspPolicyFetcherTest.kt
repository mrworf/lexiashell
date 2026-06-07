package nu.sensenet.lexiashell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CspPolicyFetcherTest {
    @Test
    fun logsSuccessfulCspFetchAndBuildsExpandedPolicy() {
        val logger = RecordingLogger()
        val fetcher = CspPolicyFetcher(
            logger = logger,
            headerSource = CspHeaderSource { _, method, _ ->
                if (method == "HEAD") {
                    "default-src 'self' https://auth.mylexia.com; connect-src *.mylexia.com"
                } else {
                    null
                }
            },
        )

        val policy = fetcher.fetch(BOOTSTRAP_URL)

        assertTrue(policy.allows("https://auth.mylexia.com/mylexiaLogin"))
        assertTrue(policy.allows("https://core5-cargo.mylexia.com/core5"))
        assertTrue(logger.debugMessages.any { it.contains("Fetching CSP header with HEAD") })
        assertTrue(logger.debugMessages.any { it.contains("Fetched CSP header for $BOOTSTRAP_URL") })
        assertFalse(logger.debugMessages.any { it.contains("falling back to GET") })
        assertTrue(logger.errorMessages.isEmpty())
    }

    @Test
    fun logsHeadFallbackToGetWhenHeadHasNoHeader() {
        val logger = RecordingLogger()
        val fetcher = CspPolicyFetcher(
            logger = logger,
            headerSource = CspHeaderSource { _, method, _ ->
                if (method == "GET") {
                    "default-src 'self' https://auth.mylexia.com"
                } else {
                    null
                }
            },
        )

        val policy = fetcher.fetch(BOOTSTRAP_URL)

        assertTrue(policy.allows("https://auth.mylexia.com/mylexiaLogin"))
        assertTrue(logger.debugMessages.any { it.contains("No CSP header from HEAD") })
        assertTrue(logger.debugMessages.any { it.contains("Fetching CSP header with GET") })
        assertTrue(logger.errorMessages.isEmpty())
    }

    @Test
    fun logsFetchFailuresAndFallsBackToBootstrapOnlyPolicy() {
        val logger = RecordingLogger()
        val fetcher = CspPolicyFetcher(
            logger = logger,
            headerSource = CspHeaderSource { _, _, _ ->
                throw IllegalStateException("network unavailable")
            },
        )

        val policy = fetcher.fetch(BOOTSTRAP_URL)

        assertTrue(policy.allows("https://www.lexiacore5.com/student"))
        assertFalse(policy.allows("https://auth.mylexia.com/mylexiaLogin"))
        assertTrue(logger.errorMessages.any { it.contains("CSP HEAD fetch failed") })
        assertTrue(logger.errorMessages.any { it.contains("CSP GET fetch failed") })
        assertTrue(logger.debugMessages.any { it.contains("using bootstrap-origin-only policy") })
    }

    @Test
    fun logsMissingHeadersAndFallsBackToBootstrapOnlyPolicy() {
        val logger = RecordingLogger()
        val fetcher = CspPolicyFetcher(
            logger = logger,
            headerSource = CspHeaderSource { _, _, _ -> null },
        )

        val policy = fetcher.fetch(BOOTSTRAP_URL)

        assertTrue(policy.allows("https://www.lexiacore5.com/student"))
        assertFalse(policy.allows("https://auth.mylexia.com/mylexiaLogin"))
        assertTrue(logger.debugMessages.any { it.contains("No CSP header from HEAD") })
        assertTrue(logger.debugMessages.any { it.contains("using bootstrap-origin-only policy") })
        assertTrue(logger.errorMessages.isEmpty())
    }

    private class RecordingLogger : LexiaLogger {
        val debugMessages = mutableListOf<String>()
        val errorMessages = mutableListOf<String>()

        override fun debug(message: String) {
            debugMessages += message
        }

        override fun error(message: String, throwable: Throwable?) {
            errorMessages += message
        }
    }

    private companion object {
        const val BOOTSTRAP_URL = "https://www.lexiacore5.com"
    }
}
