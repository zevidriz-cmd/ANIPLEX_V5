package com.aniplex.app.domain.model

data class SkipTimes(
    val introStart: Long = -1L,
    val introEnd: Long = -1L,
    val outroStart: Long = -1L,
    val outroEnd: Long = -1L
) {
    fun hasIntro(): Boolean = introStart >= 0 && introEnd > introStart
    fun hasOutro(): Boolean = outroStart >= 0 && outroEnd > outroStart
    
    fun isDuringIntro(positionMs: Long): Boolean {
        return hasIntro() && positionMs >= introStart && positionMs < introEnd
    }
    
    fun isDuringOutro(positionMs: Long): Boolean {
        return hasOutro() && positionMs >= outroStart && positionMs < outroEnd
    }
}
