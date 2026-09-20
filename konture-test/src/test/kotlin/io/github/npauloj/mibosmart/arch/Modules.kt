package io.github.npauloj.mibosmart.arch

/** Names used by every rule, in one place, so a rename breaks exactly one file. */
internal object Modules {
    const val DOMAIN = ":shared:domain"
    const val DATA = ":shared:data"
    const val APP = ":shared:app"
    const val ANDROID_APP = ":androidApp"

    const val ROOT_PACKAGE = "io.github.npauloj.mibosmart"
    const val DOMAIN_PACKAGE = "$ROOT_PACKAGE.domain"
    const val DATA_PACKAGE = "$ROOT_PACKAGE.data"
    const val APP_PACKAGE = "$ROOT_PACKAGE.app"
}
