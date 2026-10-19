package jsw

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import org.khronos.webgl.ArrayBuffer
import org.khronos.webgl.Int8Array
import org.khronos.webgl.get
import kotlin.coroutines.suspendCoroutine
import kotlin.random.Random
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class WorkerTest {
    @Test
    fun simple() = runTest {
        val worker = SampleWorkerWebWorker()
        val sum = worker.sum(1, 10)
        sum shouldBe 11
    }

    @OptIn(ExperimentalWasmJsInterop::class)
    @Test
    fun concurrent() = runTest(timeout = 10.minutes) {
        val worker = SampleWorkerWebWorker()

        val results = (1..10).map {
            async {
                val response = worker.sum(1, it)
                println(response)
                response
            }
        }.awaitAll()

        results shouldBe (2..11).toList()

        worker.diff(10, 1) shouldBe 9
    }

    @Test
    fun constructorParams() = runTest {
        val worker = SampleWorkerWebWorker(100)
        worker.sum(1, 10) shouldBe 111
    }

    @Test
    fun bytes() = runTest {
        val worker = SampleWorkerWebWorker()
        val bytes = Random.nextBytes(10)
        worker.bytes(bytes) shouldBe bytes
    }

    @Test
    fun transfer() = runTest {
        val worker = SampleWorkerWebWorker()
        val arr = Int8Array(5)
        arr.byteLength shouldBe 5
        worker.array(arr) shouldBe 5
        arr.byteLength shouldBe 0
        isBufferDetached(arr.buffer) shouldBe true
    }

    @Test
    fun transferBack() = runTest {
        val worker = SampleWorkerWebWorker()
        val arr = Int8Array(100)
        arr.byteLength shouldBe 100
        val oldBuffer = arr.buffer

        val newArrSameBuffer = worker.subArrayZeroCopy(arr, 10)

        isBufferDetached(arr.buffer) shouldBe true
        arr.byteLength shouldBe 0

        isBufferDetached(newArrSameBuffer.buffer) shouldBe false
        newArrSameBuffer.byteLength shouldBe 10

        isSameBufferReference(oldBuffer, newArrSameBuffer.buffer)
    }

    @Test
    fun transferBuffer() = runTest {
        val size = 1024*1024
        val worker = SampleWorkerWebWorker()
        val buffer = ArrayBuffer(size)
        val returnedBuffer = worker.buffer(buffer)
        isSameBufferReference(buffer, returnedBuffer)

        buffer.byteLength shouldBe 0
        isBufferDetached(buffer) shouldBe true

        returnedBuffer.byteLength shouldBe size
        isBufferDetached(returnedBuffer) shouldBe false

        val arr = Int8Array(returnedBuffer, 0, 100)
        arr[0] shouldBe 42
    }

}

@JsFun("(a, b) => a === b")
private external fun isSameBufferReference(a: ArrayBuffer, b: ArrayBuffer): Boolean

@JsFun("(buffer) => buffer.detached")
private external fun isBufferDetached(buffer: JsAny): Boolean