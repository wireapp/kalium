package scripts

import OnlyAffectedTestTask

OnlyAffectedTestTask.TestTaskConfiguration.values().forEach {
    tasks.register(it.taskName, OnlyAffectedTestTask::class) { targetTestTask = it.testTarget }
}
