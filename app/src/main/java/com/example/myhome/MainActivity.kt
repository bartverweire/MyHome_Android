package com.example.myhome

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.sharp.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.MutableState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.example.myhome.components.Light
import com.example.myhome.components.Shutter
import com.example.myhome.ui.theme.MyHomeTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MyHomeTheme {
                // A surface container using the 'background' color from the theme
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colors.background
                ) {
                    MyHomeApp()
                }
            }
        }
    }
}

@Composable
fun MyHomeApp() {
    val viewModel: MyHomeViewModel = viewModel()
    val navController = rememberNavController()
    NavHost(navController, startDestination = "lights") {
        composable(route="lights") {
            LightsScreen(viewModel.lightsState,
                changeState = { id, newState -> viewModel.changeLightState(id, newState)},
                refresh = { viewModel.getLightsStatus() })

        }
        composable(route="shutters") {
            ShuttersScreen(viewModel.shuttersState) {
                id, newState ->
                viewModel.changeShutterState(id, newState)
            }
        }
    }
}

@Composable
fun LightsScreen(state: MutableState<List<Light>>, changeState: (Int, Int) -> Unit, refresh: () -> Unit) {
    Column {
        state.value.forEach {
            light -> LightUIItem(light, changeState)
        }
        Button(onClick = refresh ) {
            Text("Refresh")
        }
    }
}

@Composable
fun LightUIItem(light: Light, onClick: (Int, Int) -> Unit) {
    Row (
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(32.dp)
    ){
        Text(
            light.name,
            Modifier
                .weight(0.8f)
                .padding(horizontal = 8.dp, vertical = 1.dp)
        )
        Switch(
            checked = (light.state == 1),
            onCheckedChange = {
                val newState: Int = if (it) 1 else 0
                onClick(light.id, newState)
            },
            Modifier
                .weight(0.2f)
                .padding(end = 4.dp)
        )
    }
}


@Composable
fun ShuttersScreen(state: MutableState<List<Shutter>>, changeState: (Int, Int) -> Unit) {
    Column {
        state.value.forEach {
                shutters -> ShutterUI(shutters, changeState)
        }
    }
}


@Composable
fun ShutterUI(shutter: Shutter, onClick: (Int, Int) -> Unit) {
    Row (
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.height(32.dp)
    ){
        Text(
            shutter.name,
            Modifier
                .weight(0.8f)
                .padding(horizontal = 8.dp, vertical = 1.dp)
        )
        Row {
            Image(
                imageVector = Icons.Sharp.KeyboardArrowDown,
                contentDescription = "Down",
                modifier = Modifier.clickable {
                    onClick(shutter.id, 0)
                }
            )
            Image(
                imageVector = Icons.Sharp.Close,
                contentDescription = "Stop",
                modifier = Modifier.clickable {
                    onClick(shutter.id, 1)
                }
            )
            Image(
                imageVector = Icons.Sharp.KeyboardArrowUp,
                contentDescription = "Up",
                modifier = Modifier.clickable {
                    onClick(shutter.id, 2)
                }
            )
        }
    }
}




@Preview(showBackground = true)
@Composable
fun DefaultPreview() {
    MyHomeTheme {
        MyHomeApp()
    }
}