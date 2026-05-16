package ru.openide.reatom.analyzer

import com.google.gson.Gson
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.CapturingProcessHandler
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.project.Project
import ru.openide.reatom.model.ReatomGraph

/**
 * Проектный сервис: хранит модель реактивного графа и умеет её перестраивать,
 * запуская анализатор `@openide/reatom-ts-plugin` отдельным Node-процессом
 * (вариант 2a гибридной архитектуры). Code Lens и gutter-иконки читают
 * модель отсюда.
 */
@Service(Service.Level.PROJECT)
class ReatomGraphService(private val project: Project) {

    @Volatile
    var graph: ReatomGraph? = null
        private set

    @Volatile
    private var loading = false

    /** Перестраивает граф в фоне; по завершении обновляет UI на EDT. */
    fun reloadAsync() {
        if (loading) return
        loading = true
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val built = runAnalyzer()
                if (built != null) {
                    graph = built
                    ApplicationManager.getApplication().invokeLater {
                        ReatomGraphRefresher.refreshAll(project)
                    }
                }
            } finally {
                loading = false
            }
        }
    }

    /** Для тестов: задать модель напрямую, минуя запуск Node. */
    fun setGraphForTesting(value: ReatomGraph?) {
        graph = value
    }

    private fun runAnalyzer(): ReatomGraph? {
        val locations = ReatomAnalyzerLocator.locate(project) ?: run {
            thisLogger().info("Reatom: анализатор или tsconfig не найдены — граф не построен")
            return null
        }
        return try {
            val commandLine = GeneralCommandLine(
                locations.node.path,
                locations.analyzerCli.path,
                "--project",
                locations.tsconfig.path,
            ).withWorkDirectory(locations.tsconfig.parent)
            val output = CapturingProcessHandler(commandLine).runProcess(ANALYZER_TIMEOUT_MS)
            if (output.exitCode != 0) {
                thisLogger().warn(
                    "Reatom: анализатор вернул код ${output.exitCode}: ${output.stderr}",
                )
                return null
            }
            Gson().fromJson(output.stdout, ReatomGraph::class.java)
        } catch (e: Exception) {
            thisLogger().warn("Reatom: не удалось построить граф", e)
            null
        }
    }

    companion object {
        private const val ANALYZER_TIMEOUT_MS = 60_000

        fun getInstance(project: Project): ReatomGraphService = project.service()
    }
}
