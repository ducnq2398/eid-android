package com.vncccd.sample

import android.os.Bundle
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.vncccd.sdk.models.CCCDData

/**
 * Hiển thị kết quả đọc CCCD.
 */
class ResultActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_CCCD_DATA = "extra_cccd_data"
    }

    private lateinit var ivFacePhoto: ImageView
    private lateinit var tvFullName: TextView
    private lateinit var tvIdNumber: TextView
    private lateinit var tvDateOfBirth: TextView
    private lateinit var tvGender: TextView
    private lateinit var tvNationality: TextView
    private lateinit var tvEthnicity: TextView
    private lateinit var tvReligion: TextView
    private lateinit var tvPlaceOfOrigin: TextView
    private lateinit var tvPlaceOfResidence: TextView
    private lateinit var tvDateOfIssue: TextView
    private lateinit var tvDateOfExpiry: TextView
    private lateinit var tvFatherName: TextView
    private lateinit var tvMotherName: TextView
    private lateinit var tvPersonalId: TextView

    @Suppress("DEPRECATION")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_result)

        val cccdData = intent.getSerializableExtra(EXTRA_CCCD_DATA) as? CCCDData
        if (cccdData == null) {
            finish()
            return
        }

        initViews()
        displayData(cccdData)
    }

    private fun initViews() {
        ivFacePhoto = findViewById(R.id.ivFacePhoto)
        tvFullName = findViewById(R.id.tvFullName)
        tvIdNumber = findViewById(R.id.tvIdNumber)
        tvDateOfBirth = findViewById(R.id.tvDateOfBirth)
        tvGender = findViewById(R.id.tvGender)
        tvNationality = findViewById(R.id.tvNationality)
        tvEthnicity = findViewById(R.id.tvEthnicity)
        tvReligion = findViewById(R.id.tvReligion)
        tvPlaceOfOrigin = findViewById(R.id.tvPlaceOfOrigin)
        tvPlaceOfResidence = findViewById(R.id.tvPlaceOfResidence)
        tvDateOfIssue = findViewById(R.id.tvDateOfIssue)
        tvDateOfExpiry = findViewById(R.id.tvDateOfExpiry)
        tvFatherName = findViewById(R.id.tvFatherName)
        tvMotherName = findViewById(R.id.tvMotherName)
        tvPersonalId = findViewById(R.id.tvPersonalId)
    }

    private fun displayData(cccdData: CCCDData) {
        // Face image
//        cccdData.faceImage?.let {
//            ivFacePhoto.setImageBitmap(it)
//        }

        val info = cccdData.personalInfo

        // Display all fields
        tvFullName.text = info?.fullName
        tvIdNumber.text = info?.idNumber
        tvDateOfBirth.text = info?.dateOfBirth
        tvGender.text = info?.gender
        tvNationality.text = info?.nationality
        tvEthnicity.text = info?.ethnicity ?: "—"
        tvReligion.text = info?.religion ?: "—"
        tvPlaceOfOrigin.text = info?.placeOfOrigin ?: "—"
        tvPlaceOfResidence.text = info?.placeOfResidence ?: "—"
        tvDateOfIssue.text = info?.dateOfIssue ?: "—"
        tvDateOfExpiry.text = info?.dateOfExpiry
        tvFatherName.text = info?.fatherName ?: "—"
        tvMotherName.text = info?.motherName ?: "—"
        tvPersonalId.text = info?.personalIdentification ?: "—"
    }

    /**
     * Convert MRZ date YYMMDD -> DD/MM/YYYY
     */
    private fun formatMrzDate(mrzDate: String): String {
        if (mrzDate.length != 6) return mrzDate
        val yy = mrzDate.substring(0, 2).toIntOrNull() ?: return mrzDate
        val mm = mrzDate.substring(2, 4)
        val dd = mrzDate.substring(4, 6)
        val yyyy = if (yy > 50) "19$yy" else "20$yy"
        return "$dd/$mm/$yyyy"
    }
}
