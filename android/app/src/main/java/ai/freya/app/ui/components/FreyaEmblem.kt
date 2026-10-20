package ai.freya.app.ui.components

import ai.freya.app.R
import androidx.compose.foundation.Image
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource

@Composable
fun FreyaEmblem(
    modifier: Modifier = Modifier,
    contentDescription: String? = "FREYA emblem",
) {
    Image(
        painter = painterResource(R.drawable.ic_freya_emblem),
        contentDescription = contentDescription,
        modifier = modifier,
    )
}
