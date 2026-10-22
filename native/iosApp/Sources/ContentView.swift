import SwiftUI

struct ContentView: View {
    @EnvironmentObject var model: AppModel
    @Environment(\.scenePhase) private var scenePhase
    @SwiftUI.State private var tab = 0
    @SwiftUI.State private var speakMode = 0

    var body: some View {
        Group {
            if model.onboarded {
                TabView(selection: $tab) {
                    HomeView(tab: $tab, speakMode: $speakMode)
                        .tag(0)
                        .tabItem { Label("Home", systemImage: "house") }
                    ReadView()
                        .tag(1)
                        .tabItem { Label("Read", systemImage: "book") }
                    ListenView()
                        .tag(2)
                        .tabItem { Label("Listen", systemImage: "headphones") }
                    SpeakView(mode: $speakMode)
                        .tag(3)
                        .tabItem { Label("Speak", systemImage: "mic") }
                    MoreView()
                        .tag(4)
                        .tabItem { Label("More", systemImage: "ellipsis.circle") }
                }
                .tint(.blue)
                .onAppear(perform: restoreTab)
                .onChange(of: tab) { _, value in model.saveLastTab(Self.name(value)) }
                .task {
                    while !Task.isCancelled {
                        try? await Task.sleep(nanoseconds: 60_000_000_000)
                        model.rolloverDay()
                    }
                }
                .onChange(of: scenePhase) { _, phase in
                    if phase == .active { model.rolloverDay() }
                }
            } else {
                OnboardingView()
            }
        }
        .overlay {
            if let error = model.error {
                VStack {
                    Text("Pluton couldn't load")
                        .font(.headline)
                    Text(error)
                        .foregroundStyle(.red)
                    Text("Build the data with `uv run data/fr/build.py`, then rebuild the app.")
                        .font(.footnote)
                        .foregroundStyle(.secondary)
                }
                .padding()
                .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 16))
                .padding()
            }
        }
    }

    private func restoreTab() {
        switch model.lastTab() {
        case "Read": tab = 1
        case "Listen": tab = 2
        case "Speak": tab = 3
        case "More": tab = 4
        default: tab = 0
        }
    }

    private static func name(_ tab: Int) -> String {
        switch tab {
        case 1: return "Read"
        case 2: return "Listen"
        case 3: return "Speak"
        case 4: return "More"
        default: return "Home"
        }
    }
}
