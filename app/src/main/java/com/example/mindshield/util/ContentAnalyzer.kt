package com.example.mindshield.util

data class ContentAnalysisResult(
    val isDistracting: Boolean,
    val confidence: Float,
    val reason: String,
    val contentType: String
)

object ContentAnalyzer {
    
    // Content classification keywords
    private val ENTERTAINMENT_KEYWORDS = listOf(
        "comedy", "funny", "jabardasth", "telugu", "entertainment", "music", "dance",
        "movie", "trailer", "gaming", "stream", "vlog", "prank", "challenge",
        "reaction", "tiktok", "instagram", "social media", "celebrity", "gossip",
        "fun", "laugh", "humor", "joke", "meme", "viral", "trending", "popular",
        "show", "series", "episode", "season", "drama", "romance", "action",
        "thriller", "horror", "fantasy", "sci-fi", "animation", "cartoon"
    )
    
    private val PRODUCTIVE_KEYWORDS = listOf(
        "tutorial", "educational", "learning", "course", "lecture", "documentary",
        "news", "politics", "business", "technology", "science", "research",
        "how to", "guide", "tips", "tricks", "professional", "work", "study",
        "academic", "university", "college", "school", "training", "workshop",
        "seminar", "conference", "presentation", "analysis", "report", "paper",
        "article", "journal", "publication", "thesis", "dissertation", "research",
        "development", "programming", "coding", "software", "engineering",
        "mathematics", "physics", "chemistry", "biology", "history", "geography"
    )
    
    fun analyzeYouTubeContent(
        title: String,
        description: String,
        channel: String,
        tags: List<String>
    ): ContentAnalysisResult {
        val allText = "${title.lowercase()} ${description.lowercase()} ${channel.lowercase()} ${tags.joinToString(" ").lowercase()}"
        
        val entertainmentScore = calculateEntertainmentScore(allText)
        val productiveScore = calculateProductiveScore(allText)
        
        val isDistracting = entertainmentScore > productiveScore && entertainmentScore > 0.3f
        val confidence = maxOf(entertainmentScore, productiveScore)
        
        return ContentAnalysisResult(
            isDistracting = isDistracting,
            confidence = confidence,
            reason = if (isDistracting) "Entertainment content detected" else "Productive content detected",
            contentType = if (isDistracting) "Entertainment" else "Productive"
        )
    }
    
    fun analyzeChromeContent(
        url: String,
        title: String,
        content: String
    ): ContentAnalysisResult {
        val allText = "${url.lowercase()} ${title.lowercase()} ${content.lowercase()}"
        
        val entertainmentScore = calculateEntertainmentScore(allText)
        val productiveScore = calculateProductiveScore(allText)
        
        val isDistracting = entertainmentScore > productiveScore && entertainmentScore > 0.3f
        val confidence = maxOf(entertainmentScore, productiveScore)
        
        return ContentAnalysisResult(
            isDistracting = isDistracting,
            confidence = confidence,
            reason = if (isDistracting) "Entertainment content detected" else "Productive content detected",
            contentType = if (isDistracting) "Entertainment" else "Productive"
        )
    }
    
    private fun calculateEntertainmentScore(text: String): Float {
        var score = 0f
        var matches = 0
        
        ENTERTAINMENT_KEYWORDS.forEach { keyword ->
            if (text.contains(keyword, ignoreCase = true)) {
                score += 0.1f
                matches++
            }
        }
        
        return if (matches > 0) score / matches else 0f
    }
    
    private fun calculateProductiveScore(text: String): Float {
        var score = 0f
        var matches = 0
        
        PRODUCTIVE_KEYWORDS.forEach { keyword ->
            if (text.contains(keyword, ignoreCase = true)) {
                score += 0.1f
                matches++
            }
        }
        
        return if (matches > 0) score / matches else 0f
    }
} 