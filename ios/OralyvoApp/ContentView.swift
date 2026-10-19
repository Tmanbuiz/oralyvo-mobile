import SwiftUI

struct ContentView: View {
    @StateObject private var api = OralyvoAPI()
    @State private var showSignIn = false
    @State private var error: String?
    var body: some View {
        Group {
            if let session = api.session {
                TabView {
                    RecordView(api: api).tabItem { Label("Record", systemImage: "mic.fill") }
                    HistoryView(api: api).tabItem { Label("History", systemImage: "clock") }
                    NavigationStack {
                        Form {
                            Section("Account") { Text(session.email ?? "Signed in") }
                            Section {
                                Link("Privacy policy", destination: URL(string: "https://oralyvo.app/privacy/")!)
                                Link("Open Oralyvo on the web", destination: OralyvoAPI.origin)
                            }
                            Section { Button("Sign out", role: .destructive) { api.signOut() } }
                        }.navigationTitle("Settings")
                    }.tabItem { Label("Settings", systemImage: "gearshape") }
                }.id(session.userId)
            } else {
                NavigationStack {
                    VStack(spacing: 24) {
                        Spacer()
                        Image(systemName: "waveform").font(.system(size: 64)).foregroundStyle(.blue)
                        Text("Oralyvo").font(.largeTitle.bold())
                        Text("Turn your voice into clear text.") .font(.title3).multilineTextAlignment(.center)
                        Text("Record, transcribe, translate, and save notes to your Oralyvo account.")
                            .foregroundStyle(.secondary).multilineTextAlignment(.center)
                        Button("Sign in or create an account") { showSignIn = true }
                            .buttonStyle(.borderedProminent).controlSize(.large)
                        Spacer()
                        Text("Recordings are sent securely to Oralyvo for transcription when you choose Transcribe.")
                            .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                        Link("Privacy policy", destination: URL(string: "https://oralyvo.app/privacy/")!)
                    }.padding(28)
                }
            }
        }
        .sheet(isPresented: $showSignIn) {
            SignInView { session in
                do { try api.accept(session); showSignIn = false }
                catch { self.error = error.localizedDescription }
            }
        }
        .alert("Sign-in could not finish", isPresented: Binding(get: { error != nil }, set: { if !$0 { error = nil } })) {
            Button("OK") { error = nil }
        } message: { Text(error ?? "") }
    }
}

private struct RecordView: View {
    @ObservedObject var api: OralyvoAPI
    @StateObject private var recorder = AudioRecorder()
    @Environment(\.scenePhase) private var scenePhase
    @State private var transcript = ""
    @State private var result = ""
    @State private var language = "French"
    @State private var resultType = "summary"
    @State private var busy = false
    @State private var message: String?
    @State private var draftID = UUID().uuidString
    @State private var resultID = UUID().uuidString
    @State private var confirmNew = false
    @State private var transcriptSaved = false
    @State private var resultSaved = false
    private let languages = ["English", "French", "Yoruba", "Spanish", "Arabic", "Portuguese", "Hindi", "Chinese"]
    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    VStack(spacing: 14) {
                        Image(systemName: recorder.isRecording ? "waveform" : "mic.circle.fill")
                            .font(.system(size: 58)).foregroundStyle(recorder.isRecording ? .red : .blue)
                        Text(String(format: "%02d:%02d", recorder.seconds / 60, recorder.seconds % 60))
                            .font(.system(.largeTitle, design: .monospaced))
                        Text(recorder.isRecording ? "Recording…" : recorder.file == nil ? "Ready when you are" : "Recording ready")
                            .font(.headline)
                        if recorder.isRecording {
                            Button("Stop recording", role: .destructive) { recorder.stop() }.buttonStyle(.borderedProminent)
                        } else if recorder.file != nil {
                            Button("Transcribe recording") { transcribe() }.buttonStyle(.borderedProminent).disabled(busy)
                            Button("New recording") { confirmNew = true }.disabled(busy)
                        } else {
                            Button("Start recording") { Task { await recorder.start() } }.buttonStyle(.borderedProminent).disabled(busy || recorder.isStarting)
                        }
                        Text("Up to 20 minutes per recording. Keep the app open while recording. Transcription starts after you stop.")
                            .font(.footnote).foregroundStyle(.secondary).multilineTextAlignment(.center)
                    }.frame(maxWidth: .infinity).padding().background(Color.blue.opacity(0.06)).cornerRadius(20)
                    if busy { ProgressView("Working…").frame(maxWidth: .infinity) }
                    if let message { Text(message).font(.callout).foregroundStyle(.secondary).textSelection(.enabled) }
                    if let error = recorder.error { Text(error).foregroundStyle(.red).font(.callout) }
                    if !transcript.isEmpty {
                        Text("Transcript").font(.title2.bold())
                        Text(transcript).textSelection(.enabled).frame(maxWidth: .infinity, alignment: .leading)
                        HStack {
                            ShareLink(item: transcript) { Label("Share", systemImage: "square.and.arrow.up") }
                            Spacer()
                            Button(transcriptSaved ? "Saved" : "Save transcript") { save(transcript, type: "transcript", id: draftID) }.disabled(busy || transcriptSaved)
                        }
                        Picker("Translate to", selection: $language) { ForEach(languages, id: \.self) { Text($0) } }.pickerStyle(.menu)
                        HStack {
                            Button("Translate") { process(mode: "translate", type: "translation") }
                            Spacer()
                            Button("AI notes") { process(mode: "summarize", type: "summary") }
                        }.buttonStyle(.bordered).disabled(busy)
                    }
                    if !result.isEmpty {
                        Divider()
                        Text(resultType == "translation" ? "Translation" : "AI notes").font(.title2.bold())
                        Text(result).textSelection(.enabled)
                        HStack {
                            ShareLink(item: result) { Label("Share", systemImage: "square.and.arrow.up") }
                            Spacer()
                            Button(resultSaved ? "Saved" : "Save result") { save(result, type: resultType, id: resultID) }.disabled(busy || resultSaved)
                        }
                    }
                }.padding()
            }
            .navigationTitle("Record")
            .confirmationDialog("Start a new recording? Save any text you want to keep first. The current recording and results will be cleared.", isPresented: $confirmNew, titleVisibility: .visible) {
                Button("Start new recording", role: .destructive) {
                    transcript = ""; result = ""; message = nil; recorder.error = nil
                    draftID = UUID().uuidString; transcriptSaved = false; resultSaved = false
                    Task { await recorder.start() }
                }
            }
        }
        .onChange(of: scenePhase) { phase in if phase != .active { recorder.stop() } }
        .onDisappear { recorder.stop() }
    }
    private func transcribe() {
        guard let file = recorder.file else { return }
        busy = true; message = nil
        Task {
            defer { busy = false }
            do {
                transcript = try await api.transcribe(file)
                draftID = UUID().uuidString; transcriptSaved = false
                result = ""; resultSaved = false
                message = "Transcript ready. Save it to keep it in your account history."
            } catch { message = error.localizedDescription + " Your recording is still available to retry." }
        }
    }
    private func process(mode: String, type: String) {
        busy = true; message = nil
        Task {
            defer { busy = false }
            do {
                result = try await api.process(transcript, mode: mode, language: language)
                resultType = type; resultID = UUID().uuidString; resultSaved = false
            } catch { message = error.localizedDescription }
        }
    }
    private func save(_ text: String, type: String, id: String) {
        busy = true; message = nil
        Task {
            defer { busy = false }
            do {
                try await api.save(text, type: type, id: id)
                if type == "transcript" { transcriptSaved = true } else { resultSaved = true }
                message = "Saved to your Oralyvo history."
            } catch { message = error.localizedDescription + " Your text is still here; you can retry saving or share a copy." }
        }
    }
}

private struct HistoryView: View {
    @ObservedObject var api: OralyvoAPI
    @State private var entries: [HistoryEntry] = []
    @State private var loading = false
    @State private var error: String?
    var body: some View {
        NavigationStack {
            List {
                if loading { ProgressView("Loading history…") }
                if let error {
                    Text(error).foregroundStyle(.red)
                    Button("Retry") { Task { await load() } }
                }
                if entries.isEmpty && !loading && error == nil {
                    Text("No saved transcripts yet. Save a transcript from Record to see it here.").foregroundStyle(.secondary)
                }
                ForEach(entries) { entry in
                    NavigationLink {
                        ScrollView {
                            VStack(alignment: .leading, spacing: 20) {
                                Text(entry.content).textSelection(.enabled)
                                ShareLink(item: entry.content) { Label("Share", systemImage: "square.and.arrow.up") }
                            }.frame(maxWidth: .infinity, alignment: .leading).padding()
                        }.navigationTitle(entry.type.capitalized).navigationBarTitleDisplayMode(.inline)
                    } label: {
                        VStack(alignment: .leading, spacing: 6) {
                            Text(entry.type.capitalized).font(.caption).foregroundStyle(.blue)
                            Text(entry.content).lineLimit(3)
                            Text(entry.created_at.prefix(10)).font(.caption).foregroundStyle(.secondary)
                        }.padding(.vertical, 4)
                    }
                }
            }.navigationTitle("History")
                .refreshable { await load() }
                .toolbar { Button { Task { await load() } } label: { Image(systemName: "arrow.clockwise") }.disabled(loading) }
                .task { await load() }
        }
    }
    private func load() async {
        guard !loading else { return }
        loading = true; error = nil
        defer { loading = false }
        do { entries = try await api.history() }
        catch { self.error = error.localizedDescription }
    }
}
