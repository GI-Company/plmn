package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.plmn.ui.PLMNApp
import com.example.plmn.ui.viewmodel.PLMNViewModel
import com.example.ui.theme.PLMNTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PLMNTheme(darkTheme = true) {
                val viewModel: PLMNViewModel = viewModel()
                PLMNApp(viewModel = viewModel)
            }
        }
    }
}
