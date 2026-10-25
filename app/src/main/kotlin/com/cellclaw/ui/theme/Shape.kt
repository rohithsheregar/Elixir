package com.cellclaw.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Shapes
import androidx.compose.ui.unit.dp

val ElixirShapes = Shapes(
    extraSmall = RoundedCornerShape(4.dp),
    small = RoundedCornerShape(8.dp),
    medium = RoundedCornerShape(12.dp),
    large = RoundedCornerShape(16.dp),
    extraLarge = RoundedCornerShape(24.dp)
)

object ElixirShapeTokens {
    val Badge = RoundedCornerShape(8.dp)
    val Card = RoundedCornerShape(16.dp)
    val Dialog = RoundedCornerShape(20.dp)
    val Button = RoundedCornerShape(12.dp)
    val Input = RoundedCornerShape(12.dp)
    val Pill = RoundedCornerShape(50)
}
