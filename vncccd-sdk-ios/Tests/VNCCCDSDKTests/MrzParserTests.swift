import XCTest
@testable import VNCCCDSDK

final class MrzParserTests: XCTestCase {
    func testParseTd1Success() {
        let mrz = [
            "I<VNM0123456785<<<123<<<<<<<<<<",
            "9001011M3501014VNM<<<<<<<<<<<6",
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        ]

        let result = MrzParser.parse(mrzLines: mrz)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.documentNumber, "012345678")
        XCTAssertEqual(result?.dateOfBirth, "900101")
        XCTAssertEqual(result?.dateOfExpiry, "350101")
    }

    func testCheckDigit() {
        XCTAssertEqual(MrzParser.computeCheckDigit(input: "900101"), 1)
    }

    func testRejectInvalidDobDigit() {
        let mrz = [
            "I<VNM0123456785<<<123<<<<<<<<<<",
            "9001019M3501014VNM<<<<<<<<<<<6",
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        ]
        XCTAssertNil(MrzParser.parse(mrzLines: mrz))
    }
}
