package io.github.npauloj.mibosmart.app.session

import kotlin.jvm.JvmInline

/** Where a token is generated — the partner's web portal, which is not where the API lives. */
@JvmInline
value class PortalUrl(val value: String)
