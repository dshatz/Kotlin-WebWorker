const params = new URLSearchParams(self.location.search);
const scriptName = params.get("script");

if (!scriptName) {
    throw new Error("Worker loader missing required 'script' query parameter.");
}

importScripts(scriptName + ".js")

self[scriptName].then(function() {
    console.log("Worker " + scriptName +": wasm loaded.")
    self.postMessage("READY")
})