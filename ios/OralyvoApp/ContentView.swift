import SwiftUI

// Placeholder root view -- proves the app builds and launches end to end
// via Codemagic CI (no Mac available locally to verify this any other way).
// Sign-in, recording, and transcription land in follow-up commits once this
// skeleton is confirmed green in CI.
struct ContentView: View {
    var body: some View {
        VStack(spacing: 16) {
            Image(systemName: "waveform")
                .font(.system(size: 48))
                .foregroundStyle(.tint)
            Text("Oralyvo")
                .font(.largeTitle)
                .bold()
            Text("iOS app scaffold — more to come.")
                .foregroundStyle(.secondary)
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
