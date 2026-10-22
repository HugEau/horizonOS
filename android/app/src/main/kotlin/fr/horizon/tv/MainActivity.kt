package fr.horizon.tv

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import fr.horizon.tv.ui.HText
import fr.horizon.tv.ui.SkyBackdrop

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            SkyBackdrop()
            HText("Horizon")
        }
    }
}
