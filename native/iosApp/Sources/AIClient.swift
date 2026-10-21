import Foundation
import Security

struct AIConfig: Codable {
    var provider = "anthropic"
    var model = "claude-opus-5-5"
    var base = ""
}

struct ChatTurn: Codable {
    let role: String
    let content: String
}

enum AIError: LocalizedError {
    case message(String)
    var errorDescription: String? {
        switch self { case .message(let text): return text }
    }
}

/// Bring-your-own-key AI over raw HTTPS: the official Anthropic SDK is JVM/JS/Python only, and Swift has no
/// first-party one. The key lives in the Keychain (never in UserDefaults or a backup-visible file).
enum AIClient {
    private static let service = "io.github.vykosmolt.pluton.ai"
    private static let configKey = "ai-config"

    struct Provider: Identifiable {
        let id: String
        let name: String
        let base: String
        let model: String
    }

    static let providers: [Provider] = [
        Provider(id: "anthropic", name: "Claude (Anthropic)", base: "https://api.anthropic.com", model: "claude-opus-5-5"),
        Provider(id: "openai", name: "OpenAI", base: "https://api.openai.com/v1", model: ""),
        Provider(id: "deepseek", name: "DeepSeek", base: "https://api.deepseek.com", model: ""),
        Provider(id: "openrouter", name: "OpenRouter", base: "https://openrouter.ai/api/v1", model: ""),
        Provider(id: "ollama", name: "Ollama (local, free)", base: "http://localhost:11434/v1", model: ""),
        Provider(id: "custom", name: "Other OpenAI-compatible server", base: "", model: ""),
    ]

    static func config() -> AIConfig {
        guard let data = UserDefaults.standard.data(forKey: configKey),
              let value = try? JSONDecoder().decode(AIConfig.self, from: data) else { return AIConfig() }
        return value
    }

    static func save(config: AIConfig) {
        if let data = try? JSONEncoder().encode(config) {
            UserDefaults.standard.set(data, forKey: configKey)
        }
    }

    static var key: String {
        get { Keychain.load(service: service) ?? "" }
        set { Keychain.save(service: service, value: newValue) }
    }

    static func ready() -> Bool {
        let config = config()
        guard !config.model.isEmpty else { return false }
        if config.provider == "custom" || config.provider == "ollama" { return !config.base.isEmpty || config.provider == "ollama" }
        return !key.isEmpty
    }

    static func chat(system: String, turns: [ChatTurn], effort: String = "low") async throws -> String {
        let config = config()
        let provider = providers.first { $0.id == config.provider }
        let base = (config.base.isEmpty ? provider?.base ?? "" : config.base).trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard !base.isEmpty else { throw AIError.message("Set the server URL in Settings first.") }
        if config.provider == "anthropic" {
            return try await anthropic(base: base, config: config, system: system, turns: turns, effort: effort)
        }
        return try await openAI(base: base, config: config, system: system, turns: turns)
    }

    private static func anthropic(base: String, config: AIConfig, system: String, turns: [ChatTurn], effort: String) async throws -> String {
        var request = URLRequest(url: URL(string: base + "/v1/messages")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "content-type")
        request.setValue(key, forHTTPHeaderField: "x-api-key")
        request.setValue("2023-06-01", forHTTPHeaderField: "anthropic-version")
        let body: [String: Any] = [
            "model": config.model,
            "max_tokens": 1024,
            "system": system,
            "messages": turns.map { ["role": $0.role, "content": $0.content] },
            "output_config": ["effort": effort],
        ]
        request.httpBody = try JSONSerialization.data(withJSONObject: body)
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        if status < 200 || status >= 300 {
            throw AIError.message(errorMessage(status: status, json: json))
        }
        if (json?["stop_reason"] as? String) == "refusal" {
            throw AIError.message("The model declined to answer that one.")
        }
        let blocks = json?["content"] as? [[String: Any]] ?? []
        let text = blocks.compactMap { $0["type"] as? String == "text" ? $0["text"] as? String : nil }.joined()
        guard !text.isEmpty else { throw AIError.message("The model returned an empty reply. Try again.") }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func openAI(base: String, config: AIConfig, system: String, turns: [ChatTurn]) async throws -> String {
        var request = URLRequest(url: URL(string: base + "/chat/completions")!)
        request.httpMethod = "POST"
        request.setValue("application/json", forHTTPHeaderField: "content-type")
        if !key.isEmpty { request.setValue("Bearer \(key)", forHTTPHeaderField: "authorization") }
        let messages = [["role": "system", "content": system]] + turns.map { ["role": $0.role, "content": $0.content] }
        request.httpBody = try JSONSerialization.data(withJSONObject: [
            "model": config.model,
            "messages": messages,
        ])
        let (data, response) = try await URLSession.shared.data(for: request)
        let status = (response as? HTTPURLResponse)?.statusCode ?? 0
        let json = (try? JSONSerialization.jsonObject(with: data)) as? [String: Any]
        if status < 200 || status >= 300 {
            throw AIError.message(errorMessage(status: status, json: json))
        }
        let choices = json?["choices"] as? [[String: Any]] ?? []
        let message = choices.first?["message"] as? [String: Any]
        let text = message?["content"] as? String ?? ""
        guard !text.isEmpty else { throw AIError.message("The model returned an empty reply. Try again.") }
        return text.trimmingCharacters(in: .whitespacesAndNewlines)
    }

    private static func errorMessage(status: Int, json: [String: Any]?) -> String {
        switch status {
        case 401, 403: return "The provider rejected the API key."
        case 404: return "That model was not found. Pick another in Settings."
        case 429: return "Rate limited or out of credit. Try again in a minute."
        default:
            if let error = json?["error"] as? [String: Any], let text = error["message"] as? String { return text }
            if let text = json?["message"] as? String { return text }
            return "The AI request failed (HTTP \(status))."
        }
    }
}

private enum Keychain {
    static func save(service: String, value: String) {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
        ]
        SecItemDelete(query as CFDictionary)
        guard !value.isEmpty, let data = value.data(using: .utf8) else { return }
        var item = query
        item[kSecValueData as String] = data
        SecItemAdd(item as CFDictionary, nil)
    }

    static func load(service: String) -> String? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne,
        ]
        var result: CFTypeRef?
        guard SecItemCopyMatching(query as CFDictionary, &result) == errSecSuccess,
              let data = result as? Data else { return nil }
        return String(data: data, encoding: .utf8)
    }
}
