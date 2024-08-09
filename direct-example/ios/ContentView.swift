import SwiftUI
import ExampleKit

struct ContentView: View {
    @State
    var viewModel = ExampleViewModel()

    var body: some View {
        VStack {
            Text("Being said: \(viewModel.helloWorld)")

            TextField(text: $viewModel.helloWorld) {
                Text("Say something!")
            }

            Button("Reset") {
                viewModel.helloWorld = "Hello World!"
            }
        }
        .padding()
    }
}

#Preview {
    ContentView()
}
