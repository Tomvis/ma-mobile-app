package io.music_assistant.client.ui.language

import android.app.LocaleConfig
import android.app.LocaleManager
import android.content.Context
import android.os.Build
import android.os.LocaleList
import org.koin.core.context.GlobalContext
import java.util.Locale

actual object AppLocales : AppLanguageHost {
    override val supported: Boolean = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU

    private val context: Context get() = GlobalContext.get().get()

    private val manager: LocaleManager? get() =
        if (supported) context.getSystemService(LocaleManager::class.java) else null

    // The manifest's localeConfig, generated from the composeResources values-* directories.
    override fun shipped(): List<String> {
        if (!supported) return emptyList()
        val locales = LocaleConfig(context).supportedLocales ?: return emptyList()
        return (0 until locales.size()).map { locales[it].toLanguageTag() }
    }

    override fun applied(): String? =
        manager?.applicationLocales?.takeUnless { it.isEmpty }?.get(0)?.toLanguageTag()

    override fun apply(tag: String?) {
        val manager = manager ?: return
        if (applied() == tag) return
        manager.applicationLocales = tag?.let(LocaleList::forLanguageTags) ?: LocaleList.getEmptyLocaleList()
    }

    override fun device(): String =
        (manager?.systemLocales?.takeUnless { it.isEmpty }?.get(0) ?: Locale.getDefault()).toLanguageTag()

    override fun nativeName(tag: String): String {
        val locale = Locale.forLanguageTag(tag)
        return locale.getDisplayName(locale).replaceFirstChar { it.titlecase(locale) }
    }
}
