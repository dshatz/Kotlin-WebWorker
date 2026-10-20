package com.dshatz.jsworker

@Target(AnnotationTarget.VALUE_PARAMETER, AnnotationTarget.FUNCTION)
@Retention(AnnotationRetention.BINARY)
/**
 *  - When applied to a function parameter that is a JavaScript [TypedArray](https://developer.mozilla.org/en-US/docs/Web/JavaScript/Reference/Global_Objects/TypedArray)(e.q. Int8Array), the underlying buffer's ownership will be transferred to the web worker.
 *  - When applied to a function returning an instance of TypedArray, the underlying buffer's ownership will be transferred back to the browser.
 */
annotation class TransferOwnership()
