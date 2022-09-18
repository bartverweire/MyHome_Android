package com.example.myhome

import android.util.Log
import androidx.compose.runtime.mutableStateOf
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.myhome.components.Light
import com.example.myhome.components.Shutter
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import io.ktor.utils.io.charsets.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.nio.ByteBuffer

class MyHomeViewModel: ViewModel() {
    var lightsState = mutableStateOf(emptyList<Light>())
    var shuttersState = mutableStateOf(emptyList<Shutter>())

    var lightsMap: HashMap<Int, Int> = HashMap<Int, Int>()
    var shuttersMap: HashMap<Int, Int> = HashMap<Int, Int>()

    val ownHost: String = "192.168.0.103"
    val ownPort: Int = 20000
    val ownCommandHandshake: String = "*99*0##"
    val ownMonitorHandshake: String = "*99*1##"
    val ownMessagePattern: Regex = Regex("""^\*(\d+)\*(\d+)\*(\d+)$""")

    val responseChannel = Channel<String>(10)

    //var ownCommandSocket: Socket? = null
    //var ownMonitorSocket: Socket? = null
    //var ownCommandWriteChannel: ByteWriteChannel? = null
    //var ownCommandReadChannel: ByteReadChannel? = null
    //var ownMonitorReadChannel: ByteReadChannel? = null

    init {
        getLights()
        getShutters()

        viewModelScope.launch(Dispatchers.IO) {
            monitorOWN()
        }
        /*
        viewModelScope.launch(Dispatchers.IO) {
            setupCommandSocket()
        }
        */
        /*
        viewModelScope.launch(Dispatchers.IO) {
            commandChannel.send(ownCommandHandshake)
        }
        */
        viewModelScope.launch(Dispatchers.IO) {
            processResponseQueue()
        }
    }

    fun getLights() {
        val lights = listOf(
            Light(31, "Bureau Centraal", false),
            Light(32, "Bureau Kasten", false),
            Light(35, "Salon", true),
            Light(36, "Eetkamer", true)
        )

        lightsState.value = lights
        lights.forEachIndexed {
            index, light -> lightsMap.put(light.id, index)
        }
    }

    fun getShutters() {
        val shutters = listOf(
            //Shutter(81, "Bureau Voor"),
            Shutter(82, "Bureau Zij"),
            //Shutter(83, "Living Salon"),
            //Shutter(84, "Living Eetkamer")
        )

        shuttersState.value = shutters
        shutters.forEachIndexed {
            index, shutter -> shuttersMap.put(shutter.id, index)
        }
    }

    fun updateLightState(id: Int, newState: Int) {
        val itemIndex = lightsMap.get(id)

        if (itemIndex != null) {
            val lights = lightsState.value.toMutableList()

            val light = lights[itemIndex]
            lights[itemIndex] = light.copy(state = newState)

            lightsState.value = lights
        }
    }

    fun updateShutterState(id: Int, newState: Int) {
        val itemIndex = shuttersMap.get(id)

        if (itemIndex != null) {
            val shutters = shuttersState.value.toMutableList()

            val shutter = shutters[itemIndex]
            shutters[itemIndex] = shutter.copy(state = newState)

            shuttersState.value = shutters
        }
    }

    fun changeLightState(id: Int, newState: Int) {
        val itemIndex = lightsMap.get(id)

        if (itemIndex != null) {
            val lights = lightsState.value.toMutableList()

            val light = lights[itemIndex]
            lights[itemIndex] = light.copy(state = newState)

            lightsState.value = lights

            viewModelScope.launch() {
                sendMessages(arrayOf<String>(buildCommand(1, newState, light.id)))
            }
        }
    }

    fun getLightsStatus() {
        val lights = lightsState.value.toMutableList()

        val statusCommands = lights.map {
            light -> buildStatusCommand(1, light.id)
        }

        viewModelScope.launch(Dispatchers.IO) {
            sendMessages(statusCommands.toTypedArray())
        }
    }

    fun getShuttersStatus() {
        val shutters = shuttersState.value.toMutableList()

        val statusCommands = shutters.map {
                shutter -> buildStatusCommand(1, shutter.id)
        }

        viewModelScope.launch(Dispatchers.IO) {
            sendMessages(statusCommands.toTypedArray())
        }
    }

    fun changeShutterState(id: Int, newState: Int) {
        val itemIndex = shuttersMap.get(id)

        if (itemIndex != null) {
            val shutters = shuttersState.value.toMutableList()

            val shutter = shutters[itemIndex]
            shutters[itemIndex] = shutter.copy(state = newState)

            shuttersState.value = shutters

            viewModelScope.launch(Dispatchers.IO) {
                sendMessages(arrayOf<String>(buildCommand(2, newState, shutter.id)))
            }
        }
    }

    suspend fun getSocket(): Socket {
        val selectorManager = SelectorManager(Dispatchers.IO)
        val socket = aSocket(selectorManager).tcp().connect(ownHost, ownPort)

        return socket
    }

    suspend fun monitorOWN() {
        val ownMonitorSocket = getSocket()

        val ownMonitorWriteChannel = ownMonitorSocket.openWriteChannel()
        val ownMonitorReadChannel = ownMonitorSocket.openReadChannel()

        ownMonitorWriteChannel.writeAvailable(ByteBuffer.wrap(ownMonitorHandshake.toByteArray(Charsets.US_ASCII)))
        ownMonitorWriteChannel.flush()

        viewModelScope.launch(Dispatchers.IO) {
            while (true) {
                val buffer: ByteBuffer = ByteBuffer.allocate(32)

                ownMonitorReadChannel!!.readAvailable(buffer)
                val message = String(buffer.array())

                responseChannel.send(message)
            }
        }
    }

    suspend fun processResponseQueue() {
        while (true) {
            val response = responseChannel.receive()
            Log.i("MyHome", "--> response received ${response}")

            parseResponse(response)
        }
    }

    fun parseResponse(response: String) {
        Log.i("MyHome", "Parsing response: " + response)
        val messages = response.split("##")

        messages.forEach {
            message ->
            Log.i("MyHome", "treating message: " + message)

            if (ownMessagePattern.matches(message)) {
                Log.i("MyHome", "Valid message")
                val group: MatchResult? = ownMessagePattern.find(message)

                val (who, what, where) = group!!.destructured

                when (who.toInt()) {
                    1 -> updateLightState(where.toInt(), what.toInt())
                    2 -> updateShutterState(where.toInt(), what.toInt())
                }
            } else {
                Log.i("MyHome", "Not a light or shutter message: " + message)
            }
        }
    }

    suspend fun sendMessages(messages: Array<String>) {
        val commands = arrayOf<String>(ownCommandHandshake).plus(messages)

        viewModelScope.launch(Dispatchers.IO) {
            val ownCommandSocket = getSocket()

            val ownCommandWriteChannel = ownCommandSocket.openWriteChannel()
            val ownCommandReadChannel = ownCommandSocket.openReadChannel()

            commands.forEachIndexed {
                    index, command ->
                Log.d("MyHome", "Sending message ${command} to ownCommandWriteChannel")
                ownCommandWriteChannel.writeAvailable(ByteBuffer.wrap(command.toByteArray(Charsets.US_ASCII)))
                ownCommandWriteChannel.flush()

                val responseBuffer = ByteBuffer.allocate(32)

                Log.d("MyHome", "OWN Command Read Channel : reading")
                val bytesRead = ownCommandReadChannel.readAvailable(responseBuffer)
                Log.d("MyHome", "OWN Command Read Channel ${bytesRead} bytes read")

                if (bytesRead > 0) {
                    val message = String(responseBuffer.array())
                    Log.i("MyHome", "--> OWN Command Channel Response: ${message}")
                    responseChannel.send(message)
                }
            }

            Log.d("MyHome", "Waiting for more responses")
            for (n in 1..10) {
                val responseBuffer = ByteBuffer.allocate(32)

                Log.d("MyHome", "OWN Command Read Channel ${n}: reading")
                val bytesRead = ownCommandReadChannel.readAvailable(responseBuffer)
                Log.d("MyHome", "OWN Command Read Channel ${bytesRead} bytes read")

                if (bytesRead > 0) {
                    val message = String(responseBuffer.array())
                    Log.i("MyHome", "--> OWN Command Channel Response: ${message}")
                    responseChannel.send(message)
                } else {
                    break
                }

                delay(50)
            }

            Log.d("MyHome", "Closing OWN Command Socket")
            ownCommandSocket.close()

        }
    }

    /*
    suspend fun setupCommandSocket() {
        val ownCommandSocket = getSocket()

        viewModelScope.launch(Dispatchers.IO) {
            var ownCommandWriteChannel = ownCommandSocket.openWriteChannel()
            while (true) {
                val command = commandChannel.receive()
                Log.i("MyHome", "--> commandChannel received ${command}, sending to ownCommandWriteChannel")

                if (ownCommandWriteChannel.isClosedForWrite) {
                    ownCommandWriteChannel = ownCommandSocket.openWriteChannel()
                }

                ownCommandWriteChannel.writeAvailable(ByteBuffer.wrap(command.toByteArray(Charsets.US_ASCII)))
                ownCommandWriteChannel.flush()
            }
        }

        viewModelScope.launch(Dispatchers.IO) {
            val ownCommandReadChannel = ownCommandSocket.openReadChannel()

            while (true) {
                ownCommandReadChannel.availableForRead
                val responseBuffer = ByteBuffer.allocate(256)
                val bytesRead = ownCommandReadChannel.readAvailable(responseBuffer)

                if (bytesRead > 0) {
                    val message = String(responseBuffer.array())
                    Log.i("MyHome", "--> OWN Command Channel Response: ${message}")
                }

                delay(100)
            }
        }
    }
    */
    fun buildCommand(who: Int, what: Int, where: Int): String {
        val message: String = "*${who}*${what}*${where}##"
        Log.i("MyHome", "--> Building Command Message ${message}")

        return message
    }

    fun buildStatusCommand(who: Int, where: Int): String {
        val message: String = "*#${who}*${where}##"
        Log.i("MyHome", "--> Building Status Command Message ${message}")

        return message
    }

}


