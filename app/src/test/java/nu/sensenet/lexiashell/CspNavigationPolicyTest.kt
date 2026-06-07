package nu.sensenet.lexiashell

import org.junit.Assert.assertFalse
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CspNavigationPolicyTest {
    @Test
    fun allowsSelfExactHostsWildcardSubdomainsAndCspUrlsWithPaths() {
        val policy = CspNavigationPolicy.fromCsp(
            csp = """
                default-src 'self' https://auth.mylexia.com https://www.lexiacore5.com/legal/privacy.html;
                connect-src *.mylexia.com https://help.lexialearning.com/articles/start;
            """.trimIndent(),
            bootstrapUrl = BOOTSTRAP_URL,
        )

        assertTrue(policy.allows("https://www.lexiacore5.com"))
        assertTrue(policy.allows("https://auth.mylexia.com/mylexiaLogin"))
        assertTrue(policy.allows("https://core5-cargo.mylexia.com/core5"))
        assertTrue(policy.allows("https://help.lexialearning.com/s/article/Turn-on-Web-GL-in-Your-Browser"))
    }

    @Test
    fun blocksUnknownHostsWildcardParentDomainMismatchAndUnsupportedSchemes() {
        val policy = CspNavigationPolicy.fromCsp(
            csp = "default-src 'self' *.mylexia.com blob: data: mailto:",
            bootstrapUrl = BOOTSTRAP_URL,
        )

        assertFalse(policy.allows("https://example.com"))
        assertFalse(policy.allows("https://mylexia.com"))
        assertFalse(policy.allows("http://www.lexiacore5.com"))
        assertFalse(policy.allows("mailto:support@example.com"))
        assertFalse(policy.allows("blob:https://www.lexiacore5.com/id"))
        assertFalse(policy.allows("data:text/plain,hello"))
        assertFalse(policy.allows("not a url"))
    }

    @Test
    fun explainsAllowedNavigationDecisions() {
        val policy = CspNavigationPolicy.fromCsp(
            csp = """
                default-src 'self' https://auth.mylexia.com;
                connect-src *.mylexia.com;
            """.trimIndent(),
            bootstrapUrl = BOOTSTRAP_URL,
        )

        assertDecision(
            expectedAllowed = true,
            expectedReason = "allowed exact host www.lexiacore5.com",
            actual = policy.evaluate("https://www.lexiacore5.com/student"),
        )
        assertDecision(
            expectedAllowed = true,
            expectedReason = "allowed exact host auth.mylexia.com",
            actual = policy.evaluate("https://auth.mylexia.com/mylexiaLogin"),
        )
        assertDecision(
            expectedAllowed = true,
            expectedReason = "allowed wildcard domain mylexia.com for core5-cargo.mylexia.com",
            actual = policy.evaluate("https://core5-cargo.mylexia.com/core5"),
        )
    }

    @Test
    fun explainsBlockedNavigationDecisions() {
        val policy = CspNavigationPolicy.fromCsp(
            csp = "default-src 'self' *.mylexia.com",
            bootstrapUrl = BOOTSTRAP_URL,
        )

        assertDecision(
            expectedAllowed = false,
            expectedReason = "blocked malformed URL",
            actual = policy.evaluate("not a url"),
        )
        assertDecision(
            expectedAllowed = false,
            expectedReason = "blocked non-HTTPS scheme http",
            actual = policy.evaluate("http://www.lexiacore5.com"),
        )
        assertDecision(
            expectedAllowed = false,
            expectedReason = "blocked missing host",
            actual = policy.evaluate("https:///student"),
        )
        assertDecision(
            expectedAllowed = false,
            expectedReason = "blocked host example.com is not in policy",
            actual = policy.evaluate("https://example.com"),
        )
        assertDecision(
            expectedAllowed = false,
            expectedReason = "blocked host mylexia.com is not in policy",
            actual = policy.evaluate("https://mylexia.com"),
        )
    }

    @Test
    fun missingOrEmptyCspFallsBackToBootstrapOriginOnly() {
        val missingPolicy = CspNavigationPolicy.fromCsp(null, BOOTSTRAP_URL)
        val emptyPolicy = CspNavigationPolicy.fromCsp("", BOOTSTRAP_URL)

        assertTrue(missingPolicy.allows("https://www.lexiacore5.com/student"))
        assertTrue(emptyPolicy.allows("https://www.lexiacore5.com/student"))
        assertFalse(missingPolicy.allows("https://auth.mylexia.com"))
        assertFalse(emptyPolicy.allows("https://auth.mylexia.com"))
    }

    private companion object {
        const val BOOTSTRAP_URL = "https://www.lexiacore5.com"

        fun assertDecision(
            expectedAllowed: Boolean,
            expectedReason: String,
            actual: CspNavigationDecision,
        ) {
            assertEquals(expectedAllowed, actual.allowed)
            assertEquals(expectedReason, actual.reason)
        }
    }
}
