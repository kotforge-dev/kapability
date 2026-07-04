import XCTest
import AppIntents
@testable import iosApp

/// M0 exit proof for iOS: drive the hand-written AppIntent's `perform()` and assert the returned
/// NoteEntity carries data produced by the shared commonMain implementation (id "note-1" comes from
/// NoteRepository.create). This exercises Swift -> SKIE async -> KapabilityRuntime.invoke() -> commonMain.
final class CreateNoteIntentTests: XCTestCase {

    func testPerformRoutesThroughSharedCommonMain() async throws {
        let intent = CreateNoteIntent()
        intent.title = "Meeting notes"
        intent.content = "Discuss KMP bridge"

        let result = try await intent.perform()
        let note = result.value

        XCTAssertNotNil(note)
        // "note-1" is assigned by the shared NoteRepository, proving we reached commonMain.
        XCTAssertEqual(note?.id, "note-1")
        XCTAssertEqual(note?.title, "Meeting notes")
        XCTAssertEqual(note?.content, "Discuss KMP bridge")
    }
}
