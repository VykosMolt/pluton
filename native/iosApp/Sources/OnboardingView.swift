import SwiftUI

struct OnboardingView: View {
    @EnvironmentObject var model: AppModel
    @SwiftUI.State private var minutes = 10

    var body: some View {
        ScrollView {
            VStack(alignment: .leading, spacing: 18) {
                Text("Bienvenue").font(.system(size: 38, weight: .black))
                Text("Pluton is a black cat from Poe's story, now your French guide. Each day mixes four things: a short review, something to read, something to say, and something to write.")
                    .foregroundStyle(.secondary)
                Text("How long can you practise on a normal day?")
                    .font(.title3.bold())
                HStack(spacing: 8) {
                    ForEach([5, 10, 20, 30], id: \.self) { value in
                        Button {
                            minutes = value
                        } label: {
                            Text("\(value) min")
                                .fontWeight(minutes == value ? .bold : .regular)
                        }
                        .buttonStyle(.bordered)
                        .tint(minutes == value ? .blue : .gray)
                    }
                }
                Text("You can change the pace later in Settings.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
                Button {
                    model.saveOnboarding(minutes: minutes)
                } label: {
                    Text("Start with \(minutes) minutes")
                        .font(.headline)
                        .frame(maxWidth: .infinity)
                        .padding(.vertical, 6)
                }
                .buttonStyle(.borderedProminent)
                Text("No account, no server. Your words, texts and corrections stay on this device.")
                    .font(.footnote)
                    .foregroundStyle(.secondary)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(24)
        }
    }
}
