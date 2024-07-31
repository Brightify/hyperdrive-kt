package org.brightify.hyperdrive.utils

import kotlin.native.ref.WeakReference

@OptIn(kotlin.experimental.ExperimentalNativeApi::class)
public actual typealias WeakReference<T> = WeakReference<T>
