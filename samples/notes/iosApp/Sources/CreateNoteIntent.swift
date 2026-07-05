// HAND-WRITTEN for the M0 spike — the exact Swift the Kapability KSP processor will generate from a
// commonMain @Capability in M2. It performs no business logic: it dispatches through the single
// KapabilityRuntime.invoke() entry point (RFC D2) into the shared KMP framework and re-encodes the
// result. This proves the App Intents runtime -> Swift -> SKIE async -> commonMain chain.
import AppIntents
import Shared // KMP framework produced by :shared (SKIE-processed)

struct CreateNoteIntent: AppIntent {
    static let title: LocalizedStringResource = "Create Note"
    static let description = IntentDescription("Create a new note with the given title and content")

    @Parameter(title: "Title") var title: String
    @Parameter(title: "Body content") var content: String

    init() {}

    func perform() async throws -> some IntentResult & ReturnsValue<NoteEntity> {
        // SKIE turns the Kotlin `suspend fun invoke(...)` into this Swift `async throws` call.
        let result = try await KapabilityRuntime.shared.invoke(
            id: "createNote",
            params: ["title": title, "content": content]
        )
        return .result(value: NoteEntity(from: result))
    }
}
