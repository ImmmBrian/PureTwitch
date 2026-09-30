package com.puretv.twitch.desktop.data

import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class SessionRefreshErrorTest {
    @Test fun only_a_dead_refresh_token_signs_you_out() {
        assertTrue(isRevokedSession("Invalid refresh token"))
        assertTrue(isRevokedSession("refresh token rejected"))
        assertFalse(isRevokedSession("invalid client secret"))
        assertFalse(isRevokedSession("missing client secret"))
    }
}
