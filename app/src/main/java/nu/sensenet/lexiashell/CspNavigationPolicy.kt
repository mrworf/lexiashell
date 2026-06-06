package nu.sensenet.lexiashell

import java.net.URI
import java.util.Locale

class CspNavigationPolicy private constructor(
    private val exactHosts: Set<String>,
    private val wildcardDomains: Set<String>,
) {
    fun allows(url: String): Boolean {
        val uri = runCatching { URI(url) }.getOrNull() ?: return false
        if (!uri.scheme.equals("https", ignoreCase = true)) {
            return false
        }

        val host = uri.host?.normalizedHost() ?: return false
        return host in exactHosts || wildcardDomains.any { domain ->
            host.length > domain.length && host.endsWith(".$domain")
        }
    }

    companion object {
        fun fromCsp(csp: String?, bootstrapUrl: String): CspNavigationPolicy {
            val bootstrapHost = URI(bootstrapUrl).host.normalizedHost()
            val exactHosts = mutableSetOf(bootstrapHost)
            val wildcardDomains = mutableSetOf<String>()

            csp.orEmpty()
                .split(";")
                .map { it.trim() }
                .filter { it.isNotEmpty() }
                .forEach { directive ->
                    directive.split(Regex("\\s+"))
                        .drop(1)
                        .forEach { source ->
                            when (val hostSource = source.toHostSource()) {
                                is HostSource.Exact -> exactHosts += hostSource.host
                                is HostSource.Wildcard -> wildcardDomains += hostSource.domain
                                null -> Unit
                            }
                        }
                }

            return CspNavigationPolicy(
                exactHosts = exactHosts,
                wildcardDomains = wildcardDomains,
            )
        }

        private fun String.toHostSource(): HostSource? {
            val token = trim().trim('"')
            if (
                token.isEmpty() ||
                token == "*" ||
                token.startsWith("'") ||
                token in setOf("blob:", "data:", "mailto:") ||
                token.matches(Regex("[A-Za-z][A-Za-z0-9+.-]*:"))
            ) {
                return null
            }

            val withoutScheme = token.substringAfter("://", token)
            val authority = withoutScheme
                .substringBefore("/")
                .substringBefore("?")
                .substringBefore("#")
                .substringAfterLast("@")
            val host = authority
                .substringBefore(":")
                .trim()
                .normalizedHostOrNull()
                ?: return null

            return if (host.startsWith("*.")) {
                host.removePrefix("*.").takeIf { it.isNotEmpty() }?.let(HostSource::Wildcard)
            } else {
                HostSource.Exact(host)
            }
        }

        private fun String.normalizedHostOrNull(): String? =
            lowercase(Locale.US)
                .trimEnd('.')
                .takeIf { host ->
                    host.isNotEmpty() &&
                        host.indexOf('*', startIndex = 1) == -1 &&
                        host.contains(".")
                }

        private fun String.normalizedHost(): String =
            lowercase(Locale.US).trimEnd('.')
    }
}

private sealed class HostSource {
    data class Exact(val host: String) : HostSource()
    data class Wildcard(val domain: String) : HostSource()
}
