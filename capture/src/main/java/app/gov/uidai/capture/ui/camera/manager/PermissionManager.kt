package app.gov.uidai.capture.ui.camera.manager

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.Settings
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class PermissionManager (private val fragment: Fragment) {

    private var onResult: ((Boolean) -> Unit)? = null
    private var permissionDialog: AlertDialog? = null
    private var pendingPermissions: Array<String> = emptyArray()

    private val requestPermissionsLauncher: ActivityResultLauncher<Array<String>> =
        fragment.registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            val allGranted = results.values.all { it }
            if (allGranted) {
                dismissPermissionDialog()
                onResult?.invoke(true)
            } else {
                // At least one denied → show denied dialog
                showPermissionDeniedDialog()
            }
        }

    private val settingsLauncher: ActivityResultLauncher<Intent> =
        fragment.registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // After returning from settings, re-check the permissions.
            checkPermissions()
        }

    fun requestPermissions(permissions: Array<String>, onResult: (isGranted: Boolean) -> Unit) {
        this.onResult = onResult
        this.pendingPermissions = permissions
        checkPermissions()
    }

    private fun checkPermissions() {
        val context = fragment.requireContext()
        val notGranted = pendingPermissions.filter {
            ContextCompat.checkSelfPermission(context, it) != PackageManager.PERMISSION_GRANTED
        }

        when {
            notGranted.isEmpty() -> {
                dismissPermissionDialog()
                onResult?.invoke(true)
            }
            notGranted.any { fragment.shouldShowRequestPermissionRationale(it) } -> {
                showPermissionRequestDialog(notGranted.toTypedArray())
            }
            else -> {
                requestPermissionsLauncher.launch(pendingPermissions)
            }
        }
    }

    private fun showPermissionRequestDialog(permissions: Array<String>) {
        permissionDialog?.dismiss()
        permissionDialog = AlertDialog.Builder(fragment.requireContext())
            .setTitle("Permissions Required")
            .setMessage("This app needs certain permissions to function properly. Please grant them to continue.")
            .setPositiveButton("Grant") { _, _ ->
                requestPermissionsLauncher.launch(permissions)
            }
            .setNegativeButton("Exit") { _, _ ->
                fragment.activity?.finish()
            }
            .setCancelable(false)
            .create()
        permissionDialog?.show()
    }

    private fun showPermissionDeniedDialog() {
        permissionDialog?.dismiss()
        permissionDialog = AlertDialog.Builder(fragment.requireContext())
            .setTitle("Permissions Required")
            .setMessage("Some permissions are required to use this feature. Please enable them in app settings to continue.")
            .setPositiveButton("Open Settings") { _, _ ->
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                intent.data = Uri.fromParts("package", fragment.requireContext().packageName, null)
                settingsLauncher.launch(intent)
            }
            .setNegativeButton("Exit") { _, _ ->
                fragment.activity?.finish()
            }
            .setCancelable(false)
            .create()
        permissionDialog?.show()
    }

    private fun dismissPermissionDialog() {
        permissionDialog?.dismiss()
        permissionDialog = null
    }
}
