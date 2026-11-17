package com.khayom1.missminutes.ui

import com.khayom1.missminutes.agent.AgentNodeStatus
import com.khayom1.missminutes.agent.AgentTaskStatus

internal fun taskStatusLabel(status: AgentTaskStatus, language: AppLanguage): String =
    when (language) {
        AppLanguage.English -> when (status) {
            AgentTaskStatus.READY -> "Ready"
            AgentTaskStatus.PAUSED -> "Paused"
            AgentTaskStatus.WAITING_APPROVAL -> "Waiting for approval"
            AgentTaskStatus.RUNNING -> "Running"
            AgentTaskStatus.SUCCEEDED -> "Completed"
            AgentTaskStatus.FAILED -> "Failed"
            AgentTaskStatus.CANCELLED -> "Cancelled"
            AgentTaskStatus.EXPIRED -> "Expired"
            AgentTaskStatus.RECOVERY_REQUIRED -> "Recovery required"
        }
        AppLanguage.Russian -> when (status) {
            AgentTaskStatus.READY -> "Готово к запуску"
            AgentTaskStatus.PAUSED -> "Приостановлено"
            AgentTaskStatus.WAITING_APPROVAL -> "Ожидает подтверждения"
            AgentTaskStatus.RUNNING -> "Выполняется"
            AgentTaskStatus.SUCCEEDED -> "Завершено"
            AgentTaskStatus.FAILED -> "Ошибка"
            AgentTaskStatus.CANCELLED -> "Отменено"
            AgentTaskStatus.EXPIRED -> "Срок истёк"
            AgentTaskStatus.RECOVERY_REQUIRED -> "Требуется проверка"
        }
        AppLanguage.Tajik -> when (status) {
            AgentTaskStatus.READY -> "Омода"
            AgentTaskStatus.PAUSED -> "Таваққуфшуда"
            AgentTaskStatus.WAITING_APPROVAL -> "Интизори тасдиқ"
            AgentTaskStatus.RUNNING -> "Иҷро шуда истодааст"
            AgentTaskStatus.SUCCEEDED -> "Анҷом ёфт"
            AgentTaskStatus.FAILED -> "Хато"
            AgentTaskStatus.CANCELLED -> "Бекор карда шуд"
            AgentTaskStatus.EXPIRED -> "Муҳлат гузашт"
            AgentTaskStatus.RECOVERY_REQUIRED -> "Санҷиши дастӣ лозим"
        }
    }

internal fun nodeStatusLabel(status: AgentNodeStatus, language: AppLanguage): String =
    when (language) {
        AppLanguage.English -> when (status) {
            AgentNodeStatus.PENDING -> "Pending"
            AgentNodeStatus.WAITING_APPROVAL -> "Waiting for approval"
            AgentNodeStatus.RUNNING -> "Running"
            AgentNodeStatus.SUCCEEDED -> "Completed"
            AgentNodeStatus.FAILED -> "Failed"
            AgentNodeStatus.CANCELLED -> "Cancelled"
            AgentNodeStatus.RECOVERY_REQUIRED -> "Recovery required"
        }
        AppLanguage.Russian -> when (status) {
            AgentNodeStatus.PENDING -> "Ожидает"
            AgentNodeStatus.WAITING_APPROVAL -> "Ожидает подтверждения"
            AgentNodeStatus.RUNNING -> "Выполняется"
            AgentNodeStatus.SUCCEEDED -> "Завершено"
            AgentNodeStatus.FAILED -> "Ошибка"
            AgentNodeStatus.CANCELLED -> "Отменено"
            AgentNodeStatus.RECOVERY_REQUIRED -> "Требуется проверка"
        }
        AppLanguage.Tajik -> when (status) {
            AgentNodeStatus.PENDING -> "Дар навбат"
            AgentNodeStatus.WAITING_APPROVAL -> "Интизори тасдиқ"
            AgentNodeStatus.RUNNING -> "Иҷро мешавад"
            AgentNodeStatus.SUCCEEDED -> "Анҷом ёфт"
            AgentNodeStatus.FAILED -> "Хато"
            AgentNodeStatus.CANCELLED -> "Бекор шуд"
            AgentNodeStatus.RECOVERY_REQUIRED -> "Санҷиши дастӣ лозим"
        }
    }
