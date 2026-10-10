package `in`.gov.uidai.contactlessfingersdk

import android.os.Bundle
import android.util.Log
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import `in`.gov.uidai.core.CaptureSDK

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val coreActivityLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        setResult(result.resultCode, result.data)
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Launch the CameraActivity to start the capture process
        launchCoreActivity()
    }

    private fun launchCoreActivity() {
        val intent = CaptureSDK.createIntent(
            context = this,
            requestXml = intent.getStringExtra("request"),
            callingPackage = callingPackage
        )
        Log.d(TAG, "Capture Activity Launched")
        coreActivityLauncher.launch(intent)
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
    }
}

