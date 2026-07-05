package com.example.notes

import android.app.Application
import androidx.appfunctions.service.AppFunctionConfiguration
import dev.kotforge.kapability.KapabilityRuntime
import com.example.notes.NoteCapabilities
import com.example.notes.NoteRepository
import dev.kotforge.kapability.generated.NoteCapabilitiesAppFunctions

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
