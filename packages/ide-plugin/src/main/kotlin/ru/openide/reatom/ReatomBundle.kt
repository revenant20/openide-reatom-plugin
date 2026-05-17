package ru.openide.reatom

import com.intellij.DynamicBundle
import org.jetbrains.annotations.Nls
import org.jetbrains.annotations.NonNls
import org.jetbrains.annotations.PropertyKey

@NonNls
private const val BUNDLE = "messages.ReatomBundle"

/**
 * Доступ к локализуемым строкам плагина из `messages/ReatomBundle.properties`.
 * Делегирует в [DynamicBundle] через композицию — наследоваться от него
 * платформа не велит.
 */
object ReatomBundle {

    private val INSTANCE = DynamicBundle(ReatomBundle::class.java, BUNDLE)

    @Nls
    fun message(
        @PropertyKey(resourceBundle = BUNDLE) key: String,
        vararg params: Any,
    ): String = INSTANCE.getMessage(key, *params)
}
