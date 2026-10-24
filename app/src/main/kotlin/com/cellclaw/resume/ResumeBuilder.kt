package com.cellclaw.resume

import com.cellclaw.jd.JdRequirement
import com.cellclaw.profile.CandidateProfile
import com.cellclaw.profile.ProfileFact

class ResumeBuilder {

    /**
     * Builds a tailored [ResumeContent] and [EvidenceMap] using ONLY confirmed profile facts.
     * Ranks skills, projects, and internships to align with the provided JD.
     * Every line in the resume must have an entry in EvidenceMap pointing to a confirmed fact.
     */
    fun build(profile: CandidateProfile, jd: JdRequirement): ResumeContent {
        val nameFact = profile.name?.takeIf { it.confirmed }
            ?: error("Cannot build resume without confirmed candidate name")
        val emailFact = profile.email?.takeIf { it.confirmed }
            ?: error("Cannot build resume without confirmed candidate email")
        val phoneFact = profile.phone?.takeIf { it.confirmed }

        val evidence = mutableMapOf<String, String>()
        val sections = mutableListOf<ResumeSection>()

        // 1. Education Section
        val eduLines = mutableListOf<ResumeLine>()
        val eduStrings = mutableListOf<String>()

        profile.degree?.takeIf { it.confirmed }?.let { degreeFact ->
            val branchStr = profile.branch?.takeIf { it.confirmed }?.value?.let { " in $it" } ?: ""
            val collegeStr = profile.college?.takeIf { it.confirmed }?.value?.let { ", $it" } ?: ""
            val yearStr = profile.graduationYear?.takeIf { it.confirmed }?.value?.let { " ($it)" } ?: ""
            val lineId = "edu-degree"
            val text = "${degreeFact.value}$branchStr$collegeStr$yearStr"
            eduLines.add(ResumeLine(id = lineId, text = text, profileFactId = degreeFact.id))
            eduStrings.add(text)
            evidence[lineId] = degreeFact.id
        }

        profile.cgpa?.takeIf { it.confirmed }?.let { cgpaFact ->
            val scaleStr = profile.cgpaScale?.takeIf { it.confirmed }?.value?.let { "/$it" } ?: "/10.0"
            val lineId = "edu-cgpa"
            val text = "CGPA: ${cgpaFact.value}$scaleStr"
            eduLines.add(ResumeLine(id = lineId, text = text, profileFactId = cgpaFact.id))
            evidence[lineId] = cgpaFact.id
        }

        if (eduLines.isNotEmpty()) {
            sections.add(ResumeSection(title = "Education", lines = eduLines))
        }

        // 2. Skills Section: Rank confirmed skills against JD
        val confirmedSkills = profile.skills.filter { it.confirmed }
        if (confirmedSkills.isNotEmpty()) {
            val jdRequired = jd.requiredSkills.value.orEmpty().map { normalize(it) }.toSet()
            val jdPreferred = jd.preferredSkills.value.orEmpty().map { normalize(it) }.toSet()

            // Rank: 1. matches required, 2. matches preferred, 3. other skills
            val rankedSkills = confirmedSkills.sortedWith(
                compareBy<ProfileFact> { fact ->
                    val norm = normalize(fact.value)
                    when {
                        norm in jdRequired -> 0
                        norm in jdPreferred -> 1
                        else -> 2
                    }
                }.thenBy { it.value.lowercase() }
            )

            val skillLines = rankedSkills.mapIndexed { index, fact ->
                val lineId = "skill-$index"
                evidence[lineId] = fact.id
                ResumeLine(id = lineId, text = fact.value, profileFactId = fact.id)
            }
            sections.add(ResumeSection(title = "Technical Skills", lines = skillLines))
        }

        // 3. Projects Section: Rank confirmed projects by overlap with JD skills
        val confirmedProjects = profile.projects.filter { it.confirmed }
        if (confirmedProjects.isNotEmpty()) {
            val allJdSkills = (jd.requiredSkills.value.orEmpty() + jd.preferredSkills.value.orEmpty())
                .map { normalize(it) }.filter { it.isNotBlank() }

            val rankedProjects = confirmedProjects.sortedByDescending { project ->
                val normProject = normalize(project.value)
                allJdSkills.count { skill -> normProject.contains(skill) }
            }

            val projectLines = rankedProjects.mapIndexed { index, fact ->
                val lineId = "project-$index"
                evidence[lineId] = fact.id
                ResumeLine(id = lineId, text = fact.value, profileFactId = fact.id)
            }
            sections.add(ResumeSection(title = "Projects", lines = projectLines))
        }

        // 4. Experience / Internships Section
        val confirmedInternships = profile.internships.filter { it.confirmed }
        if (confirmedInternships.isNotEmpty()) {
            val allJdSkills = (jd.requiredSkills.value.orEmpty() + jd.preferredSkills.value.orEmpty())
                .map { normalize(it) }.filter { it.isNotBlank() }

            val rankedInternships = confirmedInternships.sortedByDescending { internship ->
                val norm = normalize(internship.value)
                allJdSkills.count { skill -> norm.contains(skill) }
            }

            val internshipLines = rankedInternships.mapIndexed { index, fact ->
                val lineId = "internship-$index"
                evidence[lineId] = fact.id
                ResumeLine(id = lineId, text = fact.value, profileFactId = fact.id)
            }
            sections.add(ResumeSection(title = "Experience & Internships", lines = internshipLines))
        }

        // 5. Certifications Section
        val confirmedCerts = profile.certificates.filter { it.confirmed }
        if (confirmedCerts.isNotEmpty()) {
            val certLines = confirmedCerts.mapIndexed { index, fact ->
                val lineId = "cert-$index"
                evidence[lineId] = fact.id
                ResumeLine(id = lineId, text = fact.value, profileFactId = fact.id)
            }
            sections.add(ResumeSection(title = "Certifications", lines = certLines))
        }

        val links = profile.links.filter { it.confirmed }.map { it.value }

        return ResumeContent(
            name = nameFact.value,
            email = emailFact.value,
            phone = phoneFact?.value,
            links = links,
            education = eduStrings,
            sections = sections,
            evidenceMap = EvidenceMap(evidence)
        )
    }

    private fun normalize(str: String): String =
        str.lowercase().replace(".", "").replace("-", "").replace(" ", "").trim()
}
