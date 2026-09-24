package io.github.npauloj.mibosmart.data.platform.vault

import kotlinx.cinterop.ExperimentalForeignApi
import kotlinx.cinterop.MemScope
import kotlinx.cinterop.addressOf
import kotlinx.cinterop.alloc
import kotlinx.cinterop.convert
import kotlinx.cinterop.get
import kotlinx.cinterop.memScoped
import kotlinx.cinterop.ptr
import kotlinx.cinterop.reinterpret
import kotlinx.cinterop.usePinned
import kotlinx.cinterop.value
import org.koin.core.scope.Scope
import platform.CoreFoundation.CFDataCreate
import platform.CoreFoundation.CFDataGetBytePtr
import platform.CoreFoundation.CFDataGetLength
import platform.CoreFoundation.CFDataRef
import platform.CoreFoundation.CFDictionaryAddValue
import platform.CoreFoundation.CFDictionaryCreateMutable
import platform.CoreFoundation.CFMutableDictionaryRef
import platform.CoreFoundation.CFRelease
import platform.CoreFoundation.CFStringCreateWithCString
import platform.CoreFoundation.CFTypeRefVar
import platform.CoreFoundation.kCFBooleanTrue
import platform.CoreFoundation.kCFStringEncodingUTF8
import platform.CoreFoundation.kCFTypeDictionaryKeyCallBacks
import platform.CoreFoundation.kCFTypeDictionaryValueCallBacks
import platform.Security.SecItemAdd
import platform.Security.SecItemCopyMatching
import platform.Security.SecItemDelete
import platform.Security.errSecItemNotFound
import platform.Security.errSecSuccess
import platform.Security.kSecAttrAccessible
import platform.Security.kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
import platform.Security.kSecAttrAccount
import platform.Security.kSecAttrService
import platform.Security.kSecClass
import platform.Security.kSecClassGenericPassword
import platform.Security.kSecMatchLimit
import platform.Security.kSecMatchLimitOne
import platform.Security.kSecReturnData
import platform.Security.kSecValueData

/** iOS needs nothing from the graph — the Keychain is reached by query, not through a handle. */
@OptIn(ExperimentalForeignApi::class)
internal actual fun Scope.secureTokenStore(): SecureTokenStore = KeychainTokenStore()

/**
 * One `kSecClassGenericPassword` item, readable only on this device and only after the first
 * unlock since boot — `kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly` also keeps it out of
 * iCloud and of encrypted backups (ADR-008).
 */
@OptIn(ExperimentalForeignApi::class)
private class KeychainTokenStore : SecureTokenStore {

    override fun read(): String? = memScoped {
        val query = itemQuery()
        CFDictionaryAddValue(query, kSecReturnData, kCFBooleanTrue)
        CFDictionaryAddValue(query, kSecMatchLimit, kSecMatchLimitOne)

        val found = alloc<CFTypeRefVar>()
        val status = SecItemCopyMatching(query, found.ptr)
        if (status == errSecItemNotFound) return@memScoped null
        check(status == errSecSuccess) { "Keychain read failed with OSStatus $status" }

        val data: CFDataRef = found.value?.reinterpret() ?: return@memScoped null
        defer { CFRelease(data) }
        val bytes = CFDataGetBytePtr(data) ?: return@memScoped null
        ByteArray(CFDataGetLength(data).toInt()) { index -> bytes[index].toByte() }.decodeToString()
    }

    override fun write(token: String) = memScoped {
        SecItemDelete(itemQuery())

        val insertion = itemQuery()
        val encoded = token.encodeToByteArray()
        val value = checkNotNull(
            encoded.usePinned { pinned ->
                CFDataCreate(null, pinned.addressOf(0).reinterpret(), encoded.size.convert())
            },
        ) { "Could not allocate the Keychain payload" }
        defer { CFRelease(value) }
        CFDictionaryAddValue(insertion, kSecValueData, value)
        CFDictionaryAddValue(insertion, kSecAttrAccessible, kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly)

        val status = SecItemAdd(insertion, null)
        check(status == errSecSuccess) { "Keychain write failed with OSStatus $status" }
    }

    /** The item is deleted by the same query that identifies it. */
    override fun clear() = memScoped {
        val status = SecItemDelete(itemQuery())
        check(status == errSecSuccess || status == errSecItemNotFound) {
            "Keychain delete failed with OSStatus $status"
        }
    }

    /** The query that names this app's one credential; released when the scope ends. */
    private fun MemScope.itemQuery(): CFMutableDictionaryRef {
        val query = CFDictionaryCreateMutable(
            null,
            0.convert(),
            kCFTypeDictionaryKeyCallBacks.ptr,
            kCFTypeDictionaryValueCallBacks.ptr,
        )!!
        defer { CFRelease(query) }
        CFDictionaryAddValue(query, kSecClass, kSecClassGenericPassword)
        CFDictionaryAddValue(query, kSecAttrService, cfString(SERVICE))
        CFDictionaryAddValue(query, kSecAttrAccount, cfString(ACCOUNT))
        return query
    }

    private fun MemScope.cfString(value: String) =
        CFStringCreateWithCString(null, value, kCFStringEncodingUTF8).also { defer { CFRelease(it) } }

    private companion object {
        /** Service and account are the identity of a generic-password item: one app, one credential. */
        const val SERVICE = "io.github.npauloj.mibosmart.vault"
        const val ACCOUNT = "session.token"
    }
}
