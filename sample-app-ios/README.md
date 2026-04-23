# VNCCCD SDK iOS - Sample App

Ứng dụng mẫu minh hoạ cách tích hợp **VNCCCDSDK** vào iOS app.

## Tính năng

- **Quét đầy đủ (Full Flow)**: MRZ scan bằng camera → NFC read chip → hiển thị kết quả
- **Chỉ quét MRZ**: Quét nhanh 3 dòng MRZ trên mặt sau CCCD
- **Hiển thị kết quả**: Ảnh chân dung, thông tin cá nhân, dữ liệu MRZ, thông tin kỹ thuật
- **Chia sẻ kết quả**: Export thông tin đã đọc

## Cấu trúc

```
sample-app-ios/
├── Package.swift                    # SPM config (alternative)
├── VNCCCDSample.xcodeproj/          # Xcode project
│   └── project.pbxproj
├── VNCCCDSample/
│   ├── VNCCCDSampleApp.swift        # App entry point
│   ├── Theme.swift                  # Design system (colors, spacing, etc.)
│   ├── ScanViewModel.swift          # ViewModel – quản lý logic SDK
│   ├── HomeView.swift               # Màn hình chính
│   ├── ResultView.swift             # Màn hình kết quả full scan
│   ├── MrzResultView.swift          # Màn hình kết quả MRZ only
│   ├── AboutView.swift              # Giới thiệu SDK
│   ├── Info.plist                   # NFC + Camera permissions
│   ├── VNCCCDSample.entitlements    # NFC entitlements
│   └── Assets.xcassets/             # App icon, accent color
└── README.md
```

## Cách chạy

### Cách 1: Mở bằng Xcode (khuyến nghị)

1. Mở `VNCCCDSample.xcodeproj` trong Xcode
2. Xcode sẽ tự resolve VNCCCDSDK package từ `../vncccd-sdk-ios`
3. Chọn Team signing (nếu cần)
4. Build & Run trên thiết bị thật (NFC cần device thật)

### Cách 2: Thêm package thủ công

Nếu Xcode chưa resolve package tự động:

1. Mở project
2. `File` → `Add Package Dependencies...`
3. Click `Add Local...` → chọn thư mục `vncccd-sdk-ios`
4. Chọn product `VNCCCDSDK` → Add

## Yêu cầu

| Yêu cầu | Giá trị |
|----------|---------|
| iOS | 15.0+ |
| Xcode | 15.0+ |
| Swift | 5.9+ |
| Device | iPhone 7+ (có NFC) |

## Cấu hình quan trọng

### Info.plist

```xml
<key>NSCameraUsageDescription</key>
<string>Ứng dụng cần truy cập camera để quét mã MRZ</string>

<key>NFCReaderUsageDescription</key>
<string>Ứng dụng cần sử dụng NFC để đọc chip CCCD</string>

<key>com.apple.developer.nfc.readersession.iso7816.select-identifiers</key>
<array>
    <string>A0000002471001</string>
    <string>A0000002472001</string>
</array>
```

### Entitlements

```xml
<key>com.apple.developer.nfc.readersession.formats</key>
<array>
    <string>TAG</string>
</array>
```

## Screenshots

| Home | Result | MRZ Result |
|------|--------|------------|
| Màn hình chính với NFC status, nút scan | Kết quả đọc thẻ đầy đủ | Kết quả quét MRZ |

## Cách sử dụng SDK

### Quick Start

```swift
import VNCCCDSDK

// 1. Tạo reader
let reader = CCCDReader(
    mrzScanner: VisionMrzScannerService(),
    nfcReader: CoreNfcCardReaderService()
)

// 2. Implement callback
class MyCallback: CCCDCallback {
    func onMrzScanned(_ mrzData: MrzData) {
        print("Số CCCD: \(mrzData.fullDocumentNumber)")
    }

    func onNfcProgress(_ status: ReadingStatus) {
        print(status.description)
    }

    func onSuccess(_ cccdData: CCCDData) {
        let info = cccdData.personalInfo
        print("Họ tên: \(info?.fullName ?? "—")")
        print("Số CCCD: \(info?.idNumber ?? "—")")
    }

    func onError(_ error: CCCDError) {
        print("Lỗi: \(error.message)")
    }
}

// 3. Bắt đầu quét
let config = CCCDConfig(
    readFaceImage: true,
    readPersonalInfo: true,
    nfcTimeoutMs: 30_000
)
reader.startFullFlow(config: config, callback: MyCallback())
```

### Chỉ quét MRZ

```swift
reader.startMrzScan(config: .defaultConfig, callback: myCallback)
```

### Chỉ đọc NFC (đã có MRZ)

```swift
let mrzData = MrzData(
    documentNumber: "001099123456",
    dateOfBirth: "900115",
    dateOfExpiry: "301115"
)
reader.startNfcRead(mrzData: mrzData, config: .defaultConfig, callback: myCallback)
```

## Lưu ý

- **NFC chỉ hoạt động trên thiết bị thật** (iPhone 7+), không chạy được trên Simulator
- Camera MRZ scan cần quét mặt sau của thẻ CCCD (có 3 dòng MRZ)
- Khi đọc NFC, giữ thẻ ổn định ở phần trên mặt sau điện thoại
- Nếu đọc lỗi, kiểm tra lại MRZ data và thử lại
