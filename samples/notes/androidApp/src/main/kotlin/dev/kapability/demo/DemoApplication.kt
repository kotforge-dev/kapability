package dev.kapability.demo

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import dev.kapability.KapabilityRuntime
import dev.kapability.NoteCapabilities
import dev.kapability.NoteRepository
import dev.kapability.generated.NoteCapabilitiesAppFunctions

/**
 * The only wiring a consumer writes:
 *  1. Install the capability instance(s) into the generated [KapabilityRuntime] (supplies the
 *     dependencies the generated dispatch can't construct itself).
 *  2. Provide the appfunctions factory for the generated wrapper class.
 */
class DemoApplication : Application(), AppFunctionConfiguration.Provider {

    override fun onCreate() {
        super.onCreate()
        KapabilityRuntime.install(NoteCapabilities(NoteRepository()))
    }

    override val appFunctionConfiguration: AppFunctionConfiguration
        get() = AppFunctionConfiguration.Builder()
            .addEnclosingClassFactory(NoteCapabilitiesAppFunctions::class.java) { NoteCapabilitiesAppFunctions() }
            .build()
}
