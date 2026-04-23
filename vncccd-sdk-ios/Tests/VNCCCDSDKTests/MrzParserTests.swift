import XCTest
@testable import VNCCCDSDK

final class MrzParserTests: XCTestCase {
    func testParseTd1Success() {
        let mrz = [
            "I<VNM0123456784<<<123<<<<<<<<<",
            "9001011M3501014VNM<<<<<<<<<<<4",
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        ]

        let result = MrzParser.parse(mrzLines: mrz)
        XCTAssertNotNil(result)
        XCTAssertEqual(result?.documentNumber, "012345678")
        XCTAssertEqual(result?.dateOfBirth, "900101")
        XCTAssertEqual(result?.dateOfExpiry, "350101")
    }

    func testCheckDigit() {
        XCTAssertEqual(MrzParser.computeCheckDigit("900101"), 1)
    }

    func testFullDocumentNumberReconstructsVietnameseCccdOverflow() {
        let mrz = MrzData(
            documentNumber: "012345678",
            dateOfBirth: "900101",
            dateOfExpiry: "350101",
            optionalData1: "901<<<<<<<<<<<<"
        )

        XCTAssertEqual(mrz.fullDocumentNumber, "012345678901")
    }

    func testFullDocumentNumberFallbackWhenOverflowNotAtPrefix() {
        let mrz = MrzData(
            documentNumber: "012345678",
            dateOfBirth: "900101",
            dateOfExpiry: "350101",
            optionalData1: "<<901<<<<<<<<<<<"
        )

        XCTAssertEqual(mrz.fullDocumentNumber, "012345678901")
    }

    func testFullDocumentNumberNormalizesOcrMistakes() {
        let mrz = MrzData(
            documentNumber: "O1234S67B",
            dateOfBirth: "900101",
            dateOfExpiry: "350101",
            optionalData1: "9O1<<<<<<<<<<<<"
        )

        XCTAssertEqual(mrz.fullDocumentNumber, "012345678901")
    }

    func testFullDocumentNumberFromVietnamVariantOptionalData1() {
        let lines = [
            "IDVNM2010228641001201022864<<2",
            "0111241M2611240VNM<<<<<<<<<<<4",
            "NGUYEN<<HUU<QUOC<KHANH<<<<<<<<"
        ]
        let parsed = MrzParser.parse(mrzLines: lines)
        XCTAssertNotNil(parsed)
        XCTAssertEqual(parsed?.fullDocumentNumber, "001201022864")
    }

    func testRejectInvalidDobDigit() {
        let mrz = [
            "I<VNM0123456784<<<123<<<<<<<<<",
            "9001019M3501014VNM<<<<<<<<<<<4",
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        ]
        XCTAssertNil(MrzParser.parse(mrzLines: mrz))
    }

    func testRejectInvalidDocumentNumberDigit() {
        let mrz = [
            "I<VNM0123456785<<<123<<<<<<<<<",
            "9001011M3501014VNM<<<<<<<<<<<4",
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        ]
        XCTAssertNil(MrzParser.parse(mrzLines: mrz))
    }

    func testRejectInvalidCompositeDigit() {
        let mrz = [
            "I<VNM0123456784<<<123<<<<<<<<<",
            "9001011M3501014VNM<<<<<<<<<<<9",
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        ]
        XCTAssertNil(MrzParser.parse(mrzLines: mrz))
    }

    func testMrzKeyBuilderBuildsUsingTd1Fields() {
        let mrz = [
            "I<VNM0123456784<<<123<<<<<<<<<",
            "9001011M3501014VNM<<<<<<<<<<<4",
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        ]
        let data = MrzParser.parse(mrzLines: mrz)
        XCTAssertNotNil(data)

        let key = MrzKeyBuilder.build(from: data!)
        XCTAssertEqual(key, "012345678490010113501014")
    }

    func testParseNormalizesUnknownGenderToX() {
        let mrz = [
            "I<VNM0123456784<<<123<<<<<<<<<",
            "9001011<3501014VNM<<<<<<<<<<<4",
            "NGUYEN<<VAN<A<<<<<<<<<<<<<<<<<"
        ]
        let data = MrzParser.parse(mrzLines: mrz)
        XCTAssertEqual(data?.gender, "X")
    }
}
