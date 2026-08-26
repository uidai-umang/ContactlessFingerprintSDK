package app.gov.uidai.registration.ui.dashboard.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import app.gov.uidai.registration.ui.theme.AppButton

@Composable
fun BottomActionBar(onNewCollection: () -> Unit, modifier: Modifier = Modifier) {
    AppButton(
        text = "New collection",
        icon = Icons.Default.Add,
        iconDescription = "New collection",
        onClick = onNewCollection,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface)
            .padding(16.dp)
    )
}
