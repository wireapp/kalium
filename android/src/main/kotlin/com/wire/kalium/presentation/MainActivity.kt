package com.wire.kalium.presentation

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import com.wire.kalium.network.Greeting
import com.wire.kalium.network.api.auth.AuthApi
import com.wire.kalium.network.api.auth.AuthApiImp

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MainLayout()
        }
    }
}

@Composable
fun MainLayout() {
    Column {
        Text("Greetings!")
        Text(Greeting().greet())
    }
}
