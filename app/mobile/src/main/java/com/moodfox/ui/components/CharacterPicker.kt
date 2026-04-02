package com.moodfox.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.moodfox.R
import com.moodfox.ui.theme.AppColors
import com.moodfox.ui.theme.LocalAppColors

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun CharacterPicker(
    selected: String,
    onSelect: (String) -> Unit,
    colors: AppColors = LocalAppColors.current,
) {
    val characters = listOf(
        "fox"   to stringResource(R.string.helper_fox),
        "cat"   to stringResource(R.string.helper_cat),
        "dog"   to stringResource(R.string.helper_dog),
        "frog"  to stringResource(R.string.helper_frog),
        "panda" to stringResource(R.string.helper_panda),
        "emoji" to stringResource(R.string.helper_emoji),
    )

    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        characters.forEach { (mode, label) ->
            val isSelected = selected == mode
            val neutralDrawable = when (mode) {
                "fox"   -> R.drawable.fox_mood_0
                "cat"   -> R.drawable.cat_mood_0
                "dog"   -> R.drawable.dog_mood_0
                "frog"  -> R.drawable.frog_mood_0
                "panda" -> R.drawable.panda_mood_0
                else    -> null
            }
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = if (isSelected) colors.primary.copy(alpha = 0.15f) else colors.cardSurface,
                border = BorderStroke(1.dp, if (isSelected) colors.primary else colors.outline),
                modifier = Modifier
                    .width(100.dp)
                    .clickable { onSelect(mode) },
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.padding(vertical = 14.dp),
                ) {
                    if (neutralDrawable != null) {
                        Image(
                            painter = painterResource(neutralDrawable),
                            contentDescription = label,
                            modifier = Modifier.size(48.dp),
                        )
                    } else {
                        Text("🙂", fontSize = 36.sp)
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isSelected) colors.primary else colors.onSurfaceVariant,
                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
