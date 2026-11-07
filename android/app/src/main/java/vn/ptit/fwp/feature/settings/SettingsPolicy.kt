package vn.ptit.fwp.feature.settings

import vn.ptit.fwp.core.data.AppSettings

/** These controls write only the shared on-device demo preferences. */
internal fun canEditDemoPrivacy(settings: AppSettings): Boolean =
    settings.authenticated && settings.localDemoSession
