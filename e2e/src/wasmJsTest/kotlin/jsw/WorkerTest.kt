package jsw

import io.kotest.matchers.shouldBe
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.time.Duration.Companion.minutes

class WorkerTest {
    @OptIn(ExperimentalWasmJsInterop::class)
    @Test
    fun simple() = runTest(timeout = 10.minutes) {
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
}