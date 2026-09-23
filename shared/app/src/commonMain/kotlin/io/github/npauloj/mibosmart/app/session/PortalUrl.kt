package io.github.npauloj.mibosmart.app.session

import kotlin.jvm.JvmInline

/**
 * Where a token is generated — the partner's web portal, which is not where the API lives.
 *
 * It arrives from the platform entry point like the api host does, for the same reason: the address is
 * configured per machine in `local.properties` and never versioned (ADR-008). Wrapping it in a type
 * rather than passing a `String` is what stops it being injected where the *api* host belongs; the two
 * are different addresses and confusing them is exactly the defect ADR-025 records.
 *
 * The app only ever opens it. It never reads it, never sends a request to it from this module, and the
 * token still arrives by paste — this is a shortcut to the page, not a way in.
 */
@JvmInline
value class PortalUrl(val value: String)
