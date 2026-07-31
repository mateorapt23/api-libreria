package com.puce.service;

public class EditorialService {
  
}
/* 
Parte profile
1. Services/GithubService.swift — agregar este método a la clase existente

func getUser() async throws -> UserInfo {
    let response = await AF.request(
        "\(baseUrl)/user",
        method: .get,
        headers: headers
    )
    .validate(statusCode: 200..<300)
    .serializingDecodable(UserInfo.self)
    .response

    if let data = response.data,
       let json = String(data: data, encoding: .utf8) {
        print("***** Respuesta de usuario *****")
        print(json)
    }

    switch response.result {
    case .success(let user):
        return user
    case .failure(let error):
        print("Error al obtener usuario:")
        print(error.localizedDescription)
        throw error
    }
}

2. ViewControllers/ProfileViewController.swift — archivo nuevo

import Foundation

@MainActor
class ProfileViewController: ObservableObject {
    @Published var user: UserInfo?
    @Published var isLoading: Bool = false
    @Published var errorMsg: String?

    private let githubService: GithubService

    init(service: GithubService = .shared) {
        self.githubService = service
    }

    func loadUser() async {
        isLoading = true
        do {
            self.user = try await githubService.getUser()
            errorMsg = nil
        } catch {
            errorMsg = error.localizedDescription
        }
        isLoading = false
    }
}

3. Views/Profile.swift — reemplazar todo el contenido

import SwiftUI

struct Profile: View {
    @StateObject private var viewController = ProfileViewController()

    var body: some View {
        NavigationStack {
            Group {
                if viewController.isLoading {
                    ProgressView("Cargando perfil...")
                } else if let errorMsg = viewController.errorMsg {
                    Text(errorMsg)
                        .foregroundStyle(.red)
                        .padding()
                } else if let user = viewController.user {
                    VStack(alignment: .leading) {
                        Text(user.name ?? user.login)
                            .font(.title)

                        AsyncImage(url: URL(string: user.avatarUrl)) { image in
                            image
                                .resizable()
                                .scaledToFit()
                        } placeholder: {
                            Image(uiImage: .imageNotFound)
                                .resizable()
                                .scaledToFit()
                        }
                        .frame(width: 120, height: 120)
                        .clipShape(Circle())

                        Text(user.login)
                            .font(.headline)
                            .padding(.top)

                        if let bio = user.bio {
                            Text(bio)
                                .font(.caption)
                                .padding(.top)
                        }
                    }
                    .padding()
                }
            }
            .navigationTitle("Perfil")
        }
        .onAppear {
            Task {
                await viewController.loadUser()
            }
        }
    }
}

#Preview {
    Profile()
}



Parte patch
1. Services/GithubService.swift — agregar este método

func updateRepository(owner: String, repoName: String, name: String, description: String) async throws -> Repository {
    let response = await AF.request(
        "\(baseUrl)/repos/\(owner)/\(repoName)",
        method: .patch,
        parameters: [
            "name": name,
            "description": description
        ],
        encoding: JSONEncoding.default,
        headers: headers
    )
    .validate(statusCode: 200..<300)
    .serializingDecodable(Repository.self)
    .response

    if let data = response.data,
       let json = String(data: data, encoding: .utf8) {
        print("***** Respuesta al actualizar repositorio *****")
        print(json)
    }

    switch response.result {
    case .success(let repository):
        return repository
    case .failure(let error):
        print("Error al actualizar repositorio:")
        print(error.localizedDescription)
        throw error
    }
}


2. ViewControllers/RepoListViewController.swift — agregar dentro de la clase

@Published var repositoryToEdit: Repository?

func update(_ repository: Repository, name: String, description: String) async {
    do {
        let updated = try await githubService.updateRepository(
            owner: repository.owner.login,
            repoName: repository.name,
            name: name,
            description: description
        )
        if let index = repositories.firstIndex(where: { $0.id == repository.id }) {
            repositories[index] = updated
        }
        errorMsg = nil
    } catch {
        errorMsg = error.localizedDescription
    }
    repositoryToEdit = nil
}

3. Views/EditRepoView.swift — archivo nuevo

import SwiftUI

struct EditRepoView: View {
    let repository: Repository
    @ObservedObject var viewController: RepoListViewController

    @State private var name: String
    @State private var description: String

    init(repository: Repository, viewController: RepoListViewController) {
        self.repository = repository
        self.viewController = viewController
        _name = State(initialValue: repository.name)
        _description = State(initialValue: repository.description ?? "")
    }

    var body: some View {
        NavigationStack {
            VStack {
                TextField("Nombre de repositorio", text: $name)
                    .textFieldStyle(.roundedBorder)
                    .padding(.vertical)

                TextField("Descripción de repositorio", text: $description, axis: .vertical)
                    .textFieldStyle(.roundedBorder)
                    .lineLimit(6...10)
                    .padding(.vertical)

                Spacer()
            }
            .padding()
            .navigationTitle("Editar repositorio")
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button("Cancelar") { viewController.repositoryToEdit = nil }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button("Guardar") {
                        Task { await viewController.update(repository, name: name, description: description) }
                    }
                }
            }
        }
    }
}


4. Views/RepoList.swift — modificar el List y agregar el .sheet

import SwiftUI

struct RepoList: View {
    @StateObject private var viewController = RepoListViewController()
    
    var body: some View {
        NavigationStack {
            Group {
                if viewController.isLoading {
                    ProgressView("Cargando Repositorios...")
                } else if let errorMsg = viewController.errorMsg {
                    Text(errorMsg)
                        .foregroundStyle(.red)
                        .padding()
                } else {
                    List(viewController.repositories) { repo in
                        RepoItem(repository: repo)
                            .swipeActions(edge: .trailing) {
                                Button {
                                    viewController.repositoryToEdit = repo
                                } label: {
                                    Label("Editar", systemImage: "pencil")
                                }
                                .tint(.orange)
                            }
                    }
                }
            }
            .navigationTitle("Repositorios")
            .sheet(item: $viewController.repositoryToEdit) { repo in
                EditRepoView(repository: repo, viewController: viewController)
            }
        }
        .onAppear {
            Task {
                await viewController.loadRepositories()
            }
        }
    }
}

#Preview {
    RepoList()
}




Parte delete
1. Services/GithubService.swift — agregar este método

func deleteRepository(owner: String, repoName: String) async throws {
    let response = await AF.request(
        "\(baseUrl)/repos/\(owner)/\(repoName)",
        method: .delete,
        headers: headers
    )
    .validate(statusCode: 200..<300)
    .serializingData()
    .response

    if case .failure(let error) = response.result {
        print("Error al eliminar repositorio:")
        print(error.localizedDescription)
        throw error
    }
}



2. ViewControllers/RepoListViewController.swift — agregar dentro de la clase

@Published var repositoryToDelete: Repository?

func delete(_ repository: Repository) async {
    do {
        try await githubService.deleteRepository(owner: repository.owner.login, repoName: repository.name)
        repositories.removeAll { $0.id == repository.id }
        errorMsg = nil
    } catch {
        errorMsg = error.localizedDescription
    }
    repositoryToDelete = nil
}



3. Views/RepoList.swift — archivo completo con el botón de eliminar y la alerta de confirmación (con patch)

//  RepoList.swift
//  GithubClient
//
//  Created by Usuario invitado on 10/7/26.
//

import SwiftUI

struct RepoList: View {
    @StateObject private var viewController = RepoListViewController()
    
    var body: some View {
        NavigationStack {
            Group {
                if viewController.isLoading {
                    ProgressView("Cargando Repositorios...")
                } else if let errorMsg = viewController.errorMsg {
                    Text(errorMsg)
                        .foregroundStyle(.red)
                        .padding()
                } else {
                    List(viewController.repositories) { repo in
                        RepoItem(repository: repo)
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    viewController.repositoryToDelete = repo
                                } label: {
                                    Label("Eliminar", systemImage: "trash")
                                }

                                Button {
                                    viewController.repositoryToEdit = repo
                                } label: {
                                    Label("Editar", systemImage: "pencil")
                                }
                                .tint(.orange)
                            }
                    }
                }
            }
            .navigationTitle("Repositorios")
            .sheet(item: $viewController.repositoryToEdit) { repo in
                EditRepoView(repository: repo, viewController: viewController)
            }
            .alert(
                "¿Eliminar repositorio?",
                isPresented: Binding(
                    get: { viewController.repositoryToDelete != nil },
                    set: { if !$0 { viewController.repositoryToDelete = nil } }
                )
            ) {
                Button("Cancelar", role: .cancel) {
                    viewController.repositoryToDelete = nil
                }
                Button("Eliminar", role: .destructive) {
                    if let repo = viewController.repositoryToDelete {
                        Task {
                            await viewController.delete(repo)
                        }
                    }
                }
            } message: {
                Text("Esta acción no se puede deshacer.")
            }
        }
        .onAppear {
            Task {
                await viewController.loadRepositories()
            }
        }
    }
}

#Preview {
    RepoList()
}



4. Views/RepoList.swift — archivo completo con el botón de eliminar y la alerta de confirmación (sin patch)
import SwiftUI

struct RepoList: View {
    @StateObject private var viewController = RepoListViewController()
    
    var body: some View {
        NavigationStack {
            Group {
                if viewController.isLoading {
                    ProgressView("Cargando Repositorios...")
                } else if let errorMsg = viewController.errorMsg {
                    Text(errorMsg)
                        .foregroundStyle(.red)
                        .padding()
                } else {
                    List(viewController.repositories) { repo in
                        RepoItem(repository: repo)
                            .swipeActions(edge: .trailing) {
                                Button(role: .destructive) {
                                    viewController.repositoryToDelete = repo
                                } label: {
                                    Label("Eliminar", systemImage: "trash")
                                }
                            }
                    }
                }
            }
            .navigationTitle("Repositorios")
            .alert(
                "¿Eliminar repositorio?",
                isPresented: Binding(
                    get: { viewController.repositoryToDelete != nil },
                    set: { if !$0 { viewController.repositoryToDelete = nil } }
                )
            ) {
                Button("Cancelar", role: .cancel) {
                    viewController.repositoryToDelete = nil
                }
                Button("Eliminar", role: .destructive) {
                    if let repo = viewController.repositoryToDelete {
                        Task {
                            await viewController.delete(repo)
                        }
                    }
                }
            } message: {
                Text("Esta acción no se puede deshacer.")
            }
        }
        .onAppear {
            Task {
                await viewController.loadRepositories()
            }
        }
    }
}

#Preview {
    RepoList()
}
/* 