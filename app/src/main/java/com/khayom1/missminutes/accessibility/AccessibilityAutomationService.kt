package com.khayom1.missminutes.accessibility

import android.accessibilityservice.AccessibilityService
import android.content.ComponentName
import android.content.Context
import android.graphics.Rect
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.util.ArrayDeque
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

/**
 * User-enabled, cross-app accessibility bridge.
 *
 * It reads the active window only when an explicit assistant tool call asks it
 * to do so. Accessibility events are deliberately not collected or retained.
 */
class AccessibilityAutomationService : AccessibilityService() {
    override fun onServiceConnected() {
        super.onServiceConnected()
        connectedService = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        // No passive screen scraping. The active window is inspected on demand.
    }

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (connectedService === this) connectedService = null
        super.onDestroy()
    }

    private fun inspectActiveWindow(): String {
        val root = activeRoot() ?: return failure("No accessible foreground app window is available.")
        val packageName = root.packageName?.toString().orEmpty()
        val output = StringBuilder()
        output.append("Foreground application: ").append(packageName).append('\n')
        var included = 0
        for (node in collectNodes(root)) {
            if (included >= MAX_OUTPUT_NODES) break
            val type = node.className?.toString()?.substringAfterLast('.') ?: "View"
            val text = if (node.isPassword) "[redacted]" else redactSensitiveScreenText(node.text?.toString()?.trim().orEmpty())
            val description = if (node.isPassword) "" else redactSensitiveScreenText(node.contentDescription?.toString()?.trim().orEmpty())
            val hint = if (node.isPassword) "" else redactSensitiveScreenText(node.hintText?.toString()?.trim().orEmpty())
            if (text.isBlank() && description.isBlank() && hint.isBlank() &&
                !node.isEditable && !node.isClickable && !node.isScrollable
            ) continue

            output.append('[').append(type).append(']')
            if (text.isNotBlank()) output.append(" text=").append(text)
            if (description.isNotBlank() && description != text) output.append(" description=").append(description)
            if (hint.isNotBlank() && hint != text && hint != description) output.append(" hint=").append(hint)
            if (node.isEditable) output.append(" editable")
            if (node.isFocused) output.append(" focused")
            if (node.isClickable) output.append(" clickable")
            if (node.isScrollable) output.append(" scrollable")
            output.append('\n')
            included++
            if (output.length >= MAX_SCREEN_CHARACTERS) break
        }
        if (included == 0) output.append("No readable controls were exposed by this app.")
        return success(output.toString().take(MAX_SCREEN_CHARACTERS))
    }

    private fun clickVisibleText(requestedText: String, allowDangerousAction: Boolean): String {
        val query = requestedText.trim()
        if (query.isEmpty() || query.length > MAX_LABEL_CHARACTERS) {
            return failure("The visible control label is invalid.")
        }
        val root = activeRoot() ?: return failure("No accessible foreground app window is available.")
        val allNodes = collectNodes(root).filter { it.isVisibleToUser }
        val highRiskContext = allNodes.asSequence()
            .flatMap { nodeLabels(it).asSequence() }
            .firstOrNull(AccessibilityAutomationPolicy::isHighRiskControlLabel)
        val directRisk = AccessibilityAutomationPolicy.isHighRiskControlLabel(query)
        val contextualRisk = AccessibilityAutomationPolicy.isConfirmationControlLabel(query) &&
            !highRiskContext.isNullOrBlank()
        val isDangerous = directRisk || contextualRisk || allowDangerousAction
        val reason = when {
            directRisk -> query
            !highRiskContext.isNullOrBlank() -> highRiskContext
            else -> null
        }
        if (isDangerous && !allowDangerousAction) {
            val riskReason = reason ?: query
            val packageName = root.packageName?.toString().orEmpty()
            val saved = packageName.isNotBlank() &&
                PendingDangerousActionStore.save(this, query, riskReason, packageName)
            return if (saved) {
                "CONFIRMATION_REQUIRED: " + riskReason +
                    " may be destructive, financial, or affect account security. A separate confirmation is required before clicking " +
                    query + "."
            } else {
                failure("This control is high risk and was blocked. The confirmation request could not be stored.")
            }
        }
        if (allowDangerousAction) {
            val packageName = root.packageName?.toString().orEmpty()
            if (reason.isNullOrBlank() || !PendingDangerousActionStore.consumeIfCurrentContext(
                    this,
                    query,
                    packageName,
                    reason
                )
            ) {
                return failure("The pending dangerous-action approval does not match this app screen or visible control.")
            }
        }
        val normalizedQuery = AccessibilityAutomationPolicy.normalize(query)
        val nodes = allNodes.filter { node ->
            !node.isPassword && nodeMatches(node, normalizedQuery)
        }
        val exact = nodes.filter { node ->
            nodeLabels(node).any { AccessibilityAutomationPolicy.normalize(it) == normalizedQuery }
        }
        val matching = if (exact.isNotEmpty()) exact else nodes
        val clickTargets = matching.mapNotNull(::clickableAncestor)
            .distinctBy(::boundsKey)
        if (clickTargets.isEmpty()) return failure("No clickable control matched the requested visible label.")
        if (clickTargets.size != 1) return failure("More than one visible control matched. Use a more specific label.")
        val clicked = clickTargets.single().performAction(AccessibilityNodeInfo.ACTION_CLICK)
        return if (clicked) success("Clicked the visible control: " + query)
        else failure("Android did not accept the click action.")
    }

    private fun enterText(requestedText: String, requestedTarget: String?): String {
        val text = requestedText
        if (text.isBlank() || text.length > MAX_TYPED_CHARACTERS) {
            return failure("Text must contain between 1 and " + MAX_TYPED_CHARACTERS + " characters.")
        }
        val root = activeRoot() ?: return failure("No accessible foreground app window is available.")
        val target = requestedTarget?.trim().orEmpty()
        val editable = if (target.isBlank()) {
            listOfNotNull(root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT))
                .filter { it.isEditable }
        } else {
            val normalizedTarget = AccessibilityAutomationPolicy.normalize(target)
            collectNodes(root).filter { node ->
                node.isEditable && node.isVisibleToUser &&
                    nodeLabels(node).any { AccessibilityAutomationPolicy.normalize(it) == normalizedTarget }
            }
        }
        if (editable.isEmpty()) return failure("No matching focused or labeled text field is available.")
        if (editable.size != 1) return failure("More than one editable field matched. Specify the field label.")
        val field = editable.single()
        if (field.isPassword) return failure("Password fields are not accessible to the assistant.")
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val arguments = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val changed = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
        return if (changed) success("Entered text into the selected field.")
        else failure("The app rejected text entry in that field.")
    }

    private fun scrollVisibleContent(direction: String): String {
        val root = activeRoot() ?: return failure("No accessible foreground app window is available.")
        val scrollable = collectNodes(root).firstOrNull { it.isScrollable }
            ?: return failure("The foreground app exposes no scrollable control.")
        val action = when (direction) {
            "down" -> AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
            "up" -> AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
            else -> return failure("Scroll direction must be up or down.")
        }
        return if (scrollable.performAction(action)) success("Scrolled " + direction + ".")
        else failure("The app did not accept the scroll action.")
    }

    private fun redactSensitiveScreenText(value: String): String {
        if (value.isBlank()) return value
        val labeledCode = Regex(
            """(?i)((?:otp|one[- ]time|verification|security|authentication)\s*(?:code|passcode)?|код\s*(?:подтверждения|безопасности)|одноразовый\s+код)(\s*[:#-]?\s*)\d{4,8}"""
        )
        val cardNumber = Regex("""(?<!\d)(?:\d[ -]?){13,19}(?!\d)""")
        return cardNumber.replace(labeledCode.replace(value) { match ->
            match.groupValues[1] + match.groupValues[2] + "[redacted]"
        }, "[payment number redacted]")
    }

    private fun activeRoot(): AccessibilityNodeInfo? =
        rootInActiveWindow?.takeIf { it.isVisibleToUser }

    private fun collectNodes(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val nodes = ArrayList<AccessibilityNodeInfo>(MAX_TRAVERSED_NODES)
        val queue = ArrayDeque<Pair<AccessibilityNodeInfo, Int>>()
        queue.addLast(root to 0)
        while (queue.isNotEmpty() && nodes.size < MAX_TRAVERSED_NODES) {
            val entry = queue.removeFirst()
            val node = entry.first
            val depth = entry.second
            if (node.isVisibleToUser) nodes += node
            if (depth >= MAX_TREE_DEPTH) continue
            for (index in 0 until node.childCount) {
                val child = node.getChild(index) ?: continue
                queue.addLast(child to depth + 1)
                if (queue.size + nodes.size >= MAX_TRAVERSED_NODES) break
            }
        }
        return nodes
    }

    private fun nodeLabels(node: AccessibilityNodeInfo): List<String> = buildList {
        node.text?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        node.contentDescription?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(::add)
        node.hintText?.toString()?.trim()?.takeIf(String::isNotBlank)?.let(::add)
    }

    private fun nodeMatches(node: AccessibilityNodeInfo, query: String): Boolean =
        nodeLabels(node).any { label ->
            val normalized = AccessibilityAutomationPolicy.normalize(label)
            normalized == query || normalized.contains(query)
        }

    private fun clickableAncestor(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        var current: AccessibilityNodeInfo? = node
        repeat(MAX_CLICK_ANCESTORS) {
            val candidate = current ?: return null
            if (candidate.isClickable && candidate.isVisibleToUser) return candidate
            current = candidate.parent
        }
        return null
    }

    private fun boundsKey(node: AccessibilityNodeInfo): String {
        val bounds = Rect()
        node.getBoundsInScreen(bounds)
        return bounds.toShortString()
    }

    companion object {
        private const val MAX_TRAVERSED_NODES = 160
        private const val MAX_OUTPUT_NODES = 90
        private const val MAX_TREE_DEPTH = 18
        private const val MAX_CLICK_ANCESTORS = 6
        private const val MAX_SCREEN_CHARACTERS = 6_000
        private const val MAX_TYPED_CHARACTERS = 4_000
        private const val MAX_LABEL_CHARACTERS = 128
        private const val SERVICE_CALL_TIMEOUT_MS = 3_000L
        private val mainHandler = Handler(Looper.getMainLooper())

        @Volatile
        private var connectedService: AccessibilityAutomationService? = null

        fun isConnected(): Boolean = connectedService != null

        fun currentForegroundPackage(): String? {
            val result = invokeService { service ->
                val packageName = service.activeRoot()?.packageName?.toString().orEmpty()
                if (packageName.isBlank()) failure("No foreground package is available.")
                else success(packageName)
            }
            return result.takeIf { it.startsWith("OK:") }?.removePrefix("OK:")?.trim()
        }

        fun isServiceEnabled(context: Context): Boolean {
            val appContext = context.applicationContext
            val accessibilityEnabled = Settings.Secure.getInt(
                appContext.contentResolver,
                Settings.Secure.ACCESSIBILITY_ENABLED,
                0
            ) == 1
            if (!accessibilityEnabled) return false
            val expected = ComponentName(appContext, AccessibilityAutomationService::class.java)
            val services = Settings.Secure.getString(
                appContext.contentResolver,
                Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            ).orEmpty()
            return services.split(':').any { flattened ->
                ComponentName.unflattenFromString(flattened) == expected
            }
        }

        fun inspectScreen(): String = invokeService { it.inspectActiveWindow() }

        fun clickText(text: String, allowDangerousAction: Boolean = false): String =
            invokeService { it.clickVisibleText(text, allowDangerousAction) }

        fun typeText(text: String, target: String?): String =
            invokeService { it.enterText(text, target) }

        fun scrollScreen(direction: String): String =
            invokeService { it.scrollVisibleContent(direction) }

        fun pressBack(): String = invokeService { service ->
            if (service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK)) success("Pressed Back.")
            else failure("Android did not accept the Back action.")
        }

        private fun invokeService(action: (AccessibilityAutomationService) -> String): String {
            val service = connectedService ?: return failure("Accessibility service is not connected.")
            if (Looper.myLooper() == Looper.getMainLooper()) {
                return runCatching { action(service) }.getOrElse {
                    failure("The foreground app could not be controlled safely.")
                }
            }
            val result = AtomicReference<String?>()
            val latch = CountDownLatch(1)
            val posted = mainHandler.post {
                result.set(
                    runCatching { action(service) }.getOrElse {
                        failure("The foreground app could not be controlled safely.")
                    }
                )
                latch.countDown()
            }
            if (!posted) return failure("Android's UI automation thread is unavailable.")
            if (!latch.await(SERVICE_CALL_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return failure("The foreground app did not respond before the automation timeout.")
            }
            return result.get() ?: failure("The accessibility action returned no result.")
        }

        private fun success(message: String): String = "OK: " + message

        private fun failure(message: String): String = "ERROR: " + message
    }
}

/** Conservative guard for destructive actions and normalized visible labels. */
object AccessibilityAutomationPolicy {
    private val dangerousLabels = listOf(
        "delete", "remove", "erase", "wipe", "factory reset", "reset device",
        "purchase", "buy now", "payment", "pay now", "send money", "transfer money",
        "withdraw", "subscribe", "unsubscribe", "cancel subscription", "sign out",
        "log out", "logout", "deactivate account", "change password", "revoke access",
        "place order", "checkout", "start trial", "start free trial", "donate",
        "authorize payment", "grant access", "allow access", "permission",
        "access contacts", "access location", "access camera", "access microphone",
        "удалить", "стереть", "сброс настроек", "стереть данные", "удалить данные",
        "оплатить", "платёж", "платеж", "купить", "покупка", "перевести деньги",
        "перевод средств", "вывести средства", "снять деньги", "отменить подписку",
        "выйти из аккаунта", "деактивировать", "сменить пароль", "изменить пароль",
        "разрешить доступ", "предоставить доступ", "разрешения приложения",
        "нест кардан", "пок кардан", "пардохт", "интиқоли пул", "фиристодани пул",
        "пул фиристодан", "баромадан аз ҳисоб", "иваз кардани рамз",
        "иҷозати дастрасӣ", "дастрасӣ ба тамосҳо", "дастрасӣ ба макон",
        "дастрасӣ ба камера", "дастрасӣ ба микрофон"
    )

    fun normalize(value: String): String = value
        .lowercase()
        .replace(Regex("[^\\p{L}\\p{N}]+"), " ")
        .trim()
        .replace(Regex("\\s+"), " ")

    fun isHighRiskControlLabel(label: String): Boolean {
        val normalized = normalize(label)
        return dangerousLabels.any { normalized.contains(normalize(it)) }
    }

    fun isConfirmationControlLabel(label: String): Boolean =
        normalize(label) in setOf(
            "confirm", "continue", "proceed", "ok", "yes", "submit", "allow", "grant", "accept",
            "подтвердить", "продолжить", "да", "ок", "подтверждаю", "разрешить", "предоставить", "принять",
            "тасдиқ", "идома", "ҳа", "иҷозат", "розӣ"
        )

    fun isExplicitDangerousActionConfirmation(command: String, pendingReason: String): Boolean {
        val normalizedCommand = normalize(command)
        if (normalizedCommand.isBlank() || normalize(pendingReason).isBlank()) return false
        val explicitPhrases = listOf(
            "i confirm", "yes i confirm", "yes confirm", "confirm",
            "подтверждаю", "да подтверждаю", "подтверждаю действие",
            "тасдиқ мекунам", "тасдик мекунам", "ҳа тасдиқ", "ҳа тасдик"
        )
        val explicit = explicitPhrases.any { phrase ->
            normalizedCommand == phrase || normalizedCommand.startsWith(phrase + " ")
        }
        return explicit || normalizedCommand in setOf("yes", "да", "ҳа", "ха")
    }
}
