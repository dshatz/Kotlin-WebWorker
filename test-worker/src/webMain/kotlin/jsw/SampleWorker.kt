package jsw

import com.dshatz.jsworker.JSWorker

@JSWorker
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
}