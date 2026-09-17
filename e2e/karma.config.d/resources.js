config.files.push({
    pattern: __dirname + "/kotlin/workers/**",
    watched: false,
    included: false,
    served: true,
    nocache: false
});
config.set({
    "proxies": {
        "/workers/": __dirname + "/kotlin/workers/"
    }
});