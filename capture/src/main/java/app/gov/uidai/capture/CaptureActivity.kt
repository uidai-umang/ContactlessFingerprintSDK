package app.gov.uidai.capture

import android.content.Context
import android.content.Intent
import android.content.res.Resources
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.FragmentResultListener
import androidx.navigation.fragment.NavHostFragment
import app.gov.uidai.capture.ui.camera.CameraFragment
import dagger.hilt.android.AndroidEntryPoint
import app.gov.uidai.capture.databinding.ActivityCameraBinding
import `in`.gov.uidai.network.model.local.PidOptions
import `in`.gov.uidai.network.model.local.SDKResponse
import `in`.gov.uidai.utility.constants.JourneyConstant
import `in`.gov.uidai.utility.constants.ResultCode
import `in`.gov.uidai.utility.mapper.XmlMapper
import java.io.File

@AndroidEntryPoint
class CaptureActivity : AppCompatActivity() {

    private lateinit var binding: ActivityCameraBinding

    private var txnId: String? = null
    private var returnFullImage: Boolean? = null
    private var returnCroppedImage: Boolean? = null

    private val fragmentRequestListener = FragmentResultListener { _, result ->
        val resultCode = result.getInt(CameraFragment.KEY_RESULT_CODE)
        val finalImage = result.getString(CameraFragment.KEY_FINAL_IMAGE)
        val fullImage = result.getString(CameraFragment.KEY_FULL_IMAGE)
        val croppedImage = result.getString(CameraFragment.KEY_CROPPED_IMAGE)
        val blurScore = result.getFloat(CameraFragment.KEY_BLUR_SCORE, 0f)
        val brightnessScore = result.getFloat(CameraFragment.KEY_BRIGHTNESS_SCORE, 0f)
        val glareScore = result.getFloat(CameraFragment.KEY_GLARE_SCORE, 0f)


        var sdkResponse = SDKResponse()

        when (resultCode) {
            ResultCode.CAPTURE_SUCCESS -> {
                val finalImageUri = saveBase64ToFile(
                    context = this,
                    name = "final_image",
                    base64String = finalImage
                )

                returnFullImage?.let {
                    if(it){
                        val fullImageUri = saveBase64ToFile(
                            context = this,
                            name = "full_image",
                            base64String = fullImage
                        )
                        sdkResponse = sdkResponse.copy(fullImage = fullImageUri.toString())
                    }
                }

                returnCroppedImage?.let {
                    if(it){
                        val croppedImageUri = saveBase64ToFile(
                            context = this,
                            name = "cropped_image",
                            base64String = croppedImage
                        )
                        sdkResponse = sdkResponse.copy(croppedImage = croppedImageUri.toString())
                    }
                }

                sdkResponse = sdkResponse.copy(
                    blurScore = blurScore,
                    brightnessScore = brightnessScore,
                    glareScore = glareScore
                )

                Log.d(
                    TAG,
                    XmlMapper.writePretty(sdkResponse)
                )

                finalImageUri.let { uri ->
                    // Step 2: Return to parent activity
                    Log.d(TAG, "ACTIVITY_RESULT -- OK -- Data: $uri")
                    val resultIntent = Intent().apply {
                        data = uri
                        flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                        putExtra(
                            JourneyConstant.RESPONSE,
                            XmlMapper.write(sdkResponse)
                        )
                    }
                    setResult(ResultCode.CAPTURE_SUCCESS, resultIntent)
                }
            }

            else -> {
                Log.d(TAG, "ACTIVITY_RESULT -- Failed with code: $resultCode")
                setResult(resultCode)
            }
        }
        finish()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()
        binding = ActivityCameraBinding.inflate(layoutInflater)
        setContentView(binding.root)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            insets.getInsets(WindowInsetsCompat.Type.systemBars()).apply {
                view.setPadding(left, top, right, bottom)
            }
            insets
        }

        // Process intent and get the pid options, initialize Pid Factory
        intent.getStringExtra(JourneyConstant.REQUEST)?.let { req ->
            try {
                val pidOptions : PidOptions = XmlMapper.read(req)
                pidOptions.let {
                    txnId = it.custOpts?.param?.find { param ->
                        (param.name == "txnId")
                    }?.value
                    returnFullImage = it.custOpts?.param?.find { param ->
                        (param.name == "fullImage")
                    }?.value == "true"
                    returnCroppedImage = it.custOpts?.param?.find { param ->
                        (param.name == "croppedImage")
                    }?.value == "true"
                }

                Log.d(TAG, "Transaction Id: $txnId")
            } catch (_: Exception) {
                Toast.makeText(
                    this,
                    "Wrong Input PID Options Provided!",
                    Toast.LENGTH_LONG
                ).show()
                throw IllegalStateException()
            }
        } ?: {
            Toast.makeText(
                this,
                "No Input PID Options Provided!",
                Toast.LENGTH_LONG
            ).show()
            throw IllegalStateException()
        }

        val navHostFragment = supportFragmentManager.findFragmentById(
            R.id.nav_host_fragment
        ) as NavHostFragment

        val startDestinationArgs = Bundle().apply {
            putString("txnId", txnId)
        }

        try {
            navHostFragment.navController.setGraph(
                graphResId = R.navigation.sdk_nav_graph,
                startDestinationArgs = startDestinationArgs
            )
        } catch (e: Resources.NotFoundException) {
            Log.e(TAG, "Nav graph not found in packaged AAR", e)
        }

        // Set fragment result listener to get result from the fragment
        supportFragmentManager.setFragmentResultListener(
            CameraFragment.Companion.CAPTURE_FRAGMENT_RESULT,
            this,
            fragmentRequestListener
        )
    }

    fun saveBase64ToFile(context: Context, name: String, base64String: String?): Uri {
        val dir = File(context.cacheDir, "shared").apply { mkdirs() }
        val file = File(dir, "$name.txt")

        file.outputStream().bufferedWriter().use { writer ->
            writer.write(base64String)
        }

        return FileProvider.getUriForFile(
            /* context = */ context,
            /* authority = */ "${context.packageName}.file_provider",
            /* file = */ file
        )
    }

    override fun onPause() {
        super.onPause()
        Log.i(TAG, "onPause()")
    }

    override fun onStop() {
        super.onStop()
        Log.i(TAG, "onStop()")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")
    }

    companion object {
        private val TAG = CaptureActivity::class.simpleName
    }
}