package io.github.vykosmolt.pluton

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/** The few icons Material's core set does not carry, drawn here so the app needs no extra artifact. */
internal object PlutonIcons {
    private fun build(name: String, block: ImageVector.Builder.() -> Unit): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply(block).build()

    val Book: ImageVector = build("Book") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(4f, 4.5f)
            curveTo(4f, 3.67f, 4.67f, 3f, 5.5f, 3f)
            horizontalLineTo(11f)
            verticalLineTo(21f)
            horizontalLineTo(5.5f)
            curveTo(4.67f, 21f, 4f, 20.33f, 4f, 19.5f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(13f, 3f)
            horizontalLineTo(18.5f)
            curveTo(19.33f, 3f, 20f, 3.67f, 20f, 4.5f)
            verticalLineTo(19.5f)
            curveTo(20f, 20.33f, 19.33f, 21f, 18.5f, 21f)
            horizontalLineTo(13f)
            close()
        }
    }

    val Headphones: ImageVector = build("Headphones") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(12f, 3f)
            curveTo(7.58f, 3f, 4f, 6.58f, 4f, 11f)
            verticalLineTo(18f)
            horizontalLineTo(7f)
            verticalLineTo(11f)
            curveTo(7f, 8.24f, 9.24f, 6f, 12f, 6f)
            curveTo(14.76f, 6f, 17f, 8.24f, 17f, 11f)
            verticalLineTo(18f)
            horizontalLineTo(20f)
            verticalLineTo(11f)
            curveTo(20f, 6.58f, 16.42f, 3f, 12f, 3f)
            close()
        }
    }

    val Mic: ImageVector = build("Mic") {
        path(fill = SolidColor(Color.Black)) {
            moveTo(9f, 5f)
            curveTo(9f, 3.34f, 10.34f, 2f, 12f, 2f)
            curveTo(13.66f, 2f, 15f, 3.34f, 15f, 5f)
            verticalLineTo(11f)
            curveTo(15f, 12.66f, 13.66f, 14f, 12f, 14f)
            curveTo(10.34f, 14f, 9f, 12.66f, 9f, 11f)
            close()
        }
        path(fill = SolidColor(Color.Black)) {
            moveTo(5f, 11f)
            horizontalLineTo(7f)
            verticalLineTo(12f)
            curveTo(7f, 14.76f, 9.24f, 17f, 12f, 17f)
            curveTo(14.76f, 17f, 17f, 14.76f, 17f, 12f)
            verticalLineTo(11f)
            horizontalLineTo(19f)
            verticalLineTo(12f)
            curveTo(19f, 15.4f, 16.4f, 18.2f, 13f, 18.7f)
            verticalLineTo(21f)
            horizontalLineTo(11f)
            verticalLineTo(18.7f)
            curveTo(7.6f, 18.2f, 5f, 15.4f, 5f, 12f)
            close()
        }
    }
}
