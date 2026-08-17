package app.gov.uidai.registration.ui.composable

import android.content.Intent
import android.provider.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext

@Composable
fun NoInternetAvailableDialog(isConnected: Boolean){
    val context = LocalContext.current
    if(!isConnected) {
        AlertDialog(
            onDismissRequest = { /*Not Dismissible*/ },
            title = { Text("No Internet") },
            text = { Text("Turn on internet to continue.") },
            confirmButton = {
                TextButton(onClick = {
                    context.startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
                }) {
                    Text("Settings")
                }
            }
        )
    }
}