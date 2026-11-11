package cz.vcely.zapisnik

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import cz.vcely.zapisnik.speech.SpeechRecognizerManager
import cz.vcely.zapisnik.speech.VoiceCommand
import cz.vcely.zapisnik.ui.AppScreen
import cz.vcely.zapisnik.ui.InspectionViewModel
import cz.vcely.zapisnik.ui.VceliTheme
import cz.vcely.zapisnik.weather.MotyliceWorker
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : ComponentActivity() {

    private val viewModel: InspectionViewModel by viewModels()

    private lateinit var speech: SpeechRecognizerManager
    private lateinit var speechIntentLauncher: ActivityResultLauncher<Intent>
    private lateinit var micPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var smsPermissionLauncher: ActivityResultLauncher<String>
    private lateinit var backupSaveLauncher: ActivityResultLauncher<String>
    private lateinit var backupRestoreLauncher: ActivityResultLauncher<Array<String>>

    private var pendingPhotoFile: File? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        speech = SpeechRecognizerManager(
            context = this,
            onPartial = { viewModel.setPartial(it) },
            onFinal = { text ->
                if (text.isNotBlank()) {
                    viewModel.onTranscript(text, viewModel.lastHive.value.ifBlank { null })
                } else {
                    viewModel.setListening(false)
                    viewModel.cancelBulkMode()
                }
            },
            onError = { msg ->
                viewModel.setListening(false)
                viewModel.cancelBulkMode()
                viewModel.setPartial("")
                if (msg.isNotBlank()) toast(msg)
            },
            onCommand = { cmd ->
                viewModel.onVoiceCommand(cmd)
                vibrate()
                if (cmd == VoiceCommand.STOP) viewModel.setListening(false)
            }
        )

        // Fallback: systémové okno pro diktování.
        speechIntentLauncher = registerForActivityResult(
            ActivityResultContracts.StartActivityForResult()
        ) { result ->
            if (result.resultCode == RESULT_OK) {
                val text = result.data
                    ?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)
                    ?.firstOrNull()
                if (!text.isNullOrBlank()) {
                    viewModel.onTranscript(text, viewModel.lastHive.value.ifBlank { null })
                }
            }
        }

        micPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) startContinuous() else toast("Bez mikrofonu nelze diktovat.")
        }

        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { /* výsledek nepotřebujeme – poloha se doplní, když je povolená */ }

        cameraLauncher = registerForActivityResult(
            ActivityResultContracts.TakePicture()
        ) { success ->
            if (success) pendingPhotoFile?.let { viewModel.setPhoto(it.absolutePath) }
            else pendingPhotoFile?.delete()
        }

        smsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (!granted) {
                toast("Bez SMS oprávnění nebude appka moct upozornit na síření rámků.")
            }
        }

        // Záloha: uživatel si sám vybere, kam se ZIP uloží (Stažené, Disk…); obnova ho z něj načte.
        backupSaveLauncher = registerForActivityResult(
            ActivityResultContracts.CreateDocument("application/zip")
        ) { uri -> if (uri != null) viewModel.backupTo(uri) { toast(it) } }

        backupRestoreLauncher = registerForActivityResult(
            ActivityResultContracts.OpenDocument()
        ) { uri -> if (uri != null) viewModel.restoreFrom(uri) { toast(it) } }

        // Hlídání motýlice: naplánujeme denní kontrolu na pozadí (pokud už neběží)
        // a vyžádáme si oprávnění k odesílání SMS.
        WorkManager.getInstance(this).enqueueUniquePeriodicWork(
            MotyliceWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<MotyliceWorker>(
                MotyliceWorker.REPEAT_INTERVAL, MotyliceWorker.REPEAT_UNIT
            ).build()
        )
        if (!hasPermission(Manifest.permission.SEND_SMS)) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
        }

        // O polohu požádáme jednou při startu (nenásilně).
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            locationPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.ACCESS_FINE_LOCATION,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                )
            )
        }

        setContent {
            VceliTheme {
                AppScreen(
                    viewModel = viewModel,
                    onToggleMic = { toggleMic() },
                    onCapturePhoto = { capturePhoto() },
                    onExport = { viewModel.exportCsv { file -> shareCsv(file) } },
                    onBackupSave = {
                        val stamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale("cs")).format(Date())
                        backupSaveLauncher.launch("vceli_zapisnik_zaloha_$stamp.zip")
                    },
                    onBackupRestore = {
                        backupRestoreLauncher.launch(arrayOf("application/zip", "application/octet-stream"))
                    },
                    onBulkDictate = { startBulkDictation(it) }
                )
            }
        }
    }

    override fun onDestroy() {
        speech.destroy()
        super.onDestroy()
    }

    // --- Diktát ---
    private fun toggleMic() {
        if (viewModel.isListening.value) {
            speech.stop()
            return
        }
        // Nejdřív zjistíme/potvrdíme stanoviště podle GPS, teprve pak spustíme mikrofon.
        viewModel.cancelBulkMode() // běžný diktát nesmí zůstat v hromadném režimu
        viewModel.beginDictation(this) { startMicAfterStanoviste() }
    }

    /** Hromadný diktát pro všechny úly vybraného stanoviště (stanoviště se nehledá podle GPS). */
    private fun startBulkDictation(stanovisteName: String) {
        if (viewModel.isListening.value || viewModel.draft.value != null) {
            toast("Dokončete nejdřív rozdělaný záznam nebo diktát.")
            return
        }
        viewModel.beginBulk(stanovisteName)
        startMicAfterStanoviste()
    }

    private fun startMicAfterStanoviste() {
        if (!SpeechRecognizerManager.isAvailable(this)) {
            launchSpeechIntent()
            return
        }
        if (hasPermission(Manifest.permission.RECORD_AUDIO)) startContinuous()
        else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    private fun startContinuous() {
        viewModel.setPartial("")
        viewModel.setListening(true)
        speech.start()
    }

    private fun launchSpeechIntent() {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "cs-CZ")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Nadiktujte prohlídku úlu")
        }
        try {
            speechIntentLauncher.launch(intent)
        } catch (e: Exception) {
            toast("Rozpoznávání řeči není dostupné.")
        }
    }

    // --- Fotka ---
    private fun capturePhoto() {
        val dir = File(filesDir, "photos").apply { mkdirs() }
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale("cs")).format(Date())
        val file = File(dir, "IMG_$stamp.jpg")
        pendingPhotoFile = file
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        try {
            cameraLauncher.launch(uri)
        } catch (e: Exception) {
            toast("Fotoaparát není dostupný.")
        }
    }

    // --- Export ---
    private fun shareCsv(file: File) {
        val uri = FileProvider.getUriForFile(this, "$packageName.fileprovider", file)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        startActivity(Intent.createChooser(intent, "Exportovat CSV"))
    }

    private fun hasPermission(permission: String): Boolean =
        ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_LONG).show()

    // --- Krátká vibrace jako potvrzení hlasového příkazu ("api pauza" / "api diktuji" / "api stop") ---
    private fun vibrate(ms: Long = 40) {
        val vibrator = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
    }
}
