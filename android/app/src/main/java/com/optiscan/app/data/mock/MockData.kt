package com.optiscan.app.data.mock

/**
 * Realistic mock data for independent UI demonstration without backend dependencies.
 */
object MockData {

    /**
     * Fictional product label text for Screen F (Read Text Results).
     */
    val SAMPLE_PRODUCT_LABEL = """
        VALLEY PHARMACY
        EXTRA STRENGTH PAIN RELIEF
        Acetaminophen Tablets USP, 500 mg
        Pain Reliever • Fever Reducer
        100 COATED CAPLETS

        DIRECTIONS:
        Adults and children 12 years and over:
        Take 2 caplets every 6 hours with water while symptoms last.
        Do not take more than 6 caplets in 24 hours.

        WARNINGS:
        Liver warning: This product contains acetaminophen.
        Severe liver damage may occur if you take more than 4,000 mg in 24 hours.
        Keep out of reach of children.

        EXP: 11/2028  |  LOT: 9342B-01
        MADE IN USA
    """.trimIndent()

    /**
     * Natural-language scene description for Screen G (Description Results).
     */
    val SAMPLE_SCENE_DESCRIPTION = """
        You are facing a well-lit indoor hallway with smooth hardwood flooring. Directly in front of you is a clear walking path leading toward an open doorway approximately 10 feet ahead.

        To your right side, there is a low wooden bench with a backpack resting on it. To your left side, the wall is clear with an accessible handrail along the perimeter. No trip hazards are detected on the ground.
    """.trimIndent()

    /**
     * Simulated Q&A responses for Screen H (Ask a Question).
     */
    fun answerQuestion(question: String, contextText: String? = null): String {
        val q = question.lowercase().trim()
        return when {
            q.contains("expire") || q.contains("expiration") || q.contains("date") -> {
                "The expiration date printed on the lower corner is November 2028 (Lot 9342B-01)."
            }
            q.contains("dose") || q.contains("dosage") || q.contains("how many") || q.contains("take") -> {
                "Take 2 caplets every 6 hours with a glass of water. Do not exceed 6 caplets within 24 hours."
            }
            q.contains("ingredient") || q.contains("what is in") || q.contains("acetaminophen") -> {
                "The active ingredient is Acetaminophen USP, 500 milligrams per caplet."
            }
            q.contains("path") || q.contains("obstacle") || q.contains("walk") || q.contains("clear") || q.contains("door") -> {
                "Yes, the path straight ahead is clear. There is an open doorway approximately 10 feet in front of you."
            }
            q.contains("right") || q.contains("bench") || q.contains("bag") -> {
                "To your right, there is a low wooden bench with a backpack resting on it."
            }
            else -> {
                if (!contextText.isNullOrBlank()) {
                    "Regarding your question '$question': Based on the text, this is an over-the-counter pain reliever with standard usage instructions and warnings."
                } else {
                    "In response to '$question': The area in view is a brightly lit indoor space with no immediate hazards in your direct path."
                }
            }
        }
    }
}
