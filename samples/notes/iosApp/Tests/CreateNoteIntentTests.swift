import XCTest
import AppIntents
import Shared
@testable import iosApp

/// M1 proof for iOS: the AppIntent's `perform()` dispatches through the GENERATED KapabilityRuntime
/// (produced from @Capability, shared across platforms) via SKIE async into commonMain. Same generated
/// dispatch the Android @AppFunction uses. id "note-1" comes from the shared NoteRepository.
final class CreateNoteIntentTests: XCTestCase {

    override func setUp() {
        // The generated KapabilityRuntime uses an install() DI hook; supply the capability instance.
        KapabilityRuntime.shared.install(noteCapabilities: NoteCapabilities(repo: NoteRepository()))
    }

    func testPerformRoutesThroughSharedCommonMain() async throws {
        let intent = CreateNoteIntent()
        intent.title = "Meeting notes"
        intent.content = "Discuss KMP bridge"
        intent.priority = 3

        let result = try await intent.perform()
        let note = result.value

        XCTAssertNotNil(note)
        // "note-1" is assigned by the shared NoteRepository, proving we reached commonMain.
        XCTAssertEqual(note?.id, "note-1")
        XCTAssertEqual(note?.title, "Meeting notes")
        XCTAssertEqual(note?.content, "Discuss KMP bridge")
        // priority round-trips as Int through the string bridge (M3 type mapping).
        XCTAssertEqual(note?.priority, 3)
    }
}
