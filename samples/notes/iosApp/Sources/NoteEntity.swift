import AppIntents

/// The `AppEntity` the assistant receives back — the iOS analogue of the Android `@AppFunctionSerializable
/// NoteResult`. Built directly from the `[String: String]` the shared `invoke()` returns, so the
/// Kotlin<->Swift bridge needs no entity export in M0.
struct NoteEntity: AppEntity {
    let id: String
    let title: String
    let content: String

    init(id: String, title: String, content: String) {
        self.id = id
        self.title = title
        self.content = content
    }

    init(from map: [String: String]) {
        self.init(
            id: map["id"] ?? "",
            title: map["title"] ?? "",
            content: map["content"] ?? ""
        )
    }

    static var typeDisplayRepresentation: TypeDisplayRepresentation { "Note" }

    var displayRepresentation: DisplayRepresentation {
        DisplayRepresentation(title: "\(title)", subtitle: "\(content)")
    }

    static let defaultQuery = NoteEntityQuery()
}

/// Minimal query. Real entity lookup (AppSearch on Android / Spotlight on iOS) is out of scope for M0.
struct NoteEntityQuery: EntityQuery {
    func entities(for identifiers: [String]) async throws -> [NoteEntity] { [] }
    func suggestedEntities() async throws -> [NoteEntity] { [] }
}
