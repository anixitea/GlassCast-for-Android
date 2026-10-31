package com.glasscast.app.data

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.glasscast.app.R

/** Which set an icon belongs to, the same three as the looks. */
enum class IconFamily { ORIGINAL, SEA_GLASS, LIQUID_GLASS }

/**
 * The app icons (2.0). Each is a launcher alias in the manifest
 * (`<activity-alias>` targeting GlassCastActivity) with its own adaptive icon;
 * [preview] is the same picture as a flat drawable for the picker.
 *
 * Every icon is drawn on the original's keyline: the glyph at its size and in
 * its place, the body edge to edge, so none of them looks bigger or smaller on
 * a home screen than the one people know.
 */
enum class AppIcon(val alias: String, val family: IconFamily, val preview: Int) {
    ORIGINAL(".MainActivity", IconFamily.ORIGINAL, R.drawable.icon_original),
    MIDNIGHT(".icon.Midnight", IconFamily.ORIGINAL, R.drawable.icon_midnight),
    PAPER(".icon.Paper", IconFamily.ORIGINAL, R.drawable.icon_paper),
    SEA_GLASS(".icon.SeaGlass", IconFamily.SEA_GLASS, R.drawable.icon_sea_glass),
    PEBBLE(".icon.Pebble", IconFamily.SEA_GLASS, R.drawable.icon_pebble),
    SHORE(".icon.Shore", IconFamily.SEA_GLASS, R.drawable.icon_shore),
    TIDE(".icon.Tide", IconFamily.SEA_GLASS, R.drawable.icon_tide),
    LAGOON(".icon.Lagoon", IconFamily.SEA_GLASS, R.drawable.icon_lagoon),
    ROSE(".icon.Rose", IconFamily.SEA_GLASS, R.drawable.icon_rose),
    AMBER(".icon.Amber", IconFamily.SEA_GLASS, R.drawable.icon_amber),
    MINT(".icon.Mint", IconFamily.SEA_GLASS, R.drawable.icon_mint),
    LIQUID_GLASS(".icon.LiquidGlass", IconFamily.LIQUID_GLASS, R.drawable.icon_liquid_glass),
    PRISM(".icon.Prism", IconFamily.LIQUID_GLASS, R.drawable.icon_prism),
    CLEAR(".icon.Clear", IconFamily.LIQUID_GLASS, R.drawable.icon_clear),
    DARK(".icon.Dark", IconFamily.LIQUID_GLASS, R.drawable.icon_dark);

    /**
     * The alias as a component. Its class name is relative to the code's
     * namespace (com.glasscast.app), which stays put whatever the
     * applicationId, so the package comes from the context and the class
     * from the namespace.
     */
    fun component(context: Context): ComponentName =
        ComponentName(context.packageName, "com.glasscast.app$alias")
}

/**
 * Switching icons: the chosen alias is enabled first and the rest disabled
 * after, so there's never a moment with no launcher entry at all. With
 * DONT_KILL_APP the app keeps running; the launcher swaps the icon in place,
 * though some (HyperOS's among them) drop a home screen shortcut and leave the
 * new icon in the app drawer.
 *
 * The package manager is the record of which is on: nothing else to keep in
 * step, and a backup restored onto another phone can't disagree with it.
 * Binder calls, so callers stay off the main thread.
 */
object AppIcons {

    fun current(context: Context): AppIcon {
        val pm = context.packageManager
        return AppIcon.entries.firstOrNull { isOn(pm, context, it) } ?: AppIcon.ORIGINAL
    }

    private fun isOn(pm: PackageManager, context: Context, icon: AppIcon): Boolean =
        when (runCatching { pm.getComponentEnabledSetting(icon.component(context)) }.getOrDefault(PackageManager.COMPONENT_ENABLED_STATE_DEFAULT)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> icon == AppIcon.ORIGINAL
            else -> false
        }

    /** True when the icon actually changed. */
    fun set(context: Context, icon: AppIcon): Boolean {
        val pm = context.packageManager
        if (current(context) == icon) return false
        val on = PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val off = PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        val flags = PackageManager.DONT_KILL_APP
        val others = AppIcon.entries.filter { it != icon }
        return runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                // One call, one broadcast to the launcher: it sees the swap
                // as a single change rather than fifteen.
                pm.setComponentEnabledSettings(
                    listOf(PackageManager.ComponentEnabledSetting(icon.component(context), on, flags)) +
                        others.map { PackageManager.ComponentEnabledSetting(it.component(context), off, flags) }
                )
            } else {
                pm.setComponentEnabledSetting(icon.component(context), on, flags)
                others.forEach { pm.setComponentEnabledSetting(it.component(context), off, flags) }
            }
            true
        }.getOrDefault(false)
    }
}
