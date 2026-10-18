package com.dshatz.jsworker

@Target(AnnotationTarget.CLASS)
@Retention(AnnotationRetention.SOURCE)
annotation class JSWorker(val jsFileName: String = "", vararg val dependencyModules: String)
