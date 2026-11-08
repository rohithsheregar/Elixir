package com.cellclaw.di

import android.content.Context
import androidx.room.Room
import com.cellclaw.memory.MemoryDb
import com.cellclaw.memory.MemoryFactDao
import com.cellclaw.memory.MessageDao
import com.cellclaw.accessibility.AccessibilitySession
import com.cellclaw.approval.ConfirmationDetector
import com.cellclaw.form.AccessibilityFormNodeOperator
import com.cellclaw.form.FieldClassifier
import com.cellclaw.form.FieldFiller
import com.cellclaw.form.FormNodeOperator
import com.cellclaw.form.NeedsInputCollector
import com.cellclaw.provider.AnthropicProvider
import com.cellclaw.provider.GeminiProvider
import com.cellclaw.provider.OpenAIProvider
import com.cellclaw.provider.OpenRouterProvider
import com.cellclaw.provider.ProviderManager
import com.cellclaw.resume.PdfGenerator
import com.cellclaw.resume.ResumeBuilder
import com.cellclaw.tools.*
import com.cellclaw.upload.FileProviderResumeUriProvider
import com.cellclaw.upload.PostUploadVerifier
import com.cellclaw.upload.ResumeFileValidator
import com.cellclaw.upload.ResumeUploader
import com.cellclaw.upload.ResumeUriProvider
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideMemoryDb(@ApplicationContext context: Context): MemoryDb {
        return Room.databaseBuilder(
            context,
            MemoryDb::class.java,
            "cellclaw_memory"
        ).addMigrations(*MemoryDb.ALL_MIGRATIONS).build()
    }

    @Provides
    fun provideMessageDao(db: MemoryDb): MessageDao = db.messageDao()

    @Provides
    fun provideMemoryFactDao(db: MemoryDb): MemoryFactDao = db.memoryFactDao()

    @Provides
    fun provideScheduledTaskDao(db: MemoryDb): com.cellclaw.scheduler.ScheduledTaskDao = db.scheduledTaskDao()

    @Provides
    fun provideApplicationDao(db: MemoryDb): com.cellclaw.tracker.ApplicationDao = db.applicationDao()

    @Provides
    @Singleton
    fun provideApplicationTracker(dao: com.cellclaw.tracker.ApplicationDao): com.cellclaw.tracker.ApplicationTracker =
        com.cellclaw.tracker.RoomApplicationTracker(dao)

    @Provides
    @Singleton
    fun provideProfileRepository(@ApplicationContext context: Context): com.cellclaw.profile.ProfileRepository =
        com.cellclaw.profile.EncryptedSharedPreferencesProfileRepository(context)

    @Provides
    @Singleton
    fun provideProfileManager(repository: com.cellclaw.profile.ProfileRepository): com.cellclaw.profile.ProfileManager =
        com.cellclaw.profile.ProfileManager(repository)

    @Provides
    @Singleton
    fun provideEligibilityEngine(): com.cellclaw.eligibility.EligibilityEngine =
        com.cellclaw.eligibility.EligibilityEngine()

    @Provides
    @Singleton
    fun provideJdDocumentReader(): com.cellclaw.jd.JdDocumentReader =
        com.cellclaw.jd.JdDocumentReader()

    @Provides
    @Singleton
    fun provideResumeBuilder(): ResumeBuilder = ResumeBuilder()

    @Provides
    @Singleton
    fun providePdfGenerator(): PdfGenerator = PdfGenerator()

    @Provides
    @Singleton
    fun provideFieldClassifier(): FieldClassifier = FieldClassifier()

    @Provides
    fun provideNeedsInputCollector(): NeedsInputCollector = NeedsInputCollector()

    @Provides
    fun provideFormNodeOperator(): FormNodeOperator = AccessibilityFormNodeOperator()

    @Provides
    fun provideFieldFiller(
        classifier: FieldClassifier,
        operator: FormNodeOperator,
        needsInputCollector: NeedsInputCollector
    ): FieldFiller = FieldFiller(
        classifier = classifier,
        operator = operator,
        needsInputCollector = needsInputCollector
    )

    @Provides
    @Singleton
    fun provideResumeUriProvider(@ApplicationContext context: Context): ResumeUriProvider =
        FileProviderResumeUriProvider(context)

    @Provides
    @Singleton
    fun provideResumeUploader(uriProvider: ResumeUriProvider): ResumeUploader =
        ResumeUploader(
            validator = ResumeFileValidator(),
            uriProvider = uriProvider
        )

    @Provides
    @Singleton
    fun providePostUploadVerifier(): PostUploadVerifier = PostUploadVerifier()

    @Provides
    @Singleton
    fun provideAccessibilitySession(): AccessibilitySession = AccessibilitySession()

    @Provides
    @Singleton
    fun provideConfirmationDetector(): ConfirmationDetector = ConfirmationDetector()

    @Provides
    @Singleton
    fun provideAccessibilityChecker(@ApplicationContext context: Context): com.cellclaw.accessibility.AccessibilityChecker =
        com.cellclaw.accessibility.AccessibilityChecker(context)

    @Provides
    @Singleton
    fun provideUrlValidator(): com.cellclaw.browser.UrlValidator = com.cellclaw.browser.UrlValidator()

    @Provides
    @Singleton
    fun provideBrowserOpener(
        @ApplicationContext context: Context,
        validator: com.cellclaw.browser.UrlValidator
    ): com.cellclaw.browser.BrowserOpener = com.cellclaw.browser.BrowserOpener(context, validator)

    @Provides
    @Singleton
    fun provideAnthropicProvider(): AnthropicProvider = AnthropicProvider()

    @Provides
    @Singleton
    fun provideOpenAIProvider(): OpenAIProvider = OpenAIProvider()

    @Provides
    @Singleton
    fun provideGeminiProvider(): GeminiProvider = GeminiProvider()

    @Provides
    @Singleton
    fun provideOpenRouterProvider(): OpenRouterProvider = OpenRouterProvider()

    @Provides
    @Singleton
    fun provideAiClient(
        providerManager: ProviderManager,
        geminiProvider: GeminiProvider,
        applicationTracker: com.cellclaw.tracker.ApplicationTracker
    ): com.cellclaw.ai.AiClient = com.cellclaw.ai.ResilientAiClient(
        providerManager = providerManager,
        geminiProvider = geminiProvider,
        applicationTracker = applicationTracker
    )

    @Provides
    @Singleton
    fun provideApprovedAnswersStore(@ApplicationContext context: Context): com.cellclaw.form.ApprovedAnswersStore =
        com.cellclaw.form.EncryptedSharedPreferencesApprovedAnswersStore(context)

    @Provides
    @Singleton
    fun provideApprovedAnswersLibrary(
        store: com.cellclaw.form.ApprovedAnswersStore
    ): com.cellclaw.form.ApprovedAnswersLibrary =
        com.cellclaw.form.DefaultApprovedAnswersLibrary(store)

    @Provides
    @Singleton
    fun provideAnswerDrafter(
        aiClient: com.cellclaw.ai.AiClient,
        answersLibrary: com.cellclaw.form.ApprovedAnswersLibrary
    ): com.cellclaw.form.AnswerDrafter = com.cellclaw.form.DefaultAnswerDrafter(aiClient, answersLibrary)

    @Provides
    @Singleton
    fun provideAiJdExtractor(
        aiClient: com.cellclaw.ai.AiClient
    ): com.cellclaw.jd.AiJdExtractor = com.cellclaw.jd.AiJdExtractor(aiClient = aiClient)

    @Provides
    @Singleton
    fun provideToolRegistry(
        // --- SAFE TOOLS (retained for Elixir placement profile) ---
        calendarQuery: CalendarQueryTool,
        calendarCreate: CalendarCreateTool,
        notification: NotificationTool,
        clipboardRead: ClipboardReadTool,
        clipboardWrite: ClipboardWriteTool,
        fileRead: FileReadTool,
        fileWrite: FileWriteTool,
        fileList: FileListTool,
        sensor: SensorTool,
        settings: SettingsTool,
        // browserOpen: BrowserOpenTool,   // DISABLED (S3): until M8.1 validated opener exists
        browserSearch: BrowserSearchTool,
        // appLaunch: AppLaunchTool,       // DISABLED (S2): app launch
        // appAutomate: AppAutomateTool,   // DISABLED (S2): app automate
        screenRead: ScreenReadTool,
        emailSend: EmailSendTool,
        screenCapture: ScreenCaptureTool,
        visionAnalyze: VisionAnalyzeTool,
        notificationListen: NotificationListenTool,
        schedulerTool: SchedulerTool,
        heartbeatContext: HeartbeatContextTool
        // --- DISABLED for Elixir profile (source files kept, not deleted) ---
        // smsRead: SmsReadTool,          // DISABLED: SMS read
        // smsSend: SmsSendTool,          // DISABLED: SMS send
        // phoneCall: PhoneCallTool,      // DISABLED: phone calls
        // phoneLog: PhoneLogTool,        // DISABLED: call log
        // contactsSearch: ContactsSearchTool, // DISABLED: contacts
        // contactsAdd: ContactsAddTool,  // DISABLED: contacts
        // cameraSnap: CameraSnapTool,    // DISABLED: camera
        // cameraRecord: CameraRecordTool,// DISABLED: camera
        // location: LocationTool,        // DISABLED: location
        // scriptExec: ScriptExecTool,    // DISABLED: shell execution (ProcessBuilder)
        // messagingOpen: MessagingOpenTool,   // DISABLED: messaging app
        // messagingRead: MessagingReadTool,   // DISABLED: messaging app
        // messagingReply: MessagingReplyTool, // DISABLED: messaging app
    ): ToolRegistry {
        return ToolRegistry().apply {
            register(
                // Calendar
                calendarQuery, calendarCreate,
                // Notification
                notification, notificationListen,
                // Clipboard
                clipboardRead, clipboardWrite,
                // File
                fileRead, fileWrite, fileList,
                // Sensor and settings
                sensor, settings,
                // Browser
                browserSearch,
                // Screen read
                screenRead,
                // Email
                emailSend,
                // Vision and capture
                screenCapture, visionAnalyze,
                // Scheduling and heartbeat
                schedulerTool, heartbeatContext
            )
        }
    }
}
