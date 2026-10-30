package dev.imranr.obtainium

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.lifecycle.viewmodel.compose.viewModel
import dev.imranr.obtainium.ui.ObtainiumScreen
import dev.imranr.obtainium.ui.ObtainiumViewModel

class MainActivity : ComponentActivity() {
 override fun onCreate(savedInstanceState: Bundle?) {
  super.onCreate(savedInstanceState)
  setContent {
   MaterialTheme {
    Surface {
     val model: ObtainiumViewModel = viewModel()
     ObtainiumScreen(model)
    }
   }
  }
 }
}
