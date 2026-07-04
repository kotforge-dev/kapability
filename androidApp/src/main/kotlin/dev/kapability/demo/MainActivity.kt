package dev.kapability.demo

import android.app.Activity
import android.os.Bundle
import android.widget.TextView

/**
 * Minimal launcher activity. Its only purpose is to move the app out of the "stopped" state after
 * install so the OS will bind the generated AppFunctionService when we invoke via adb.
 */
class MainActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(TextView(this).apply { text = "Kapability demo — invoke via adb cmd app_function" })
    }
}
