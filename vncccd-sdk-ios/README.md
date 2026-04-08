# VNCCCD iOS SDK (Scaffold)

SDK iOS duoc xay dung dua tren API cua `vncccd-sdk` Android de giu cung mo hinh su dung:

- `CCCDConfig`
- `CCCDReader.startFullFlow / startMrzScan / startNfcRead`
- `CCCDCallback`
- `MrzData`, `CCCDData`, `PersonalInfo`, `ReadingStatus`, `CCCDError`

## Cai dat

Them local package vao Xcode:

1. `File` -> `Add Packages...`
2. Chon duong dan den thu muc `vncccd-sdk-ios`

## Su dung nhanh

```swift
import VNCCCDSDK

final class HostHandler: CCCDCallback {
    func onMrzScanned(_ mrzData: MrzData) {
        print("MRZ: \(mrzData.fullDocumentNumber)")
    }

    func onNfcProgress(_ status: ReadingStatus) {
        print(status.description)
    }

    func onSuccess(_ cccdData: CCCDData) {
        print("Done: \(cccdData)")
    }

    func onError(_ error: CCCDError) {
        print(error.message)
    }
}

let reader = CCCDReader(
    mrzScanner: VisionMrzScannerService(),
    nfcReader: CoreNfcCardReaderService()
)
let callback = HostHandler()

reader.startFullFlow(config: .defaultConfig, callback: callback)
```

## Trang thai hien tai

- Da port API va parser MRZ tu Android sang Swift.
- Da implement camera OCR thuc te bang `AVCaptureSession + Vision`.
- Da implement NFC read thuc te bang `NFCPassportReader` (BAC/PACE/Secure Messaging).
- Da map du lieu chip ve `CCCDData` va parse thong tin ca nhan (DG13 neu co, fallback metadata).

## Yeu cau host app (quan trong)

1. `Info.plist` can co:
   - `NSCameraUsageDescription`
   - `NFCReaderUsageDescription`
2. Entitlements can bat:
   - `Near Field Communication Tag Reading`
3. iOS deployment target: `iOS 15+`.

## Huong trien khai tiep

1. Bo sung test integration voi the that de tinh chinh mapping field cho tung loai CCCD.
2. Mo rong parser DG13 theo dump bytes that de tang do day du cua `PersonalInfo`.
