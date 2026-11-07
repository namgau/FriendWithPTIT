package vn.ptit.fwp.feature.settings

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import vn.ptit.fwp.core.data.AppSettings

class SettingsPolicyTest {
    @Test fun onlyAuthenticatedDemoSessionCanEditLocalPrivacy() {
        assertTrue(canEditDemoPrivacy(AppSettings(authenticated = true, localDemoSession = true)))
        assertFalse(canEditDemoPrivacy(AppSettings(authenticated = true, localDemoSession = false)))
        assertFalse(canEditDemoPrivacy(AppSettings(authenticated = false, localDemoSession = true)))
        assertFalse(canEditDemoPrivacy(AppSettings(authenticated = false, localDemoSession = false)))
    }

    @Test fun demoPreferenceDoesNotMakeBackendSessionDemo() {
        assertFalse(canEditDemoPrivacy(AppSettings(authenticated = true, localDemoSession = false, demoMode = true)))
        assertTrue(canEditDemoPrivacy(AppSettings(authenticated = true, localDemoSession = true, demoMode = false)))
    }
}
