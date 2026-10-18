package jsw

import com.dshatz.jsworker.JSWorker
import com.dshatz.jsworker.TransferOwnership
import org.khronos.webgl.Int8Array

@JSWorker(jsFileName = "test-worker.js", "pdfium.js")
class SampleWorker {
    val surplus: Int
    constructor() {
        this.surplus = 0
    }
    constructor(surplus: Int) {
        this.surplus = surplus
    }
    suspend fun sum(a: Int, b: Int): Int {
        return a + b + surplus
    }

    suspend fun diff(a: Int, b: Int): Int {
        return a - b
    }

    fun nonSuspendSum(a: Int, b: Int): Int {
        return a + b + surplus
    }

    fun bytes(arr: ByteArray): ByteArray {
        return arr
    }

    fun buffer(@TransferOwnership arr: Int8Array): Int {
        if (arr.byteLength == 0) error("Received arr is not transferred to webworker.")
        return arr.length
    }

    @TransferOwnership
    fun transferOwnershipBack(@TransferOwnership arr: Int8Array): Int8Array {
        return arr.subarray(0, 10)
    }
}