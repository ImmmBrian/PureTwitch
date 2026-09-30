package com.puretv.twitch.desktop.update

import com.puretv.twitch.desktop.AppBuildConfig
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * resolveReleaseUrl decides what the "Open download page" recovery button opens:
 * the release's own page when known, otherwise the repo's /releases/latest.
 */
class UpdateReleaseUrlTest {
    // The repo comes from GITHUB_REPOSITORY at build time, so a fork's CI checks its own.
    private val latest = "https://github.com/${AppBuildConfig.GITHUB_OWNER}/${AppBuildConfig.GITHUB_REPO}/releases/latest"

    @Test fun usesTheReleasePageWhenPresent() {
        val url = "https://github.com/dhawal-ss/puretv/releases/tag/v1.9.2"
        assertEquals(url, resolveReleaseUrl(url))
    }

    @Test fun fallsBackToReleasesLatestWhenBlank() {
        assertEquals(
            latest,
            resolveReleaseUrl(""),
        )
    }

    @Test fun fallsBackWhenOnlyWhitespace() {
        assertEquals(
            latest,
            resolveReleaseUrl("   "),
        )
    }
}
