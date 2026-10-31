package com.catsblock.weather.ui.alerts

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import com.catsblock.weather.i18n.I18n
import com.catsblock.weather.i18n.tr

@Composable
internal fun alertText(@StringRes id: Int, vararg args: Any): String {
    val template = tr(LocalContext.current.getString(id))
    return if (args.isEmpty()) template else String.format(I18n.lang.locale, template, *args)
}
