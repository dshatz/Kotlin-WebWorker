config.set({
                        // Time allowed for Karma to wait for a browser message/response
                        browserNoActivityTimeout: 60000,
                        // Time allowed for browser to disconnect/reconnect
                        browserDisconnectTimeout: 60000,
                        // Configures the test runner timeout (e.g. Mocha/Jasmine under Karma)
                        client: {
                            mocha: {
                                timeout: 60000
                            },
                            jasmine: {
                                timeoutInterval: 60000
                            }
                        }
                    });