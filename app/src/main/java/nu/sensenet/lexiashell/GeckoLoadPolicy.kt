package nu.sensenet.lexiashell

import org.mozilla.geckoview.GeckoSession

object GeckoLoadPolicy {
    fun flags(): Int = GeckoSession.LOAD_FLAGS_BYPASS_CACHE
}
