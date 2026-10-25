package com.cellclaw.di

import android.content.Context
import android.content.ContextWrapper
import com.cellclaw.accessibility.AccessibilitySession
import com.cellclaw.approval.ApprovalTokenManager
import com.cellclaw.approval.ConfirmationDetector
import com.cellclaw.approval.SubmitSupervisor
import com.cellclaw.form.AccessibilityFormNodeOperator
import com.cellclaw.form.FieldClassifier
import com.cellclaw.form.FieldFiller
import com.cellclaw.form.FormNodeOperator
import com.cellclaw.form.NeedsInputCollector
import com.cellclaw.resume.PdfGenerator
import com.cellclaw.resume.ResumeBuilder
import com.cellclaw.tracker.InMemoryApplicationTracker
import com.cellclaw.upload.FakeResumeUriProvider
import com.cellclaw.upload.FileProviderResumeUriProvider
import com.cellclaw.upload.PostUploadVerifier
import com.cellclaw.upload.ResumeUploader
import com.cellclaw.upload.ResumeUriProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DiBindingsTest {

    private class FakeTestContext(private val pkg: String = "com.cellclaw") : ContextWrapper(null) {
        override fun getPackageName(): String = pkg
    }

    @Test
    fun `AppModule provides ResumeBuilder`() {
        val resumeBuilder = AppModule.provideResumeBuilder()
        assertNotNull("ResumeBuilder must not be null", resumeBuilder)
        assertTrue(resumeBuilder is ResumeBuilder)
    }

    @Test
    fun `AppModule provides PdfGenerator`() {
        val pdfGenerator = AppModule.providePdfGenerator()
        assertNotNull("PdfGenerator must not be null", pdfGenerator)
        assertTrue(pdfGenerator is PdfGenerator)
    }

    @Test
    fun `AppModule provides FieldClassifier`() {
        val fieldClassifier = AppModule.provideFieldClassifier()
        assertNotNull("FieldClassifier must not be null", fieldClassifier)
        assertTrue(fieldClassifier is FieldClassifier)
    }

    @Test
    fun `AppModule provides NeedsInputCollector`() {
        val collector = AppModule.provideNeedsInputCollector()
        assertNotNull("NeedsInputCollector must not be null", collector)
        assertTrue(collector is NeedsInputCollector)
        assertFalse(collector.hasPendingInputs())
    }

    @Test
    fun `AppModule provides FormNodeOperator with AccessibilityFormNodeOperator`() {
        val operator = AppModule.provideFormNodeOperator()
        assertNotNull("FormNodeOperator must not be null", operator)
        assertTrue(operator is AccessibilityFormNodeOperator)
    }

    @Test
    fun `AppModule provides FieldFiller wired with dependencies`() {
        val classifier = AppModule.provideFieldClassifier()
        val operator = AppModule.provideFormNodeOperator()
        val collector = AppModule.provideNeedsInputCollector()

        val filler = AppModule.provideFieldFiller(classifier, operator, collector)
        assertNotNull("FieldFiller must not be null", filler)
        assertTrue(filler is FieldFiller)
        assertEquals(collector, filler.needsInputCollector)
    }

    @Test
    fun `AppModule provides FileProviderResumeUriProvider and excludes FakeResumeUriProvider`() {
        val fakeContext = FakeTestContext("com.cellclaw")
        val provider = AppModule.provideResumeUriProvider(fakeContext)
        assertNotNull("ResumeUriProvider must not be null", provider)
        assertTrue("Must be real FileProviderResumeUriProvider in production", provider is FileProviderResumeUriProvider)
        assertFalse("FakeResumeUriProvider must not be provided in production", provider is FakeResumeUriProvider)
    }

    @Test
    fun `AppModule provides ResumeUploader with production ResumeUriProvider`() {
        val fakeContext = FakeTestContext("com.cellclaw")
        val uriProvider = AppModule.provideResumeUriProvider(fakeContext)

        val uploader = AppModule.provideResumeUploader(uriProvider)
        assertNotNull("ResumeUploader must not be null", uploader)
        assertTrue(uploader is ResumeUploader)
    }

    @Test
    fun `AppModule provides PostUploadVerifier`() {
        val verifier = AppModule.providePostUploadVerifier()
        assertNotNull("PostUploadVerifier must not be null", verifier)
        assertTrue(verifier is PostUploadVerifier)
    }

    @Test
    fun `AppModule provides AccessibilitySession`() {
        val session = AppModule.provideAccessibilitySession()
        assertNotNull("AccessibilitySession must not be null", session)
        assertTrue(session is AccessibilitySession)
    }

    @Test
    fun `AppModule provides ConfirmationDetector and satisfies SubmitSupervisor constructor`() {
        val detector = AppModule.provideConfirmationDetector()
        assertNotNull("ConfirmationDetector must not be null", detector)
        assertTrue(detector is ConfirmationDetector)

        val tokenManager = ApprovalTokenManager()
        val tracker = InMemoryApplicationTracker()
        val supervisor = SubmitSupervisor(tokenManager, tracker, detector)
        assertNotNull("SubmitSupervisor must be constructible with Hilt-provided ConfirmationDetector", supervisor)
    }
}
