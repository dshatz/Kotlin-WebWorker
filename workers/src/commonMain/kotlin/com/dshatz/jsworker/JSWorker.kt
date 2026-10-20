package com.dshatz.jsworker

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.BINARY)
annotation class JSWorker(val jsFileName: String = "", vararg val dependencyModules: String)
