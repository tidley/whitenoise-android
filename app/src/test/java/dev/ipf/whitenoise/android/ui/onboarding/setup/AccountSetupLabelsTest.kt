package dev.ipf.whitenoise.android.ui.onboarding.setup

import dev.ipf.marmotkit.OnboardingIssueFfi
import dev.ipf.whitenoise.android.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AccountSetupLabelsTest {
    @Test
    fun inspectionCompletedHasItsOwnInformationalLabel() {
        assertEquals(
            R.string.setup_finding_inspection_completed,
            setupFindingTitle(OnboardingIssueFfi.INSPECTION_COMPLETED),
        )
    }

    @Test
    fun everyNativeFindingHasADisplayLabel() {
        OnboardingIssueFfi.entries.forEach { assertTrue(setupFindingTitle(it) != 0) }
    }
}
