package jsw

import com.dshatz.jsworker.Worker
import com.dshatz.jsworker.send
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch

fun main() {
    /*GlobalScope.launch {
        try {
            val w = Worker("workers/test-worker.js")
            println("Sending Hello")
            val result = w.send("Hello")
            println("Result: ${result.data}")
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }*/
}