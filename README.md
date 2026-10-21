# Create browser WebWorkers from kotlin easy and fast on Kotlin/JS and Kotlin/Wasm!

Real parallel execution is not possible in a browser environment. As such, Kotlin coroutines cannot do that too.
The only practical way to delegate expensive computation off the main thread is to use [WebWorkers](https://developer.mozilla.org/en-US/docs/Web/API/Web_Workers_API/Using_web_workers).

Trying to operate a webworker from kotlin is a big pain, requires some boilerplate and is a really easy way to cause yourself a headache in the future.

This library does most things for you!

# How
This repository itself contains an example:
 - test-worker - worker module (js + wasmJs)
 - e2e - the test module that invokes the test-worker.

## 1. Define a worker class.
```kotlin

@Serializable
data class ImageInfo(
    val width: Int,
    val height: Int,
    val colorFormat: String
)

@Serializable
data class ImageHandle(val ptr: Int)

// Make sure the js filename matches what is produced by the wasmJs dist task of worker module (see Setup below).
@JSWorker(jsFileName = "image-compressor.js")
class ImageCompressor() {
    
    private val quality: Int
    // Optionally define additional constructors
    constructor() {
        this.quality = 0
    }
    constructor(q: Int) {
        this.quality = surplus
    }
    
    // Define your functions. 
    fun getLibraryVersion(): String {
        return "Hello World"
    }
    
    // Parameter and return types must be @Serializable.
    // Functions can be suspend or not.
    suspend fun createImage(imageInfo: ImageInfo): ImageHandle {
        val imagePtr = yourCreate(imageInfo.width, imageInfo.height, imageInfo.colorFormat)
        return ImageHandle(imagePtr)
    }
    
    // ArrayBuffers and TypedArray types can be annotated with @TransferOwnership.
    // In that case, the ArrayBuffer ownership will be transferred to the worker. 
    fun setImageBytes(img: ImageHandle, @TransferOwnership buffer: ArrayBuffer) {
        yourSetBytes(img, buffer)
    }
    
    // Annotate the function to transfer the returned ArrayBuffer or TypedArray.
    @TransferOwnership
    suspend fun compress(img: ImageHandle): ArrayBuffer {
        val buffer = yourCompress(img, this.quality)
        return buffer
    }
}
```

## 2. Call it from the browser
A proxy class will be generated, appending 'WebWorker' to your name. In this case `ImageCompressorWebWorker`.
```kotlin
val worker = ImageCompressorWebWorker(quality = 80)
scope.launch {
    val img: ImageHandle = worker.createImage(ImageInfo(100, 100, "RGB"))
    
    val bytes: ArrayBuffer = readFile("image.jpg") // pseudocode
    worker.setImageBytes(img, bytes)
    // Since bytes buffer is transferred, it is now detached here and cannot be used.
    
    val compressed: ArrayBuffer = worker.compress(img)
    // Yay!
}
```

## Adding a dependency on an Emscripten-compiled module.
If your goal is to utilise a pre-compiled wasm module from inside the worker, you can add it as a dependency:
```kotlin
@JsWorker("image-compressor.js", "gzip.js")
class ImageCompressor() {...}
```

`gzip.js` is a JavaScript wrapper of the emscripten module. It should be in your worker's module resources.


# Setup
## 1. Include `Kotlin-WebWorker`, `kotlinx-serialization` and `ksp`:

```toml
[versions]
jsworker = "..."
kotlin = "..."

[plugins]
multiplatform = { id = "org.jetbrains.kotlin.multiplatform", version.ref = "kotlin" } 
jsworker = { id = "com.dshatz.jsworker", version.ref = "jsworker" }
serialization = { id = "org.jetbrains.kotlin.plugin.serialization", version.ref = "kotlin" }
ksp = { id = "com.google.devtools.ksp", version = "..." }

[libraries]
jsworker-workers = { module = "com.dshatz.jsworker:workers", version.ref = "jsworker" }
jsworker-ksp = { module = "com.dshatz.jsworker:ksp", version.ref = "jsworker" }
```

## 2. Configure gradle
You will need two Gradle modules - one for the worker and one for the rest.

### The worker module
```kotlin
plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jsworker)
    alias(libs.plugins.serialization)
}

kotlin {
    wasmJs {
        binaries.executable()
        browser()
        asWebWorker() // Register this to be a webworker
    }
    js {
        binaries.executable()
        browser()
        asWebWorker()  // This module exposes both wasmJs and pure js workers.
    }

    sourceSets {
        webMain.dependencies {
            implementation(libs.jsworker.workers)
        }
    }
}

dependencies {
    "kspJs"(libs.jsworker.ksp)
    "kspWasmJs"(libs.jsworker.ksp)
}
```

### The browser (main) module
```kotlin
plugins {
    alias(libs.plugins.multiplatform)
    alias(libs.plugins.ksp)
    alias(libs.plugins.jsworker)
    alias(libs.plugins.serialization)
}

kotlin {
    sourceSets {
        webMain.dependencies {
            implementation(libs.jsworker.workers)
        }
    }
}

dependencies {
    "jsWorker"(project(":myworker"))
    "wasmJsWorker"(project(":myworker"))
}
```



