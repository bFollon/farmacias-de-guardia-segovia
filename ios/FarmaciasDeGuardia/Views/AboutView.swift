import SwiftUI

struct AboutView: View {
    @Environment(\.dismiss) private var dismiss
    @Environment(\.openURL) private var openURL
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 24) {
                    // Header
                    VStack(spacing: 12) {
                        Image("SplashLogo")
                            .resizable()
                            .aspectRatio(contentMode: .fit)
                            .frame(height: 80)
                        
                        Text("Farmacias de Guardia Segovia")
                            .font(.title2)
                            .fontWeight(.bold)
                            .multilineTextAlignment(.center)
                    }
                    .frame(maxWidth: .infinity)
                    .padding(.top)
                    
                    // App Description
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Acerca de la aplicación")
                            .font(.headline)
                            .foregroundColor(.primary)
                        
                        Text("Esta aplicación le permite consultar las farmacias de guardia en la provincia de Segovia de forma rápida y sencilla.")
                            .font(.body)
                            .foregroundColor(.secondary)
                        
                        Text("La app es completamente gratuita, sin publicidad y así se va a quedar. Ha sido desarrollada como un proyecto personal para ayudar a la comunidad.")
                            .font(.body)
                            .foregroundColor(.secondary)
                    }
                    
                    Divider()
                    
                    // Support Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Apoya el proyecto")
                            .font(.headline)
                            .foregroundColor(.primary)
                        
                        Text("Si la aplicación le resulta útil y quiere apoyar su desarrollo, puede invitarme a un café:")
                            .font(.body)
                            .foregroundColor(.secondary)
                        
                        Button(action: {
                            openURL(URL(string: "https://ko-fi.com/bfollon")!)
                        }) {
                            HStack {
                                Image(systemName: "cup.and.saucer.fill")
                                    .foregroundColor(.white)
                                Text("Cómprame un Ko-fi ☕")
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(Color.blue)
                            .cornerRadius(25)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    
                    Divider()
                    
                    // Source Code Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Código fuente")
                            .font(.headline)
                            .foregroundColor(.primary)
                        
                        Text("El proyecto es de código abierto y está disponible en GitHub:")
                            .font(.body)
                            .foregroundColor(.secondary)
                        
                        Button(action: {
                            openURL(URL(string: "https://github.com/bFollon/farmacias-de-guardia-segovia")!)
                        }) {
                            HStack {
                                Image(systemName: "chevron.left.forwardslash.chevron.right")
                                    .foregroundColor(.white)
                                Text("Ver en GitHub")
                                    .fontWeight(.medium)
                                    .foregroundColor(.white)
                            }
                            .padding(.horizontal, 20)
                            .padding(.vertical, 12)
                            .background(Color.black)
                            .cornerRadius(25)
                        }
                        .buttonStyle(PlainButtonStyle())
                    }
                    
                    Divider()
                    
                    // Contact & Feedback Section
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Contacto y sugerencias")
                            .font(.headline)
                            .foregroundColor(.primary)
                        
                        Text("¿Has encontrado algún error o tienes ideas para mejorar la app?")
                            .font(.body)
                            .foregroundColor(.secondary)
                        
                        VStack(spacing: 12) {
                            // Error reporting button
                            if let errorURL = AppConfig.EmailLinks.errorReport() {
                                Link(destination: errorURL) {
                                    HStack {
                                        Image(systemName: "exclamationmark.triangle")
                                            .foregroundColor(.orange)
                                        Text("Reportar error")
                                            .foregroundColor(.primary)
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .foregroundColor(.secondary)
                                            .font(.caption)
                                    }
                                    .padding()
                                    .background(Color(.secondarySystemGroupedBackground))
                                    .cornerRadius(8)
                                }
                            }
                            
                            // Suggestions button
                            if let feedbackURL = AppConfig.EmailLinks.feedback() {
                                Link(destination: feedbackURL) {
                                    HStack {
                                        Image(systemName: "lightbulb")
                                            .foregroundColor(.blue)
                                        Text("Enviar sugerencia")
                                            .foregroundColor(.primary)
                                        Spacer()
                                        Image(systemName: "chevron.right")
                                            .foregroundColor(.secondary)
                                            .font(.caption)
                                    }
                                    .padding()
                                    .background(Color(.secondarySystemGroupedBackground))
                                    .cornerRadius(8)
                                }
                            }
                        }
                    }
                    
                    Divider()
                    
                    // Legal Notice
                    VStack(alignment: .leading, spacing: 12) {
                        Text("Aviso legal")
                            .font(.headline)
                            .foregroundColor(.primary)
                        
                        Text("Los horarios mostrados son informativos. Se recomienda confirmar la información antes de desplazarse a la farmacia.")
                            .font(.caption)
                            .foregroundColor(.secondary)
                            .italic()
                        
                        Button(action: {
                            if let url = URL(string: "https://github.com/bFollon") {
                                openURL(url)
                            }
                        }) {
                            Text("Desarrollado por @bFollon")
                                .font(.caption)
                                .foregroundColor(.blue)
                                .underline()
                        }
                    }
                    
                    Spacer(minLength: 50)
                }
                .padding(.horizontal, 20)
            }
            .navigationTitle("Acerca de")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") {
                        dismiss()
                    }
                }
            }
        }
    }
}

#Preview {
    AboutView()
}
