#if canImport(Observation)
import Foundation

@available(iOS 17.0, macOS 14.0, *)
@objc(ObservationRegistrarBridge)
@dynamicMemberLookup
class ObservationRegistrarBridge: NSObject, Observable {

    let registrar = ObservationRegistrar()

    @objc(accessedProperty:)
    func accessed(property: String) {
        registrar.access(self, keyPath: \.[dynamicMember: property])
    }

    @objc(willSetProperty:)
    func willSet(property: String) {
        registrar.willSet(self, keyPath: \.[dynamicMember: property])
    }

    @objc(didSetProperty:)
    func didSet(property: String) {
        registrar.didSet(self, keyPath: \.[dynamicMember: property])
    }

    subscript(dynamicMember member: String) -> Never {
        fatalError("Not supposed to be called")
    }
}
#endif
