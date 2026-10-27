package com.bahikhata.app

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.bahikhata.app.data.Cloud
import com.bahikhata.app.data.Repo
import com.bahikhata.app.ui.AppRoot
import com.bahikhata.app.ui.BahiTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        Repo.init(this)
        Cloud.init(this)
        setContent {
            BahiTheme {
                AppRoot()
            }
        }
    }
}
