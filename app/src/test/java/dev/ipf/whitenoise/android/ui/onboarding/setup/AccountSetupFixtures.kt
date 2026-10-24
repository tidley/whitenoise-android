package dev.ipf.whitenoise.android.ui.onboarding.setup

import dev.ipf.marmotkit.OnboardingActionFfi
import dev.ipf.marmotkit.OnboardingCheckOutcomeFfi
import dev.ipf.marmotkit.OnboardingCheckPurposeFfi
import dev.ipf.marmotkit.OnboardingFindingContextFfi
import dev.ipf.marmotkit.OnboardingFindingFfi
import dev.ipf.marmotkit.OnboardingIssueFfi
import dev.ipf.marmotkit.OnboardingRelayCapabilityFfi
import dev.ipf.marmotkit.OnboardingSnapshotFfi
import dev.ipf.marmotkit.OnboardingStatusFfi
import dev.ipf.marmotkit.OnboardingStepFfi
import dev.ipf.marmotkit.OnboardingStepStateFfi

internal val SETUP_TEST_ACCOUNT = "ab".repeat(32)

/** Legacy finding fixtures do not claim a new inspection or publication result. */
internal fun setupFinding(
    issue: OnboardingIssueFfi,
    endpoint: String?,
): OnboardingFindingFfi =
    OnboardingFindingFfi(
        issue = issue,
        endpoint = endpoint,
        context = OnboardingFindingContextFfi.UNKNOWN,
        purpose = OnboardingCheckPurposeFfi.UNKNOWN,
        outcome = OnboardingCheckOutcomeFfi.UNKNOWN,
        declaredCapability = OnboardingRelayCapabilityFfi.NONE,
        actions = emptyList(),
        observedRevision = null,
    )

/** Six-stage fixture with only the selected checkpoint requiring a decision. */
internal fun setupSnapshot(
    step: OnboardingStepFfi = OnboardingStepFfi.PROFILE,
    actions: List<OnboardingActionFfi> = listOf(OnboardingActionFfi.CONTINUE_WITHOUT, OnboardingActionFfi.EDIT_PROFILE),
    revision: ULong = 3uL,
    ready: Boolean = false,
): OnboardingSnapshotFfi =
    OnboardingSnapshotFfi(
        SETUP_TEST_ACCOUNT,
        null,
        revision,
        ready,
        OnboardingStepFfi.entries.map { item ->
            OnboardingStepStateFfi(
                item,
                when {
                    ready || item.ordinal < step.ordinal -> OnboardingStatusFfi.PASSED
                    item == step -> OnboardingStatusFfi.NEEDS_INPUT
                    else -> OnboardingStatusFfi.PENDING
                },
                emptyList(),
                if (item == step && !ready) actions else emptyList(),
                null,
            )
        },
        null,
        null,
        false,
    )
