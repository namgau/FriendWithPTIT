package vn.ptit.fwp.core.ai

import com.google.gson.JsonObject
import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Test
import vn.ptit.fwp.core.model.*
import java.io.File

class BApp1FixtureTest {
    private fun fixture(name: String) = JsonParser.parseString(File("../../tests/fixtures/$name").readText()).asJsonArray
    private fun str(o: JsonObject, key: String, default: String = "") = o.get(key)?.takeUnless { it.isJsonNull }?.asString ?: default
    private fun profile(o: JsonObject): Profile {
        val distance = o.get("distance_km")?.takeUnless { it.isJsonNull }?.asDouble
        return Profile(id = str(o,"id"), nickname = "", fullName = "", age = o.get("age").asInt,
            gender = Gender.OTHER, school = str(o,"school"), campus = str(o,"campus"), faculty = str(o,"faculty"),
            major = "", academicYear = "", alumni = o.get("alumni")?.asBoolean ?: false, gpa = 0.0,
            gpaTier = GpaTier.valueOf(str(o,"gpa_tier","GOOD")), bio = "",
            interests = o.getAsJsonArray("interests")?.map { it.asString } ?: emptyList(), avatarSeed = 1,
            distanceKm = distance ?: 0.0, verified = o.get("verified")?.asBoolean ?: true,
            isDemo = false, showGpa = true, showLocation = o.get("show_location")?.asBoolean ?: (distance != null),
            blocked = o.get("blocked")?.asBoolean ?: false,
            mode = ConnectionMode.valueOf(str(o,"mode","FRIENDS")))
    }

    @Test fun rankingFixtures() {
        fixture("m1_rank_cases.json").forEach { raw ->
            val c = raw.asJsonObject; val f = c.getAsJsonObject("filters")
            val filters = DiscoveryFilters(maxDistanceKm = f.get("max_distance_km")?.asInt ?: 50,
                modes = f.getAsJsonArray("modes")?.map { ConnectionMode.valueOf(it.asString) }?.toSet() ?: ConnectionMode.entries.toSet(),
                ageRange = (f.get("min_age")?.asInt ?: 18)..(f.get("max_age")?.asInt ?: 30),
                gpaTiers = f.getAsJsonArray("gpa_tiers")?.map { GpaTier.valueOf(it.asString) }?.toSet() ?: GpaTier.entries.toSet(),
                sameSchoolOnly = f.get("same_school_only")?.asBoolean ?: false,
                onlyVerified = f.get("only_verified")?.asBoolean ?: true,
                graduation = GraduationFilter.valueOf(str(f,"graduation","ALL")))
            // Swiped profiles are removed by DiscoverViewModel before the engine is called.
            val candidates = c.getAsJsonArray("candidates").map { it.asJsonObject }.filter { it.get("swiped")?.asBoolean != true }.map { profile(it) }
            val result = RankingEngine.rank(profile(c.getAsJsonObject("viewer")), candidates, filters)
            assertEquals(c.get("name").asString, c.getAsJsonArray("expected").map { it.asString }, result.map { it.profile.id })
            assertEquals(c.get("name").asString, c.getAsJsonArray("scores").map { it.asInt }, result.map { it.score })
        }
    }

    @Test fun blurFixtures() {
        val me = profile(JsonParser.parseString("""{"id":"me","age":20}""").asJsonObject)
        fixture("m2_blur_cases.json").forEach { raw ->
            val c = raw.asJsonObject; val days = c.get("days").asInt; val opt = c.get("opt_in").asBoolean
            val messages = listOf("me" to c.get("viewer").asInt, "other" to c.get("partner").asInt).flatMap { (sender, count) ->
                (0 until count).map { i -> ChatMessage("$sender-$i", "m", sender, "", createdAt = if (days == 0) 0 else 1_700_000_000_000L + (i % days) * 86_400_000L) }
            }
            val match = FwpMatch("m", "other", ConnectionMode.FRIENDS, 0, revealOptInMe = opt, revealOptInThem = opt)
            val result = CompatibilityEngine.analyze(me, me, match, messages)
            assertEquals(c.get("name").asString, c.get("blur").asFloat, result.blurLevel, 0.00011f)
            assertEquals(opt, result.revealEligible)
        }
    }

    @Test fun utc7MidnightSystemAndWithdrawnConsent() {
        val me = profile(JsonParser.parseString("""{"id":"me","age":20}""").asJsonObject)
        val first = 1_767_286_740_000L
        val messages = (0 until 30).map { i -> ChatMessage("$i", "m", if (i % 2 == 0) "me" else "other", "",
            createdAt = first + (if (i % 2 == 0) 0L else 120_000L)) }.toMutableList()
        messages.add(ChatMessage("system", "m", "system", "", type = MessageType.SYSTEM, createdAt = first + 86_520_000L))
        val opted = FwpMatch("m", "other", ConnectionMode.FRIENDS, 0, revealOptInMe = true, revealOptInThem = true)
        assertEquals(0.2833f, CompatibilityEngine.analyze(me, me, opted, messages).blurLevel, 0.0001f)
        messages[0] = messages[0].copy(createdAt = first + 86_520_000L)
        assertTrue(CompatibilityEngine.analyze(me, me, opted, messages).revealEligible)
        val withdrawn = CompatibilityEngine.analyze(me, me, opted.copy(revealOptInMe = false), messages)
        assertFalse(withdrawn.revealEligible)
        assertEquals(0.15f, withdrawn.blurLevel, 0.0001f)
    }
}
