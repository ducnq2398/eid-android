import Foundation

public protocol CCCDCallback: AnyObject {
    func onMrzScanned(_ mrzData: MrzData)
    func onNfcProgress(_ status: ReadingStatus)
    func onSuccess(_ cccdData: CCCDData)
    func onError(_ error: CCCDError)
}

public extension CCCDCallback {
    func onMrzScanned(_ mrzData: MrzData) {}
    func onNfcProgress(_ status: ReadingStatus) {}
    func onSuccess(_ cccdData: CCCDData) {}
    func onError(_ error: CCCDError) {}
}
