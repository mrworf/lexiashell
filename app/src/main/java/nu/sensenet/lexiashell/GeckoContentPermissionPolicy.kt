package nu.sensenet.lexiashell

import org.mozilla.geckoview.GeckoSession

object GeckoContentPermissionPolicy {
    fun valueFor(permission: Int): Int =
        when (permission) {
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
            GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
            GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS
            -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW
            else -> GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY
        }

    fun decisionName(value: Int): String =
        when (value) {
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW -> "allowed"
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY -> "denied"
            else -> "unknown"
        }
}
