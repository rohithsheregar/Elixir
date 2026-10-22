package com.cellclaw.profile

import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import java.time.Instant

@Serializable
enum class ProfileFactSource { USER_ENTERED, IMPORTED_FROM_RESUME }

@Serializable
data class ProfileFact(
    val id: String,
    val field: String,
    val value: String,
    val source: ProfileFactSource,
    val confirmed: Boolean,
    @Serializable(with = InstantIso8601Serializer::class)
    val updatedAt: Instant
)

@Serializable
data class CandidateProfile(
    val name: ProfileFact? = null,
    val email: ProfileFact? = null,
    val phone: ProfileFact? = null,
    val college: ProfileFact? = null,
    val university: ProfileFact? = null,
    val degree: ProfileFact? = null,
    val branch: ProfileFact? = null,
    val graduationYear: ProfileFact? = null,
    val cgpa: ProfileFact? = null,
    val cgpaScale: ProfileFact? = null,
    val marks10th: ProfileFact? = null,
    val marks12th: ProfileFact? = null,
    val skills: List<ProfileFact> = emptyList(),
    val projects: List<ProfileFact> = emptyList(),
    val internships: List<ProfileFact> = emptyList(),
    val certificates: List<ProfileFact> = emptyList(),
    val links: List<ProfileFact> = emptyList(),
    val reusableAnswers: List<ProfileFact> = emptyList()
)

/** Stores [Instant] values as ISO-8601 strings so profile JSON remains portable and readable. */
object InstantIso8601Serializer : KSerializer<Instant> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("Instant", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: Instant) = encoder.encodeString(value.toString())

    override fun deserialize(decoder: Decoder): Instant = Instant.parse(decoder.decodeString())
}
