package com.cellclaw.di

import android.content.Context
import androidx.room.Room
import com.cellclaw.memory.MemoryDb
import com.cellclaw.memory.MemoryFactDao
import com.cellclaw.memory.MessageDao
import com.cellclaw.provider.AnthropicProvider
import com.cellclaw.provider.GeminiProvider
import com.cellclaw.provider.OpenAIProvider
import com.cellclaw.provider.OpenRouterProvider
import com.cellclaw.tools.*
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
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideMessageDao(db: MemoryDb): MessageDao = db.messageDao()

    @Provides
    fun provideMemoryFactDao(db: MemoryDb): MemoryFactDao = db.memoryFactDao()

    @Provides
    fun provideScheduledTaskDao(db: MemoryDb): com.cellclaw.scheduler.ScheduledTaskDao = db.scheduledTaskDao()

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
        browserOpen: BrowserOpenTool,
        browserSearch: BrowserSearchTool,
        appLaunch: AppLaunchTool,
        appAutomate: AppAutomateTool,
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
                browserOpen, browserSearch,
                // App control and screen
                appLaunch, appAutomate, screenRead,
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
