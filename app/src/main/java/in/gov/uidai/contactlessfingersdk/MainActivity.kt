package `in`.gov.uidai.contactlessfingersdk

import android.content.Intent
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import dagger.hilt.android.AndroidEntryPoint
import app.gov.uidai.registration.RegistrationActivity

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startActivity(Intent(this, RegistrationActivity::class.java))
        finish()
    }

    private fun launchRegistrationFlow() {
        startActivity(Intent(this, RegistrationActivity::class.java))
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.i(TAG, "onDestroy()")
    }

    companion object {
        private val TAG = MainActivity::class.simpleName
    }
}

