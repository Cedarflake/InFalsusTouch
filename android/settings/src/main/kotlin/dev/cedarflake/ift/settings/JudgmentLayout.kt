package dev.cedarflake.ift.settings

data class JudgmentLayout(
  val fieldLeft: Float = 0.07f,
  val fieldRight: Float = 0.93f,
  val fieldY: Float = 0.635f,
  val floorLeft: Float = 0.21f,
  val floorRight: Float = 0.79f,
  val floorY: Float = 0.89f,
  val sideLeft: Float = 0.06f,
  val sideRight: Float = 0.94f,
  val sideY: Float = 0.725f,
  val hitPadding: Float = 0.04f,
) {
  init {
    require(listOf(fieldLeft, fieldRight, fieldY, floorLeft, floorRight, floorY, sideLeft, sideRight, sideY)
      .all { it.isFinite() && it in 0f..1f })
    require(fieldRight - fieldLeft >= 0.1f && floorRight - floorLeft >= 0.1f)
    require(sideLeft < floorLeft && floorRight < sideRight)
    require(hitPadding in 0.01f..0.1f)
    require(fieldY > hitPadding && fieldY + hitPadding < sideY - hitPadding)
    require(sideY < floorY && floorY < 1f)
  }
}
