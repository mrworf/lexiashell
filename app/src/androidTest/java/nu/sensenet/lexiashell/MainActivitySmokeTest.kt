package nu.sensenet.lexiashell

import android.view.View
import android.view.Window
import android.view.WindowManager
import androidx.test.core.app.ActivityScenario
import androidx.test.espresso.Espresso.onView
import androidx.test.espresso.assertion.ViewAssertions.matches
import androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom
import androidx.test.espresso.matcher.ViewMatchers.isDisplayed
import androidx.test.espresso.matcher.ViewMatchers.withId
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.hamcrest.Description
import org.hamcrest.Matcher
import org.hamcrest.TypeSafeMatcher
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.mozilla.geckoview.GeckoView

@RunWith(AndroidJUnit4::class)
class MainActivitySmokeTest {
    @Test
    @Suppress("DEPRECATION")
    fun launchesFullscreenShellWithBrowserAndLoadingOverlay() {
        ActivityScenario.launch(MainActivity::class.java).use { scenario ->
            onView(withId(R.id.gecko_view))
                .check(matches(isAssignableFrom(GeckoView::class.java)))
                .check(matches(isAttachedAndFillsParent()))

            onView(withId(R.id.loading_overlay))
                .check(matches(isDisplayed()))
                .check(matches(isAttachedAndFillsParent()))

            scenario.onActivity { activity ->
                assertEquals("nu.sensenet.lexiashell", activity.packageName)
                assertFalse(activity.window.hasFeature(Window.FEATURE_ACTION_BAR))
                assertTrue(
                    activity.window.attributes.flags and WindowManager.LayoutParams.FLAG_FULLSCREEN != 0,
                )
            }
        }
    }

    private fun isAttachedAndFillsParent(): Matcher<View> =
        object : TypeSafeMatcher<View>() {
            override fun describeTo(description: Description) {
                description.appendText("is attached to the window and fills its parent")
            }

            override fun matchesSafely(view: View): Boolean {
                val parent = view.parent as? View ?: return false
                return view.isAttachedToWindow &&
                    view.width == parent.width &&
                    view.height == parent.height
            }
        }
}
