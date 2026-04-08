import Foundation

public struct PersonalInfo: Sendable, Codable, Equatable {
    public let fullName: String?
    public let idNumber: String?
    public let dateOfBirth: String?
    public let gender: String?
    public let nationality: String?
    public let ethnicity: String?
    public let religion: String?
    public let placeOfOrigin: String?
    public let placeOfResidence: String?
    public let personalIdentification: String?
    public let dateOfIssue: String?
    public let dateOfExpiry: String?
    public let fatherName: String?
    public let motherName: String?
    public let spouseName: String?
    public let oldIdNumber: String?

    public init(
        fullName: String? = nil,
        idNumber: String? = nil,
        dateOfBirth: String? = nil,
        gender: String? = nil,
        nationality: String? = nil,
        ethnicity: String? = nil,
        religion: String? = nil,
        placeOfOrigin: String? = nil,
        placeOfResidence: String? = nil,
        personalIdentification: String? = nil,
        dateOfIssue: String? = nil,
        dateOfExpiry: String? = nil,
        fatherName: String? = nil,
        motherName: String? = nil,
        spouseName: String? = nil,
        oldIdNumber: String? = nil
    ) {
        self.fullName = fullName
        self.idNumber = idNumber
        self.dateOfBirth = dateOfBirth
        self.gender = gender
        self.nationality = nationality
        self.ethnicity = ethnicity
        self.religion = religion
        self.placeOfOrigin = placeOfOrigin
        self.placeOfResidence = placeOfResidence
        self.personalIdentification = personalIdentification
        self.dateOfIssue = dateOfIssue
        self.dateOfExpiry = dateOfExpiry
        self.fatherName = fatherName
        self.motherName = motherName
        self.spouseName = spouseName
        self.oldIdNumber = oldIdNumber
    }
}
