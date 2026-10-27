package vn.ptit.fwp

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import vn.ptit.fwp.ui.FwpApp
import vn.ptit.fwp.ui.theme.FwpTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ServiceLocator.init(this)
        enableEdgeToEdge()
        setContent {
            FwpTheme {
                FwpApp()
            }
        }
    }
}
