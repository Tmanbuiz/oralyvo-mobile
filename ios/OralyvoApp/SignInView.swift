import SwiftUI
import WebKit

// Only authentication uses web content. Recording, results and history are native.
// An ephemeral store prevents a previous account from signing itself back in.
struct SignInView: View {
    @Environment(\.dismiss) private var dismiss
    let onSession: (OralyvoSession) -> Void
    @State private var failure: String?
    @State private var loading = true
    @State private var attempt = UUID()
    var body: some View {
        NavigationStack {
            VStack(spacing: 0) {
                Text("Use your Oralyvo email and password. You can also create an account or reset your password below.")
                    .font(.footnote).foregroundStyle(.secondary).padding()
                if let failure {
                    Text(failure).font(.callout).padding()
                    Button("Retry") { self.failure = nil; loading = true; attempt = UUID() }.padding()
                } else {
                    ZStack {
                        AccountWebView(onSession: onSession, onError: { failure = $0 }, onLoaded: { loading = false }).id(attempt)
                        if loading { ProgressView("Opening sign-in…").padding().background(.regularMaterial).cornerRadius(12) }
                    }
                }
            }
            .navigationTitle("Sign in to Oralyvo")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .cancellationAction) { Button("Cancel") { dismiss() } } }
        }
    }
}

private struct AccountWebView: UIViewRepresentable {
    let onSession: (OralyvoSession) -> Void
    let onError: (String) -> Void
    let onLoaded: () -> Void
    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }
    func makeUIView(context: Context) -> WKWebView {
        let config = WKWebViewConfiguration()
        config.websiteDataStore = .nonPersistent()
        config.userContentController.add(context.coordinator, name: "oralyvoSession")
        // This script runs only in the trusted top-level origin. Capture the
        // existing session contract without collecting passwords or OAuth tokens.
        let script = """
        if (location.origin === 'https://oralyvo.app') {
          const deliver = value => {
            try {
              const s = JSON.parse(value || 'null');
              if (s && s.access_token && s.userId) {
                window.webkit.messageHandlers.oralyvoSession.postMessage(JSON.stringify({
                  access_token:s.access_token, refresh_token:s.refresh_token,
                  expires_at:s.expires_at, email:s.email, userId:s.userId
                }));
              }
            } catch (_) {}
          };
          const original = Storage.prototype.setItem;
          Storage.prototype.setItem = function(key, value) {
            original.call(this, key, value);
            if (this === localStorage && key === 'speech2text_pro_session') deliver(value);
          };
          deliver(localStorage.getItem('speech2text_pro_session'));
          document.addEventListener('DOMContentLoaded', () => {
            // Embedded OAuth is restricted by some providers. Email sign-in
            // and signup remain available, including for existing accounts
            // after setting a password through the recovery flow.
            document.querySelectorAll('[data-oauth-provider]').forEach(b => b.hidden = true);
          });
        }
        """
        config.userContentController.addUserScript(WKUserScript(source: script, injectionTime: .atDocumentStart, forMainFrameOnly: true))
        let view = WKWebView(frame: .zero, configuration: config)
        view.navigationDelegate = context.coordinator
        view.load(URLRequest(url: OralyvoAPI.origin.appendingPathComponent("login/")))
        return view
    }
    func updateUIView(_ view: WKWebView, context: Context) {}
    static func dismantleUIView(_ view: WKWebView, coordinator: Coordinator) {
        view.stopLoading()
        view.configuration.userContentController.removeScriptMessageHandler(forName: "oralyvoSession")
        view.navigationDelegate = nil
    }
    final class Coordinator: NSObject, WKNavigationDelegate, WKScriptMessageHandler {
        let parent: AccountWebView
        private var delivered = false
        init(parent: AccountWebView) { self.parent = parent }
        func userContentController(_ controller: WKUserContentController, didReceive message: WKScriptMessage) {
            guard !delivered, message.frameInfo.isMainFrame,
                  message.frameInfo.securityOrigin.protocol == "https",
                  message.frameInfo.securityOrigin.host == "oralyvo.app",
                  let raw = message.body as? String, let data = raw.data(using: .utf8),
                  let session = try? JSONDecoder().decode(OralyvoSession.self, from: data),
                  !session.access_token.isEmpty, let id = session.userId, UUID(uuidString: id) != nil else { return }
            delivered = true
            parent.onSession(session)
        }
        func webView(_ webView: WKWebView, didFinish navigation: WKNavigation!) { parent.onLoaded() }
        func webView(_ webView: WKWebView, didFailProvisionalNavigation navigation: WKNavigation!, withError error: Error) {
            if (error as NSError).code != NSURLErrorCancelled { parent.onError("Could not open sign-in. Check your connection and retry.") }
        }
        func webView(_ webView: WKWebView, didFail navigation: WKNavigation!, withError error: Error) {
            if (error as NSError).code != NSURLErrorCancelled { parent.onError("Sign-in could not load. Please retry.") }
        }
        func webView(_ webView: WKWebView, decidePolicyFor action: WKNavigationAction, decisionHandler: @escaping (WKNavigationActionPolicy) -> Void) {
            guard let url = action.request.url else { decisionHandler(.cancel); return }
            if action.targetFrame?.isMainFrame == false { decisionHandler(.allow); return }
            if url.scheme == "https", url.host == "oralyvo.app" { decisionHandler(.allow) }
            else {
                decisionHandler(.cancel)
                if ["https", "mailto"].contains(url.scheme ?? "") { UIApplication.shared.open(url) }
            }
        }
    }
}
