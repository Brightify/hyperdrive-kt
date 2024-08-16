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

            SwiftUIComposeView(viewModel: viewModel)
        }
        .padding()
    }
}

#if canImport(UIKit)
struct SwiftUIComposeView: UIViewControllerRepresentable {
    let viewModel: ExampleViewModel

    func updateUIViewController(_ uiViewController: UIViewControllerType, context: Context) {

    }

    func makeUIViewController(context: Context) -> some UIViewController {
        return ComposeViewController(viewModel: viewModel)
    }
}
#elseif canImport(AppKit)
//struct SwiftUIComposeView: NSViewControllerRepresentable {
//    func makeNSViewController(context: Context) -> some NSViewController {
//
//    }
//}
#endif

#Preview {
    ContentView()
}
