package org.brightify.hyperdrive.example.multiplatformx

import org.brightify.hyperdrive.AutoFactory
import org.brightify.hyperdrive.BaseViewModel
import org.brightify.hyperdrive.Provided
import org.brightify.hyperdrive.ViewModel
import kotlinx.coroutines.flow.StateFlow

@ViewModel
@AutoFactory
class ExampleViewModel(
    private val a: String,
    @Provided
    private val b: Int,
): BaseViewModel() {

    val x: String? by published(a)

    val y: List<String> by published(emptyList())
}

//class ExampleViewModel(
//    private val a: String,
//    private val b: Int,
//): BaseViewModel() {
//
//    val x: String? by published(a)
//
//    val observeX: StateFlow<String?>
//        get() = observe(this::x).value
//
//
//    class Factory private constructor() {
//        @kotlin.jvm.JvmField
//        private lateinit var a: String
//
//        constructor(a: String): this() {
//            this.a = a
//        }
//
//        fun create(b: Int): ExampleViewModel {
//            return ExampleViewModel(a, b)
//        }
//    }
//}

fun a() {
//    val x: ExampleViewModel.Factory = TODO()
//    ExampleViewModel.Factory()
    val a = ExampleViewModel.Factory("hello").create(10)
    println(a)
//        .observeX
//    println(a.value)
}
