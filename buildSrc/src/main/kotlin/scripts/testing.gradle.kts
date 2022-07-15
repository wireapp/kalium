package scripts

import OnlyAffectedTestTask

OnlyAffectedTestTask.TestTaskConfiguration.values().forEach {
    project.tasks.register(it.taskName, OnlyAffectedTestTask::class) { targetTestTask = it.testTarget }
}
