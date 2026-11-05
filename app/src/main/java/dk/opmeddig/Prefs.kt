package dk.opmeddig

import android.content.Context

object Prefs {
    private fun p(c: Context) = c.getSharedPreferences("opmeddig", Context.MODE_PRIVATE)

    fun hour(c: Context) = p(c).getInt("hour", 7)
    fun minute(c: Context) = p(c).getInt("minute", 0)
    fun count(c: Context) = p(c).getInt("count", 5)
    fun armed(c: Context) = p(c).getBoolean("armed", false)

    fun save(c: Context, hour: Int, minute: Int, count: Int, armed: Boolean) =
        p(c).edit()
            .putInt("hour", hour).putInt("minute", minute)
            .putInt("count", count).putBoolean("armed", armed)
            .apply()

    fun setArmed(c: Context, armed: Boolean) = p(c).edit().putBoolean("armed", armed).apply()

    /** Ugedage som bitmaske: bit 0 = mandag … bit 6 = søndag. Standard: alle dage. */
    fun days(c: Context) = p(c).getInt("days", 0b1111111)
    fun setDays(c: Context, days: Int) = p(c).edit().putInt("days", days).apply()

    /** Opgavetyper som bitmaske (se ChallengeType). Standard: alle. */
    fun challenges(c: Context) = p(c).getInt("challenges", ChallengeType.ALL)
    fun setChallenges(c: Context, v: Int) = p(c).edit().putInt("challenges", v).apply()

    fun ringing(c: Context) = p(c).getBoolean("ringing", false)
    fun setRinging(c: Context, v: Boolean) = p(c).edit().putBoolean("ringing", v).commit()

    /** Seneste version fundet på GitHub, fx "v1.4" */
    fun latestTag(c: Context) = p(c).getString("latestTag", "") ?: ""
    fun setLatestTag(c: Context, t: String) = p(c).edit().putString("latestTag", t).apply()
}
