package vn.ptit.fwp.core.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map

/** Local social rows have no account owner and must never represent a backend user. */
internal fun allowsLocalDemo(authenticated: Boolean, localDemoSession: Boolean): Boolean =
    authenticated && localDemoSession

/** Room's unscoped seed IDs are not backend UUIDs or anonymous match handles. */
internal fun allowsDemoRoute(authenticated: Boolean, localDemoSession: Boolean, route: String, id: String? = null): Boolean {
    if (!allowsLocalDemo(authenticated, localDemoSession)) return false
    return when (route) {
        "profile/{profileId}" -> isDemoProfileId(id)
        "chat/{matchId}" -> isDemoMatchId(id)
        "nearby", "double_date", "notifications" -> true
        else -> false
    }
}

internal fun isDemoProfileId(id: String?): Boolean = id != null && id.matches(Regex("p[0-9]{2}"))

internal fun isDemoMatchId(id: String?): Boolean = id != null &&
    (id.startsWith("match-") || id.startsWith("double-")) && id.matches(Regex("[A-Za-z0-9_-]{1,120}"))

internal fun demoCountOrZero(demo: Boolean, count: Int): Int = if (demo) count else 0

/** Unsubscribe from Room outside an explicit demo session; never replay a local row as backend data. */
internal fun <T> Flow<T>.demoOnly(settings: Flow<AppSettings>, empty: T): Flow<T> =
    settings.map { allowsLocalDemo(it.authenticated, it.localDemoSession) }
        .distinctUntilChanged()
        .flatMapLatest { demo -> if (demo) this else flowOf(empty) }

data class PresentationIdentity(val authenticated: Boolean, val demo: Boolean, val email: String, val generation: Long)

internal fun presentationIdentity(settings: AppSettings, generation: Long): PresentationIdentity =
    PresentationIdentity(settings.authenticated, allowsLocalDemo(settings.authenticated, settings.localDemoSession), settings.email.trim().lowercase(), generation)
