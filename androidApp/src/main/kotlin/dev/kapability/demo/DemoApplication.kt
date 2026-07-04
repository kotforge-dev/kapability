package dev.kapability.demo

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration

/**
 * The appfunctions runtime discovers enclosing-class factories through an
 * [AppFunctionConfiguration.Provider] on the [Application]. This is how the generated service knows
 * how to construct [NoteFunctions] when an agent invokes `createNote`.
 */
class DemoApplication : Application(), AppFunctionConfiguration.Provider {
    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(NoteFunctions::class.java) { NoteFunctions() }
            .build()
}
