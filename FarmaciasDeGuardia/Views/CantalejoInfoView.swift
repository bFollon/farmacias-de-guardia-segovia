import SwiftUI

struct CantalejoInfoView: View {
    @Environment(\.presentationMode) var presentationMode
    
    var body: some View {
        NavigationView {
            ScrollView {
                VStack(alignment: .leading, spacing: 20) {
                    // Header with icon
                    HStack {
                        Text("🏘️")
                            .font(.largeTitle)
                        VStack(alignment: .leading) {
                            Text("Información sobre Cantalejo")
                                .font(.title)
                                .fontWeight(.bold)
                            Text("Zona Básica de Salud")
                                .font(.subheadline)
                                .foregroundColor(.secondary)
                        }
                    }
                    .padding(.bottom)
                    
                    // Main explanation
                    VStack(alignment: .leading, spacing: 16) {
                        Text("Situación especial")
                            .font(.headline)
                            .foregroundColor(.orange)
                        
                        Text("El calendario oficial de guardias rurales de Segovia no incluye información completa sobre la rotación de farmacias en Cantalejo.")
                            .font(.body)
                        
                        Text("¿Qué significa esto?")
                            .font(.headline)
                        
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top) {
                                Text("•")
                                    .foregroundColor(.blue)
                                Text("Se muestran ambas farmacias de Cantalejo para todas las fechas")
                            }
                            
                            HStack(alignment: .top) {
                                Text("•")
                                    .foregroundColor(.blue)
                                Text("No conocemos el calendario específico de rotación")
                            }
                            
                            HStack(alignment: .top) {
                                Text("•")
                                    .foregroundColor(.blue)
                                Text("Los datos están basados en contacto directo con las farmacias")
                            }
                        }
                        .font(.body)
                        
                        Text("¿Qué debes hacer?")
                            .font(.headline)
                        
                        VStack(alignment: .leading, spacing: 8) {
                            HStack(alignment: .top) {
                                Text("1.")
                                    .foregroundColor(.blue)
                                    .fontWeight(.semibold)
                                Text("Llama siempre antes de desplazarte")
                            }
                            
                            HStack(alignment: .top) {
                                Text("2.")
                                    .foregroundColor(.blue)
                                    .fontWeight(.semibold)
                                Text("Confirma qué farmacia está de guardia ese día")
                            }
                            
                            HStack(alignment: .top) {
                                Text("3.")
                                    .foregroundColor(.blue)
                                    .fontWeight(.semibold)
                                Text("Si una no está disponible, prueba con la otra")
                            }
                        }
                        .font(.body)
                    }
                    
                    Divider()
                    
                    // Footer note
                    VStack(alignment: .leading, spacing: 8) {
                        Text("Nota importante")
                            .font(.footnote)
                            .fontWeight(.semibold)
                        
                        Text("Esta información ha sido obtenida mediante contacto directo con las farmacias de Cantalejo. Si tienes información actualizada sobre el calendario de rotación, por favor contacta con nosotros.")
                            .font(.footnote)
                            .foregroundColor(.secondary)
                        
                        // Contact link
                        Link("Contactar para actualizar información",
                             destination: URL(string: "mailto:alive.intake_0b@icloud.com?subject=Información%20Cantalejo%20Guardias&body=Tengo%20información%20actualizada%20sobre%20las%20guardias%20en%20Cantalejo:")!)
                            .font(.footnote)
                            .padding(.top, 4)
                    }
                }
                .padding()
            }
            .navigationTitle("")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar {
                ToolbarItem(placement: .navigationBarTrailing) {
                    Button("Cerrar") {
                        presentationMode.wrappedValue.dismiss()
                    }
                }
            }
        }
    }
}

struct CantalejoInfoView_Previews: PreviewProvider {
    static var previews: some View {
        CantalejoInfoView()
    }
}
