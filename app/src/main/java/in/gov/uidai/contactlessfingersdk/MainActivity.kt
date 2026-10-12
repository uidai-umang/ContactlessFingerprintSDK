
package `in`.gov.uidai.contactlessfingersdk

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import app.gov.uidai.registration.ContactlessRegistrationSdk
import app.gov.uidai.registration.RegistrationConfig

class MainActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = ContactlessRegistrationSdk.createIntent(
            context = this
        )

        startActivity(intent)
        finish()
    }
}
