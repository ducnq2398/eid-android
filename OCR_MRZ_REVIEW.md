# iOS OCR MRZ SDK - Code Review

**Date**: April 23, 2026  
**Component**: Vietnam CCCD MRZ (Machine Readable Zone) OCR Detection  
**Language**: Swift  
**Platform**: iOS (Vision Framework)

---

## 1. Architecture Overview

### 1.1 Core Components

```
VisionMrzScannerService
    ├── VisionMrzFlowController (Orchestration)
    │   └── MrzScannerViewController (Camera UI & Capture)
    │       └── AVCaptureVideoDataOutput (Frame Processing)
    │           └── VNRecognizeTextRequest (OCR Engine)
    ├── MrzParser (TD1 Format Parsing)
    └── MrzData (Data Model)
```

### 1.2 Processing Flow

1. **Camera Capture** → Frames from AVCaptureSession
2. **Text Recognition** → Vision Framework OCR via VNRecognizeTextRequest
3. **MRZ Detection** → Find 3 consecutive 30-char lines
4. **OCR Cleaning** → Fix common OCR errors (O→0, I→1, etc.)
5. **Validation** → Multi-frame confirmation (N consecutive identical results)
6. **Parsing** → Extract CCCD data, validate check digits
7. **Return** → Deliver MrzData back to caller

---

## 2. Strengths ✅

### 2.1 Robust OCR Error Correction

- **`cleanOcrText()`**: Fixes bracket misrecognition (`{} → <>`, `[] → <>`, etc.)
- **`smartCleanMrzLine()`**: Position-aware digit fixing (O→0 only in numeric zones)
- **`fixToDigit()`**: Maps common OCR confusions (O→0, I/L→1, Z→2, S→5, B→8)

**Impact**: Significantly improves OCR reliability without over-correcting alphabetic zones.

### 2.2 Multi-Frame Validation Strategy

- Requires N consecutive frames with identical MRZ string before parsing
- Configurable via `CCCDConfig.mrzConsecutiveFrames` (default: 3)
- Prevents false positives from transient OCR errors

**Code**: `VisionMrzScannerService.swift` L88-99

```swift
if consecutiveCount >= max(1, config.mrzConsecutiveFrames) {
    if let mrzData = MrzParser.parse(mrzLines: mrzLines) {
        complete(with: .success(mrzData))
    }
}
```

### 2.3 ICAO TD1 Compliance

- Proper 3-line × 30-char format parsing
- Validates document type, line lengths, check digits (DOB, DOE, doc number)
- Returns `nil` on any validation failure

### 2.4 Vietnam CCCD Specific Handling

- Reconstructs full 12-digit document number from 9-digit MRZ + 3-digit overflow in `optionalData1`
- Handles both direct TD1 VNM prefixes and variations (I<VNM, IDVNM, I0VNM, ICVNM)

**Code**: `MrzData.swift` L36-53

```swift
let merged = primary + extensionDigits
if merged.allSatisfy(\.isNumber), merged.count > 12 {
    return String(merged.prefix(12))
}
```

### 2.5 Clean Async/Await Integration

- Uses `CheckedContinuation` pattern with timeout support
- Proper camera permission handling
- Safe view controller lifecycle management (weak refs)

### 2.6 Comprehensive UI/UX

- Scan line animation with gradient
- Corner brackets guide frame
- Dark overlay with transparent cutout
- Real-time instruction feedback
- Professional styling (#FF6B35 accent color)

---

## 3. Issues & Concerns ⚠️

### 3.1 CHECK DIGIT VALIDATION INCOMPLETE

**Issue**: Only validates 2 of 3 check digits in line 2

**Current Code** (`MrzParser.swift` L35-36):

```swift
guard computeCheckDigit(input: dateOfBirth) == dobCheckDigit else { return nil }
guard computeCheckDigit(input: dateOfExpiry) == doeCheckDigit else { return nil }
```

**Missing**: Position 30 (final check digit covering entire machine readable zone)

**ICAO TD1 Standard**:

- CD1: Document number (line 1, pos 14)
- CD2: DOB (line 2, pos 6)
- CD3: DOE (line 2, pos 14)
- **CD4: Composite check digit (line 2, pos 29)** ← NOT VALIDATED

**Recommendation**:

```swift
private static func validateCompositeCheckDigit(line2: String, line1: String) -> Bool {
    let compositeStr = line1.substring(from: 5, length: 9)   // Doc number
                     + line2.substring(from: 0, length: 7)   // DOB + CD
                     + line2.substring(from: 8, length: 7)   // DOE + CD
                     + line2.substring(from: 15, length: 3)  // Nationality
    let expectedCD = computeCheckDigit(input: compositeStr)
    let actualCD = charToValue(charAt(line2, 29))
    return expectedCD == actualCD
}
```

**Impact**: Low-risk undetected corruptions currently possible.

---

### 3.2 Insufficient Line Validation in findMrzLines()

**Issue**: `cleanAndValidateLine()` only checks:

- Length bounds (min 25, pad to 30)
- First character for line 1 (I/A/C prefix)
- First 6 chars numeric for line 2

**Missing Checks**:

- Line 2: Position 7 should be single char (M/F/X for sex)
- Line 2: Positions 8-13 should be numeric (DOE)
- Line 3: Should contain at least one `<` separator (name format)
- All lines: High confidence thresholds for OCR recognition

**Current Code** (`VisionMrzScannerService.swift` L643-656):

```swift
switch lineNumber {
case 1:
    return (cleaned.hasPrefix("I") || cleaned.hasPrefix("A") || cleaned.hasPrefix("C")) ? cleaned : nil
case 2:
    let prefix = String(cleaned.prefix(6))
    let valid = prefix.allSatisfy { $0.isNumber || $0 == "<" }
    return valid ? cleaned : nil
case 3:
    return cleaned  // ⚠️ NO VALIDATION
}
```

**Recommendation**:

```swift
case 2:
    let prefix = String(cleaned.prefix(7))
    let dateRange = String(cleaned.dropFirst(8).prefix(6))
    let sex = charAt(cleaned, 7)
    let validSex = [Character("M"), Character("F"), Character("X"), Character("<")].contains(sex)
    let validDates = dateRange.allSatisfy { $0.isNumber || $0 == "<" }
    return prefix.allSatisfy { $0.isNumber || $0 == "<" } && validSex && validDates ? cleaned : nil
case 3:
    return cleaned.contains("<<") ? cleaned : nil
}
```

**Impact**: Medium - Could accept malformed MRZ data or OCR noise.

---

### 3.3 OCR Language Recognition Not Optimized

**Issue**: Fixed to English recognition only

**Code** (`VisionMrzScannerService.swift` L671-673):

```swift
let request = VNRecognizeTextRequest { ... }
request.recognitionLanguages = ["en-US"]  // Hardcoded
request.recognitionLevel = .accurate
```

**Concerns**:

- MRZ contains uppercase letters + numbers (no Vietnamese characters)
- Using `.accurate` (slower) vs `.fast` not configurable
- No way to adjust text recognition settings per card type

**Recommendation**:

```swift
var recognitionConfig: TextRecognitionConfig {
    return TextRecognitionConfig(
        languages: ["en-US"],           // MRZ is always English
        level: config.mrzOcrAccuracy,   // From CCCDConfig
        useLanguageCorrection: false,   // Disable - MRZ format is strict
        usesLanguageModelBased: false   // Disable - MRZ is not natural language
    )
}
```

**Impact**: Low-Medium - Current behavior may be acceptable but inflexible.

---

### 3.4 Missing Line Number Validation in Parse

**Issue**: `parse()` doesn't validate line count before parsing

**Current Code** (`MrzParser.swift` L9):

```swift
public static func parse(mrzLines: [String]) -> MrzData? {
    guard mrzLines.count == td1NumLines else { return nil }
    // Continues...
}
```

✅ **Actually this IS correct** - Early return prevents issues.

---

### 3.5 Race Condition in Frame Processing

**Issue**: `isProcessingFrame` flag can miss frames between async operations

**Current Code** (`VisionMrzScannerService.swift` L678-683):

```swift
nonisolated func captureOutput(...) {
    Task { @MainActor [weak self] in
        guard let self else { return }
        guard !isCompleted, !isProcessingFrame else { return }
        isProcessingFrame = true
        defer { isProcessingFrame = false }
        // Async OCR work... but flag reset might occur before completion
    }
}
```

**Issue**: If OCR completes before next frame arrives, flag is already `false`. If new frame arrives during OCR, it returns early (good). But heavy OCR processing → frame drop → longer wait time.

**Recommendation**: Use `DispatchSemaphore` or queue-based serialization:

```swift
let ocrQueue = DispatchQueue(label: "com.vncccd.ocr", qos: .userInitiated)

nonisolated func captureOutput(...) {
    ocrQueue.async { [weak self] in
        // Process serially - only one frame processed at a time
    }
}
```

**Impact**: Low-Medium - Current approach works but can drop frames unnecessarily.

---

### 3.6 Insufficient Error Handling in Camera Setup

**Issue**: Several points return generic `.cameraNotAvailable` error

**Code** (`VisionMrzScannerService.swift` L492-510`):

```swift
guard let presenter = Self.topViewController() else {
    throw CCCDError.cameraNotAvailable  // Too vague
}
// Permission denied/restricted:
if cameraStatus == .denied || cameraStatus == .restricted {
    throw CCCDError.cameraNotAvailable  // Should be different error
}
```

**Better Error Types**:

```swift
enum CCCDError {
    case cameraNotAvailable
    case cameraAccessDenied        // User denied permission
    case cameraAccessRestricted    // OS restricted (MDM, etc)
    case noCameraHardware          // Device doesn't have camera
    case cameraConfigurationFailed // Hardware issue
    case viewControllerNotFound    // UI presentation failed
}
```

**Impact**: Low - Doesn't affect parsing but impacts user experience.

---

### 3.7 No Timeout for Individual Frame Processing

**Issue**: Timeout applies to full scan, not individual OCR operations

**Current Code** (`VisionMrzScannerService.swift` L523-535):

```swift
if config.mrzTimeoutMs > 0 {
    timeoutTask = Task { [weak self] in
        let ns = UInt64(max(0, config.mrzTimeoutMs)) * 1_000_000
        try? await Task.sleep(nanoseconds: ns)
        await MainActor.run {
            self.finish(.failure(CCCDError.timeout))
        }
    }
}
```

**Scenario**: If one OCR request hangs, whole scan times out. VNRecognizeTextRequest doesn't support per-call timeouts in Vision framework.

**Workaround** (if needed):

```swift
let ocrTask = Task {
    let handler = VNImageRequestHandler(...)
    try handler.perform([request])
}

let timeout = Task {
    try? await Task.sleep(nanoseconds: 5_000_000_000)  // 5 sec per frame
    ocrTask.cancel()
}

ocrTask.finish() // Cleanup
```

**Impact**: Low - Unlikely to occur in practice, but possible deadlock risk.

---

### 3.8 MrzData Model Missing Validation

**Issue**: `MrzData` struct accepts invalid date strings without validation

**Code** (`MrzData.swift`):

```swift
public struct MrzData: Sendable, Codable, Equatable {
    public let documentNumber: String
    public let dateOfBirth: String      // No format validation (YYMMDD)
    public let dateOfExpiry: String     // No format validation (YYMMDD)
    // ...
}
```

**Recommendation**: Add property validation:

```swift
public struct MrzData: Sendable, Codable, Equatable {
    private let _dateOfBirth: String
    public var dateOfBirth: String {
        get { _dateOfBirth }
    }

    public init(
        dateOfBirth: String,
        // ...
    ) throws {
        guard Self.isValidMrzDate(dateOfBirth) else {
            throw MrzValidationError.invalidDateFormat
        }
        self._dateOfBirth = dateOfBirth
    }

    static func isValidMrzDate(_ date: String) -> Bool {
        guard date.count == 6 else { return false }
        return date.allSatisfy(\.isNumber)
    }
}
```

**Impact**: Low-Medium - Parser already validates, but defensive programming helps.

---

## 4. Test Coverage Analysis

### 4.1 Existing Tests (MrzParserTests.swift)

✅ **What's Covered**:

- `testParseTd1Success()` - Happy path parsing
- `testCheckDigit()` - Check digit computation
- `testFullDocumentNumberReconstructsVietnameseCccdOverflow()` - Document number overflow
- `testRejectInvalidDobDigit()` - DOB validation

### 4.2 Missing Test Cases

❌ **Critical Missing**:

1. DOE (Date of Expiry) check digit validation
2. Composite check digit (position 29) validation
3. Invalid sex character (not M/F/X)
4. Malformed line 3 (no `<<` separator)
5. Out-of-order lines
6. OCR error correction edge cases:
   - Multiple consecutive confusable chars
   - Mixed case input
   - Unicode lookalikes (ⅠⅤⅩ vs IVX)
7. Name parsing with multiple `<<`:
   - Single name (no middle)
   - Multiple given names
   - Empty surname

**Recommended Test Suite**:

```swift
class MrzParserComprehensiveTests: XCTestCase {
    func testParseRejectsInvalidDoeCheckDigit() { }
    func testParseRejectsInvalidCompositeCheckDigit() { }
    func testParseRejectsInvalidSexCharacter() { }
    func testParseRejectsMissingNameSeparator() { }
    func testSmartCleanPreservesLettersInLine3() { }
    func testOcrCleaningEdgeCases() { }
    func testDocumentNumberOverflowTruncation() { }
}
```

---

## 5. Performance Considerations

### 5.1 OCR Performance

| Aspect                 | Current                   | Status                   |
| ---------------------- | ------------------------- | ------------------------ |
| Recognition Level      | `.accurate`               | ⚠️ Slower                |
| Languages              | `["en-US"]`               | ✅ Optimal               |
| Language Correction    | `false`                   | ✅ Correct               |
| Processing Per Frame   | ~100-200ms (iPhone 12+)   | ⚠️ High variance         |
| Multi-frame Validation | 3 frames × 100ms = ~300ms | ⚠️ Slow on older devices |

**Recommendation**: Make recognition level configurable:

```swift
let level: VNRequestTextRecognitionLevel =
    config.mrzOcrQuality == .high ? .accurate : .fast
```

### 5.2 Memory Usage

✅ **Good**:

- Frames properly released after processing
- No image buffer retention
- Weak reference to self in closures

⚠️ **Potential Issue**:

- Large number of `smartCleanMrzLine()` calls create temporary char arrays
- Consider using `NSString` methods for better performance

---

## 6. Android Comparison

**Note**: Android SDK has these additional features worth porting:

| Feature                           | Android    | iOS           | Status     |
| --------------------------------- | ---------- | ------------- | ---------- |
| Multi-frame validation            | ✅ Yes     | ✅ Yes        | ✓          |
| Smart OCR cleaning                | ✅ Yes     | ✅ Yes        | ✓          |
| Document number overflow          | ✅ Yes     | ✅ Yes        | ✓          |
| Additional check digit validation | ?          | ❌ No         | ⚠️ Missing |
| Logger/Debug output               | ✅ Log.d() | ❌ No logging | Missing    |
| Configurable timeouts             | ✅ Yes     | ⚠️ Partial    | Incomplete |

---

## 7. Summary of Recommendations

### Priority 1 (Critical)

- ✅ **Validate composite check digit** (line 2, position 29)
- ✅ **Enhance line validation** in `cleanAndValidateLine()`
- ✅ **Add comprehensive test coverage** for edge cases

### Priority 2 (Medium)

- 🟡 **Improve error types** for better UX
- 🟡 **Add logging capability** for debugging
- 🟡 **Make OCR settings configurable** (recognition level, timeout per frame)

### Priority 3 (Low)

- 🟢 **Optimize string operations** in parsing (use NSString)
- 🟢 **Add property validation** to MrzData
- 🟢 **Document known limitations** (confidence thresholds, lighting requirements)

---

## 8. Code Quality Metrics

| Metric             | Rating     | Notes                                                               |
| ------------------ | ---------- | ------------------------------------------------------------------- |
| **Architecture**   | ⭐⭐⭐⭐⭐ | Clean separation of concerns                                        |
| **Error Handling** | ⭐⭐⭐⭐   | Good async/await usage, could improve error types                   |
| **Test Coverage**  | ⭐⭐⭐     | Basic tests present, needs expansion                                |
| **Documentation**  | ⭐⭐⭐⭐   | Good comments, some complex logic needs more detail                 |
| **Security**       | ⭐⭐⭐⭐   | Input validation is solid, check digit verification almost complete |
| **Performance**    | ⭐⭐⭐⭐   | Efficient frame processing, room for OCR optimization               |

**Overall**: **B+** / **7.5/10** - Solid, production-ready code with minor validation gaps.

---

## 9. Quick Reference: Files Reviewed

| File                            | Lines | Purpose                           |
| ------------------------------- | ----- | --------------------------------- |
| `MrzParser.swift`               | 200+  | MRZ parsing logic & validation    |
| `MrzData.swift`                 | 60+   | Data model with overflow handling |
| `VisionMrzScannerService.swift` | 750+  | Camera capture & OCR integration  |
| `MrzParserTests.swift`          | 50+   | Unit tests                        |

---

## 10. Contact & Follow-up

- **Reviewer**: Code Review - April 23, 2026
- **Next Review**: Recommended after implementing Priority 1 recommendations
- **Related**: Android SDK comparison available
