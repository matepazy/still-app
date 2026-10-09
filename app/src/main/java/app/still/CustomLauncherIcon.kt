package app.still

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import app.still.data.themes.CustomThemeBuilder

/** Bundled resources are paired with manifest aliases; arbitrary colors use the closest pair. */
object CustomLauncherIcon {
    data class Choice(val accentIndex: Int, val backgroundIndex: Int) {
        val alias: String get() = "CustomThemeLauncher${accentIndex}_${backgroundIndex}"
        val resource: Int get() = resources[accentIndex][backgroundIndex]
        val accent: String get() = CustomThemeBuilder.swatches[accentIndex].hex
        val background: String get() = CustomThemeBuilder.backgroundTones[backgroundIndex].backgroundHex
    }

    fun closest(accent: String, background: String): Choice {
        fun distance(first: String, second: String): Long {
            val a = first.substring(1, 7).toInt(16)
            val b = second.substring(1, 7).toInt(16)
            return listOf(16, 8, 0).sumOf { shift ->
                val difference = ((a shr shift) and 255) - ((b shr shift) and 255)
                difference.toLong() * difference
            }
        }
        return Choice(
            CustomThemeBuilder.swatches.indices.minBy { distance(accent, CustomThemeBuilder.swatches[it].hex) },
            CustomThemeBuilder.backgroundTones.indices.minBy { distance(background, CustomThemeBuilder.backgroundTones[it].backgroundHex) },
        )
    }

    val aliases: List<String> get() = CustomThemeBuilder.swatches.indices.flatMap { accent ->
        CustomThemeBuilder.backgroundTones.indices.map { background -> Choice(accent, background).alias }
    }

    fun bitmap(context: Context, choice: Choice): Bitmap {
        val bitmap = Bitmap.createBitmap(192, 192, Bitmap.Config.ARGB_8888)
        requireNotNull(context.getDrawable(choice.resource)).apply {
            setBounds(0, 0, bitmap.width, bitmap.height)
            draw(Canvas(bitmap))
        }
        return bitmap
    }

    private val resources = arrayOf(
        intArrayOf(R.mipmap.ic_launcher_custom_0_0, R.mipmap.ic_launcher_custom_0_1, R.mipmap.ic_launcher_custom_0_2, R.mipmap.ic_launcher_custom_0_3, R.mipmap.ic_launcher_custom_0_4, R.mipmap.ic_launcher_custom_0_5, R.mipmap.ic_launcher_custom_0_6, R.mipmap.ic_launcher_custom_0_7),
        intArrayOf(R.mipmap.ic_launcher_custom_1_0, R.mipmap.ic_launcher_custom_1_1, R.mipmap.ic_launcher_custom_1_2, R.mipmap.ic_launcher_custom_1_3, R.mipmap.ic_launcher_custom_1_4, R.mipmap.ic_launcher_custom_1_5, R.mipmap.ic_launcher_custom_1_6, R.mipmap.ic_launcher_custom_1_7),
        intArrayOf(R.mipmap.ic_launcher_custom_2_0, R.mipmap.ic_launcher_custom_2_1, R.mipmap.ic_launcher_custom_2_2, R.mipmap.ic_launcher_custom_2_3, R.mipmap.ic_launcher_custom_2_4, R.mipmap.ic_launcher_custom_2_5, R.mipmap.ic_launcher_custom_2_6, R.mipmap.ic_launcher_custom_2_7),
        intArrayOf(R.mipmap.ic_launcher_custom_3_0, R.mipmap.ic_launcher_custom_3_1, R.mipmap.ic_launcher_custom_3_2, R.mipmap.ic_launcher_custom_3_3, R.mipmap.ic_launcher_custom_3_4, R.mipmap.ic_launcher_custom_3_5, R.mipmap.ic_launcher_custom_3_6, R.mipmap.ic_launcher_custom_3_7),
        intArrayOf(R.mipmap.ic_launcher_custom_4_0, R.mipmap.ic_launcher_custom_4_1, R.mipmap.ic_launcher_custom_4_2, R.mipmap.ic_launcher_custom_4_3, R.mipmap.ic_launcher_custom_4_4, R.mipmap.ic_launcher_custom_4_5, R.mipmap.ic_launcher_custom_4_6, R.mipmap.ic_launcher_custom_4_7),
        intArrayOf(R.mipmap.ic_launcher_custom_5_0, R.mipmap.ic_launcher_custom_5_1, R.mipmap.ic_launcher_custom_5_2, R.mipmap.ic_launcher_custom_5_3, R.mipmap.ic_launcher_custom_5_4, R.mipmap.ic_launcher_custom_5_5, R.mipmap.ic_launcher_custom_5_6, R.mipmap.ic_launcher_custom_5_7),
        intArrayOf(R.mipmap.ic_launcher_custom_6_0, R.mipmap.ic_launcher_custom_6_1, R.mipmap.ic_launcher_custom_6_2, R.mipmap.ic_launcher_custom_6_3, R.mipmap.ic_launcher_custom_6_4, R.mipmap.ic_launcher_custom_6_5, R.mipmap.ic_launcher_custom_6_6, R.mipmap.ic_launcher_custom_6_7),
        intArrayOf(R.mipmap.ic_launcher_custom_7_0, R.mipmap.ic_launcher_custom_7_1, R.mipmap.ic_launcher_custom_7_2, R.mipmap.ic_launcher_custom_7_3, R.mipmap.ic_launcher_custom_7_4, R.mipmap.ic_launcher_custom_7_5, R.mipmap.ic_launcher_custom_7_6, R.mipmap.ic_launcher_custom_7_7),
        intArrayOf(R.mipmap.ic_launcher_custom_8_0, R.mipmap.ic_launcher_custom_8_1, R.mipmap.ic_launcher_custom_8_2, R.mipmap.ic_launcher_custom_8_3, R.mipmap.ic_launcher_custom_8_4, R.mipmap.ic_launcher_custom_8_5, R.mipmap.ic_launcher_custom_8_6, R.mipmap.ic_launcher_custom_8_7),
        intArrayOf(R.mipmap.ic_launcher_custom_9_0, R.mipmap.ic_launcher_custom_9_1, R.mipmap.ic_launcher_custom_9_2, R.mipmap.ic_launcher_custom_9_3, R.mipmap.ic_launcher_custom_9_4, R.mipmap.ic_launcher_custom_9_5, R.mipmap.ic_launcher_custom_9_6, R.mipmap.ic_launcher_custom_9_7),
        intArrayOf(R.mipmap.ic_launcher_custom_10_0, R.mipmap.ic_launcher_custom_10_1, R.mipmap.ic_launcher_custom_10_2, R.mipmap.ic_launcher_custom_10_3, R.mipmap.ic_launcher_custom_10_4, R.mipmap.ic_launcher_custom_10_5, R.mipmap.ic_launcher_custom_10_6, R.mipmap.ic_launcher_custom_10_7),
        intArrayOf(R.mipmap.ic_launcher_custom_11_0, R.mipmap.ic_launcher_custom_11_1, R.mipmap.ic_launcher_custom_11_2, R.mipmap.ic_launcher_custom_11_3, R.mipmap.ic_launcher_custom_11_4, R.mipmap.ic_launcher_custom_11_5, R.mipmap.ic_launcher_custom_11_6, R.mipmap.ic_launcher_custom_11_7),
        intArrayOf(R.mipmap.ic_launcher_custom_12_0, R.mipmap.ic_launcher_custom_12_1, R.mipmap.ic_launcher_custom_12_2, R.mipmap.ic_launcher_custom_12_3, R.mipmap.ic_launcher_custom_12_4, R.mipmap.ic_launcher_custom_12_5, R.mipmap.ic_launcher_custom_12_6, R.mipmap.ic_launcher_custom_12_7),
        intArrayOf(R.mipmap.ic_launcher_custom_13_0, R.mipmap.ic_launcher_custom_13_1, R.mipmap.ic_launcher_custom_13_2, R.mipmap.ic_launcher_custom_13_3, R.mipmap.ic_launcher_custom_13_4, R.mipmap.ic_launcher_custom_13_5, R.mipmap.ic_launcher_custom_13_6, R.mipmap.ic_launcher_custom_13_7),
        intArrayOf(R.mipmap.ic_launcher_custom_14_0, R.mipmap.ic_launcher_custom_14_1, R.mipmap.ic_launcher_custom_14_2, R.mipmap.ic_launcher_custom_14_3, R.mipmap.ic_launcher_custom_14_4, R.mipmap.ic_launcher_custom_14_5, R.mipmap.ic_launcher_custom_14_6, R.mipmap.ic_launcher_custom_14_7),
        intArrayOf(R.mipmap.ic_launcher_custom_15_0, R.mipmap.ic_launcher_custom_15_1, R.mipmap.ic_launcher_custom_15_2, R.mipmap.ic_launcher_custom_15_3, R.mipmap.ic_launcher_custom_15_4, R.mipmap.ic_launcher_custom_15_5, R.mipmap.ic_launcher_custom_15_6, R.mipmap.ic_launcher_custom_15_7)
    )
}
