import SwiftUI

struct ContentView: View {
    @EnvironmentObject var model: AppModel

    var body: some View {
        TabView {
            HomeView()
                .tabItem { Label("Home", systemImage: "house") }
            ReadView()
                .tabItem { Label("Read", systemImage: "book") }
            SpeakView()
                .tabItem { Label("Speak", systemImage: "mic") }
            MoreView()
                .tabItem { Label("More", systemImage: "ellipsis.circle") }
        }
        .tint(.blue)
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
}
