// SPDX-License-Identifier: Apache-2.0
package org.dualmind.android

import android.app.Application

class DualMindApplication : Application() {
    // One database, model engine and operation gate per process, including Activity recreation.
    val container: AppContainer by lazy { AppContainer(this) }
}
