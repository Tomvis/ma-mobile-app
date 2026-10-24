package io.music_assistant.client.ui.language

import platform.Foundation.NSLocale
import platform.Foundation.localizedStringForLocaleIdentifier
import platform.Foundation.preferredLanguages

// iOS sets an app's language in its own Settings page; the fork only follows it there.
actual object AppLocales : AppLanguageHost {
    override val supported: Boolean = false

    override fun shipped(): List<String> = emptyList()

    override fun applied(): String? = null

    override fun apply(tag: String?) = Unit

    override fun device(): String = (NSLocale.preferredLanguages.firstOrNull() as? String) ?: "en"

    override fun nativeName(tag: String): String =
        NSLocale(localeIdentifier = tag).localizedStringForLocaleIdentifier(tag) ?: tag
}
