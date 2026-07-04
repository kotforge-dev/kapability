import SwiftUI
import AppIntents

@main
struct KapabilityDemoApp: App {
    var body: some Scene {
        WindowGroup {
            Text("Kapability iOS demo — CreateNoteIntent available in Shortcuts")
                .padding()
        }
    }
}

/// Exposes CreateNoteIntent to Siri/Shortcuts so it can be discovered manually (optional M0 check).
/// The App Intents test drives `perform()` directly and does not depend on this.
struct KapabilityShortcuts: AppShortcutsProvider {
    static var appShortcuts: [AppShortcut] {
        AppShortcut(
            intent: CreateNoteIntent(),
            phrases: ["Create a note in \(.applicationName)"],
            shortTitle: "Create Note",
            systemImageName: "note.text"
        )
    }
}
