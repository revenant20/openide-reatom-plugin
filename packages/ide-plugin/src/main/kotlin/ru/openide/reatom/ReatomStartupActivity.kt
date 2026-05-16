package ru.openide.reatom

import com.intellij.openapi.project.Project
import com.intellij.openapi.startup.ProjectActivity
import ru.openide.reatom.analyzer.ReatomGraphService

/** На открытие проекта строит реактивный граф в фоне. */
class ReatomStartupActivity : ProjectActivity {

    override suspend fun execute(project: Project) {
        ReatomGraphService.getInstance(project).reloadAsync()
    }
}
