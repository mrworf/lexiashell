package nu.sensenet.lexiashell

import org.junit.Assert.assertEquals
import org.junit.Test
import org.mozilla.geckoview.GeckoSession

class GeckoContentPermissionPolicyTest {
    @Test
    fun allowsAutoplayAndStorageAccessPermissions() {
        assertEquals(
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
            GeckoContentPermissionPolicy.valueFor(
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_AUDIBLE,
            ),
        )
        assertEquals(
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
            GeckoContentPermissionPolicy.valueFor(
                GeckoSession.PermissionDelegate.PERMISSION_AUTOPLAY_INAUDIBLE,
            ),
        )
        assertEquals(
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
            GeckoContentPermissionPolicy.valueFor(
                GeckoSession.PermissionDelegate.PERMISSION_STORAGE_ACCESS,
            ),
        )
    }

    @Test
    fun deniesPromptingPermissionsOutsideLexiaShellNeeds() {
        assertEquals(
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
            GeckoContentPermissionPolicy.valueFor(
                GeckoSession.PermissionDelegate.PERMISSION_GEOLOCATION,
            ),
        )
        assertEquals(
            GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
            GeckoContentPermissionPolicy.valueFor(
                GeckoSession.PermissionDelegate.PERMISSION_DESKTOP_NOTIFICATION,
            ),
        )
    }

    @Test
    fun formatsDecisionNames() {
        assertEquals(
            "allowed",
            GeckoContentPermissionPolicy.decisionName(
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_ALLOW,
            ),
        )
        assertEquals(
            "denied",
            GeckoContentPermissionPolicy.decisionName(
                GeckoSession.PermissionDelegate.ContentPermission.VALUE_DENY,
            ),
        )
        assertEquals("unknown", GeckoContentPermissionPolicy.decisionName(Int.MIN_VALUE))
    }
}
