package io.music_assistant.client.ui.language

/**
 * The app's own UI language (HW-191, fork-only), as a BCP-47 tag; null = the device's language.
 * Android 13+ keeps it per app (LocaleManager, also shown in the system's App languages page).
 */
interface AppLanguageHost {
    /** False where there is no per-app language to set (Android < 13, iOS). */
    val supported: Boolean

    /** The languages this build ships, e.g. "en", "ru", "zh-CN". */
    fun shipped(): List<String>

    /** The per-app language in force, or null when the app follows the device. */
    fun applied(): String?

    fun apply(tag: String?)

    /** The device's first language. */
    fun device(): String

    /** [tag]'s name in its own language ("Русский", "עברית"). */
    fun nativeName(tag: String): String
}

expect object AppLocales : AppLanguageHost
