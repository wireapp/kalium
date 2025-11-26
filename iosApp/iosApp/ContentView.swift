/*
 * Wire
 * Copyright (C) 2024 Wire Swiss GmbH
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program. If not, see http://www.gnu.org/licenses/.
 */

import SwiftUI
import KaliumNetwork

// MARK: - App Flow State

enum AppFlowState {
    case serverConfig
    case login(ServerConfigDTO.Links)
    case loggedIn(LoginResult, ServerConfigDTO.Links)
}

// MARK: - Main Content View

struct ContentView: View {
    @StateObject private var viewModel = AppFlowViewModel()

    var body: some View {
        NavigationView {
            switch viewModel.flowState {
            case .serverConfig:
                ServerConfigView(viewModel: viewModel)
            case .login(let links):
                LoginView(viewModel: viewModel, serverConfigLinks: links)
            case .loggedIn(let result, let links):
                TokenResultView(viewModel: viewModel, loginResult: result, serverConfigLinks: links)
            }
        }
        .navigationViewStyle(.stack)
    }
}

// MARK: - Server Config View

struct ServerConfigView: View {
    @ObservedObject var viewModel: AppFlowViewModel

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                headerSection

                Divider()

                configInputSection

                if let links = viewModel.serverConfigLinks {
                    serverConfigDisplay(links)
                }

                if let error = viewModel.error {
                    errorSection(error)
                }
            }
            .padding()
        }
    }

    private var headerSection: some View {
        VStack(spacing: 8) {
            Image(systemName: "server.rack")
                .font(.system(size: 50))
                .foregroundColor(.blue)
            Text("Wire Server Config")
                .font(.title)
                .fontWeight(.bold)
            Text("Using KaliumNetwork Module")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }

    private var configInputSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Server Configuration URL")
                .font(.headline)

            TextField("https://example.com/config.json", text: $viewModel.configUrl)
                .textFieldStyle(.roundedBorder)
                .autocapitalization(.none)
                .disableAutocorrection(true)
                .keyboardType(.URL)

            Button(action: {
                Task {
                    await viewModel.fetchServerConfig()
                }
            }) {
                HStack {
                    if viewModel.isLoading {
                        ProgressView()
                            .progressViewStyle(CircularProgressViewStyle(tint: .white))
                    } else {
                        Image(systemName: "arrow.down.circle")
                    }
                    Text("Fetch Config")
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.blue)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
            .disabled(viewModel.isLoading || viewModel.configUrl.isEmpty)
        }
    }

    private func serverConfigDisplay(_ links: ServerConfigDTO.Links) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "checkmark.circle.fill")
                    .foregroundColor(.green)
                Text("Server Config Loaded")
                    .font(.headline)
            }

            Group {
                configRow(icon: "tag", label: "Title", value: links.title)
                configRow(icon: "network", label: "API Base", value: links.api)
                configRow(icon: "bolt.horizontal", label: "WebSocket", value: links.webSocket)
                configRow(icon: "person.circle", label: "Accounts", value: links.accounts)
                configRow(icon: "globe", label: "Website", value: links.website)
                configRow(icon: "person.3", label: "Teams", value: links.teams)
                configRow(icon: "building.2", label: "On Premises", value: links.isOnPremises ? "Yes" : "No")
            }

            if let proxy = links.apiProxy {
                VStack(alignment: .leading, spacing: 8) {
                    HStack {
                        Image(systemName: "network.badge.shield.half.filled")
                            .foregroundColor(.orange)
                        Text("Proxy Configuration")
                            .font(.headline)
                    }
                    configRow(icon: "server.rack", label: "Host", value: proxy.host)
                    configRow(icon: "number", label: "Port", value: "\(proxy.port)")
                    configRow(icon: "lock", label: "Auth Required", value: proxy.needsAuthentication ? "Yes" : "No")
                }
                .padding()
                .background(Color.orange.opacity(0.1))
                .cornerRadius(8)
            }

            Button(action: {
                viewModel.proceedToLogin()
            }) {
                HStack {
                    Image(systemName: "arrow.right.circle")
                    Text("Continue to Login")
                }
                .frame(maxWidth: .infinity)
                .padding()
                .background(Color.green)
                .foregroundColor(.white)
                .cornerRadius(10)
            }
        }
        .padding()
        .background(Color.green.opacity(0.1))
        .cornerRadius(12)
    }

    private func configRow(icon: String, label: String, value: String) -> some View {
        HStack(alignment: .top) {
            Image(systemName: icon)
                .frame(width: 20)
                .foregroundColor(.secondary)
            Text(label + ":")
                .fontWeight(.medium)
            Text(value)
                .foregroundColor(.secondary)
                .lineLimit(1)
                .truncationMode(.middle)
            Spacer()
        }
        .font(.caption)
    }

    private func errorSection(_ error: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.red)
                Text("Error")
                    .font(.headline)
            }
            Text(error)
                .foregroundColor(.red)
                .font(.caption)
        }
        .padding()
        .background(Color.red.opacity(0.1))
        .cornerRadius(8)
    }
}

// MARK: - Login View

struct LoginView: View {
    @ObservedObject var viewModel: AppFlowViewModel
    let serverConfigLinks: ServerConfigDTO.Links

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                headerSection

                Divider()

                serverInfoSection

                credentialsSection

                if serverConfigLinks.apiProxy?.needsAuthentication == true {
                    proxyCredentialsSection
                }

                loginButton

                if let error = viewModel.error {
                    errorSection(error, rawJson: viewModel.errorRawJson)
                }
            }
            .padding()
        }
        .navigationBarItems(leading: backButton)
    }

    private var backButton: some View {
        Button(action: {
            viewModel.goBackToConfig()
        }) {
            HStack {
                Image(systemName: "chevron.left")
                Text("Back")
            }
        }
    }

    private var headerSection: some View {
        VStack(spacing: 8) {
            Image(systemName: "person.badge.key")
                .font(.system(size: 50))
                .foregroundColor(.blue)
            Text("Login to \(serverConfigLinks.title)")
                .font(.title2)
                .fontWeight(.bold)
        }
    }

    private var serverInfoSection: some View {
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Image(systemName: "server.rack")
                    .foregroundColor(.blue)
                Text("Server: \(serverConfigLinks.api)")
                    .font(.caption)
                    .foregroundColor(.secondary)
                    .lineLimit(1)
                    .truncationMode(.middle)
            }
        }
        .padding()
        .background(Color.blue.opacity(0.1))
        .cornerRadius(8)
    }

    private var credentialsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            Text("Credentials")
                .font(.headline)

            VStack(alignment: .leading, spacing: 8) {
                Text("Email")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                TextField("email@example.com", text: $viewModel.email)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
                    .keyboardType(.emailAddress)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Password")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                SecureField("Password", text: $viewModel.password)
                    .textFieldStyle(.roundedBorder)
            }
        }
    }

    private var proxyCredentialsSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "network.badge.shield.half.filled")
                    .foregroundColor(.orange)
                Text("Proxy Authentication Required")
                    .font(.headline)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Proxy Username")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                TextField("Username", text: $viewModel.proxyUsername)
                    .textFieldStyle(.roundedBorder)
                    .autocapitalization(.none)
                    .disableAutocorrection(true)
            }

            VStack(alignment: .leading, spacing: 8) {
                Text("Proxy Password")
                    .font(.subheadline)
                    .foregroundColor(.secondary)
                SecureField("Proxy Password", text: $viewModel.proxyPassword)
                    .textFieldStyle(.roundedBorder)
            }
        }
        .padding()
        .background(Color.orange.opacity(0.1))
        .cornerRadius(8)
    }

    private var loginButton: some View {
        Button(action: {
            Task {
                await viewModel.login()
            }
        }) {
            HStack {
                if viewModel.isLoading {
                    ProgressView()
                        .progressViewStyle(CircularProgressViewStyle(tint: .white))
                } else {
                    Image(systemName: "arrow.right.circle")
                }
                Text("Login")
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(canLogin ? Color.blue : Color.gray)
            .foregroundColor(.white)
            .cornerRadius(10)
        }
        .disabled(!canLogin || viewModel.isLoading)
    }

    private var canLogin: Bool {
        !viewModel.email.isEmpty && !viewModel.password.isEmpty
    }

    private func errorSection(_ error: String, rawJson: String?) -> some View {
        VStack(alignment: .leading, spacing: 12) {
            HStack {
                Image(systemName: "exclamationmark.triangle.fill")
                    .foregroundColor(.red)
                Text("Login Error")
                    .font(.headline)
            }
            Text(error)
                .foregroundColor(.red)
                .font(.caption)

            if let rawJson = rawJson {
                VStack(alignment: .leading, spacing: 8) {
                    Text("Raw Error Response:")
                        .font(.caption)
                        .fontWeight(.medium)

                    ScrollView(.horizontal, showsIndicators: true) {
                        Text(rawJson)
                            .font(.system(.caption, design: .monospaced))
                            .foregroundColor(.red.opacity(0.8))
                            .padding(8)
                            .background(Color.red.opacity(0.05))
                            .cornerRadius(4)
                    }
                    .frame(maxHeight: 150)

                    Button(action: {
                        UIPasteboard.general.string = rawJson
                    }) {
                        HStack {
                            Image(systemName: "doc.on.doc")
                            Text("Copy Error JSON")
                        }
                        .font(.caption)
                        .padding(.horizontal, 12)
                        .padding(.vertical, 6)
                        .background(Color.red.opacity(0.1))
                        .cornerRadius(6)
                    }
                }
            }
        }
        .padding()
        .background(Color.red.opacity(0.1))
        .cornerRadius(8)
    }
}

// MARK: - Token Result View

struct TokenResultView: View {
    @ObservedObject var viewModel: AppFlowViewModel
    let loginResult: LoginResult
    let serverConfigLinks: ServerConfigDTO.Links

    var body: some View {
        ScrollView {
            VStack(spacing: 24) {
                successHeader

                Divider()

                userInfoSection

                tokenSection

                rawResponsesSection

                logoutButton
            }
            .padding()
        }
    }

    private var successHeader: some View {
        VStack(spacing: 8) {
            Image(systemName: "checkmark.circle.fill")
                .font(.system(size: 60))
                .foregroundColor(.green)
            Text("Login Successful!")
                .font(.title)
                .fontWeight(.bold)
            Text("Connected to \(serverConfigLinks.title)")
                .font(.subheadline)
                .foregroundColor(.secondary)
        }
    }

    private var userInfoSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(icon: "person.circle.fill", title: "User Information")

            infoRow(label: "Name", value: loginResult.selfUser.name)
            infoRow(label: "User ID", value: loginResult.session.userId.value)
            infoRow(label: "Domain", value: loginResult.session.userId.domain)

            if let email = loginResult.selfUser.email {
                infoRow(label: "Email", value: email)
            }
            if let handle = loginResult.selfUser.handle {
                infoRow(label: "Handle", value: "@\(handle)")
            }
        }
        .padding()
        .background(Color.blue.opacity(0.1))
        .cornerRadius(12)
    }

    private var tokenSection: some View {
        VStack(alignment: .leading, spacing: 12) {
            sectionHeader(icon: "key.fill", title: "Access Token")

            VStack(alignment: .leading, spacing: 8) {
                infoRow(label: "Token Type", value: loginResult.session.tokenType)

                Text("Access Token:")
                    .font(.caption)
                    .foregroundColor(.secondary)

                ScrollView(.horizontal, showsIndicators: true) {
                    Text(loginResult.session.accessToken)
                        .font(.system(.caption2, design: .monospaced))
                        .padding(8)
                        .background(Color.gray.opacity(0.1))
                        .cornerRadius(4)
                }
                .frame(maxHeight: 80)

                Button(action: {
                    UIPasteboard.general.string = loginResult.session.accessToken
                }) {
                    HStack {
                        Image(systemName: "doc.on.doc")
                        Text("Copy Token")
                    }
                    .font(.caption)
                    .padding(.horizontal, 12)
                    .padding(.vertical, 6)
                    .background(Color.blue.opacity(0.1))
                    .cornerRadius(6)
                }
            }
        }
        .padding()
        .background(Color.green.opacity(0.1))
        .cornerRadius(12)
    }

    private var rawResponsesSection: some View {
        VStack(alignment: .leading, spacing: 16) {
            sectionHeader(icon: "doc.text", title: "Raw API Responses")

            rawJsonSection(title: "Session Response", json: loginResult.rawSessionJson)
            rawJsonSection(title: "Self User Response", json: loginResult.rawSelfUserJson)
        }
        .padding()
        .background(Color.gray.opacity(0.1))
        .cornerRadius(12)
    }

    private func rawJsonSection(title: String, json: String) -> some View {
        VStack(alignment: .leading, spacing: 8) {
            Text(title)
                .font(.subheadline)
                .fontWeight(.medium)

            ScrollView {
                Text(json)
                    .font(.system(.caption2, design: .monospaced))
                    .padding(8)
                    .frame(maxWidth: .infinity, alignment: .leading)
            }
            .frame(maxHeight: 150)
            .background(Color.white)
            .cornerRadius(4)

            Button(action: {
                UIPasteboard.general.string = json
            }) {
                HStack {
                    Image(systemName: "doc.on.doc")
                    Text("Copy \(title)")
                }
                .font(.caption)
                .padding(.horizontal, 12)
                .padding(.vertical, 6)
                .background(Color.blue.opacity(0.1))
                .cornerRadius(6)
            }
        }
    }

    private var logoutButton: some View {
        Button(action: {
            viewModel.logout()
        }) {
            HStack {
                Image(systemName: "rectangle.portrait.and.arrow.right")
                Text("Logout")
            }
            .frame(maxWidth: .infinity)
            .padding()
            .background(Color.red)
            .foregroundColor(.white)
            .cornerRadius(10)
        }
    }

    private func sectionHeader(icon: String, title: String) -> some View {
        HStack {
            Image(systemName: icon)
                .foregroundColor(.blue)
            Text(title)
                .font(.headline)
        }
    }

    private func infoRow(label: String, value: String) -> some View {
        HStack(alignment: .top) {
            Text(label + ":")
                .font(.caption)
                .foregroundColor(.secondary)
                .frame(width: 80, alignment: .leading)
            Text(value)
                .font(.caption)
                .fontWeight(.medium)
            Spacer()
        }
    }
}

// MARK: - View Model

@MainActor
class AppFlowViewModel: ObservableObject {
    // Flow state
    @Published var flowState: AppFlowState = .serverConfig

    // Server config screen
    @Published var configUrl: String = ""
    @Published var serverConfigLinks: ServerConfigDTO.Links?

    // Login screen
    @Published var email: String = ""
    @Published var password: String = ""
    @Published var proxyUsername: String = ""
    @Published var proxyPassword: String = ""

    // Common state
    @Published var isLoading: Bool = false
    @Published var error: String?
    @Published var errorRawJson: String?

    private let networkService = NetworkService()

    // MARK: - Server Config

    func fetchServerConfig() async {
        isLoading = true
        error = nil
        errorRawJson = nil
        serverConfigLinks = nil

        do {
            let links = try await networkService.fetchServerConfig(configUrl: configUrl)
            serverConfigLinks = links
        } catch let networkError as NetworkError {
            self.error = networkError.localizedDescription
            self.errorRawJson = networkError.rawJson
        } catch {
            self.error = error.localizedDescription
        }

        isLoading = false
    }

    func proceedToLogin() {
        guard let links = serverConfigLinks else { return }
        error = nil
        errorRawJson = nil
        flowState = .login(links)
    }

    // MARK: - Login

    func login() async {
        guard case .login(let links) = flowState else { return }

        isLoading = true
        error = nil
        errorRawJson = nil

        // Configure proxy if needed
        if links.apiProxy?.needsAuthentication == true {
            networkService.configureProxy(links.apiProxy, credentials: (proxyUsername, proxyPassword))
        }

        do {
            let result = try await networkService.login(
                email: email,
                password: password,
                serverConfigLinks: links
            )
            flowState = .loggedIn(result, links)
        } catch let networkError as NetworkError {
            self.error = networkError.localizedDescription
            self.errorRawJson = networkError.rawJson
        } catch {
            self.error = error.localizedDescription
        }

        isLoading = false
    }

    // MARK: - Navigation

    func goBackToConfig() {
        error = nil
        errorRawJson = nil
        flowState = .serverConfig
    }

    func logout() {
        email = ""
        password = ""
        proxyUsername = ""
        proxyPassword = ""
        serverConfigLinks = nil
        error = nil
        errorRawJson = nil
        flowState = .serverConfig
    }
}

// MARK: - Preview

#Preview {
    ContentView()
}
