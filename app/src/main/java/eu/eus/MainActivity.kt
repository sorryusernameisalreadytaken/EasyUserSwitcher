package eu.eus

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import eu.eus.databinding.ActivityMainBinding
import kotlinx.coroutines.launch

/**
 * The main activity provides a simple interface to pair and connect to the
 * device's own ADB daemon over TCP and then query the list of configured
 * users. Each discovered user can be tapped to perform a temporary switch
 * that automatically reverts to the owner when the device becomes idle.
 */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var adbHelper: AdbHelper
    private var userList: MutableList<Pair<Int, String>> = mutableListOf()
    private lateinit var adapter: ArrayAdapter<String>

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        adbHelper = AdbHelper(this)

        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, mutableListOf())
        binding.userList.adapter = adapter

        binding.pairButton.setOnClickListener { onPairClicked() }
        binding.connectButton.setOnClickListener { onConnectClicked() }
        binding.loadUsersButton.setOnClickListener { onLoadUsersClicked() }

        binding.userList.setOnItemClickListener { _, _, position, _ ->
            val entry = userList.getOrNull(position)
            if (entry != null) {
                val userId = entry.first
                val name = entry.second
                Toast.makeText(this, "Switching to $name ($userId)", Toast.LENGTH_SHORT).show()
                lifecycleScope.launch {
                    adbHelper.switchUser(userId, 0)
                }
            }
        }

        updateStatus()
    }

    private fun onPairClicked() {
        val host = binding.hostInput.text.toString().trim().ifEmpty { "127.0.0.1" }
        val portText = binding.pairingPortInput.text.toString().trim()
        val code = binding.pairingCodeInput.text.toString().trim()
        if (portText.isEmpty() || code.isEmpty()) {
            Toast.makeText(this, "Please enter pairing port and code", Toast.LENGTH_SHORT).show()
            return
        }
        val port = portText.toIntOrNull()
        if (port == null) {
            Toast.makeText(this, "Invalid pairing port", Toast.LENGTH_SHORT).show()
            return
        }
        updateStatus("Pairing…")
        lifecycleScope.launch {
            val success = adbHelper.pair(host, port, code)
            if (success) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Pairing successful", Toast.LENGTH_SHORT).show()
                    updateStatus()
                }
            } else {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Pairing failed", Toast.LENGTH_SHORT).show()
                    updateStatus()
                }
            }
        }
    }

    private fun onConnectClicked() {
        val host = binding.hostInput.text.toString().trim().ifEmpty { "127.0.0.1" }
        val port = binding.connectPortInput.text.toString().toIntOrNull() ?: 5555
        updateStatus("Connecting…")
        lifecycleScope.launch {
            val connected = adbHelper.connect(host, port)
            runOnUiThread {
                if (connected) {
                    Toast.makeText(this@MainActivity, "Connected", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Already connected or failed", Toast.LENGTH_SHORT).show()
                }
                updateStatus()
            }
        }
    }

    private fun onLoadUsersClicked() {
        if (!adbHelper.isConnected()) {
            Toast.makeText(this, "Connect to ADB first", Toast.LENGTH_SHORT).show()
            return
        }
        updateStatus("Loading users…")
        lifecycleScope.launch {
            val result = adbHelper.executeShell("pm list users")
            if (result == null) {
                runOnUiThread {
                    Toast.makeText(this@MainActivity, "Failed to list users", Toast.LENGTH_SHORT).show()
                    updateStatus()
                }
                return@launch
            }
            val users = adbHelper.parseUsers(result)
            userList = users.toMutableList()
            val names = users.map { (id, name) -> "$name ($id)" }
            runOnUiThread {
                adapter.clear()
                adapter.addAll(names)
                adapter.notifyDataSetChanged()
                updateStatus()
            }
        }
    }

    private fun updateStatus(message: String? = null) {
        val status = if (message != null) {
            message
        } else {
            if (adbHelper.isConnected()) "connected" else "not connected"
        }
        binding.statusText.text = "Status: $status"
    }
}