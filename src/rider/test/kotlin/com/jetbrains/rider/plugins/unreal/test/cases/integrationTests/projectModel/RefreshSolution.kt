package com.jetbrains.rider.plugins.unreal.test.cases.integrationTests.projectModel

import com.jetbrains.rd.ide.model.UnrealEngine
import com.jetbrains.rd.util.reactive.fire
import com.jetbrains.rdclient.util.idea.waitAndPump
import com.jetbrains.rider.ideaInterop.vfs.VfsWriteOperationsHost
import com.jetbrains.rider.model.RdFsRefreshRequest
import com.jetbrains.rider.plugins.unreal.model.frontendBackend.rdRiderModel
import com.jetbrains.rider.projectView.solution
import com.jetbrains.rider.test.annotations.TestEnvironment
import com.jetbrains.rider.test.contexts.ProjectModelTestContext
import com.jetbrains.rider.test.contexts.UnrealTestContext
import com.jetbrains.rider.test.enums.PlatformType
import com.jetbrains.rider.test.env.enums.BuildTool
import com.jetbrains.rider.test.env.enums.SdkVersion
import com.jetbrains.rider.test.framework.combine
import com.jetbrains.rider.test.framework.testData.TestDataStorage
import com.jetbrains.rider.test.framework.waitBackendAndWorkspaceModel
import com.jetbrains.rider.test.scriptingApi.experimental.ProjectModelExp.dumpAfterAction
import com.jetbrains.rider.test.scriptingApi.experimental.ProjectModelExp.withDump
import com.jetbrains.rider.test.scriptingApi.waitPumping
import com.jetbrains.rider.test.unreal.UnrealTestLevelProject
import io.qameta.allure.Epic
import io.qameta.allure.Feature
import org.testng.annotations.BeforeMethod
import org.testng.annotations.Test
import java.time.Duration

@Epic("UnrealLink")
@Feature("Refresh Solution")
@TestEnvironment(
  platform = [PlatformType.WINDOWS_X64],
  buildTool = BuildTool.CPP,
  sdkVersion = SdkVersion.AUTODETECT
)
class RefreshSolution : UnrealTestLevelProject() {
  init {
    projectDirectoryName = "EmptyUProject"
  }

  @BeforeMethod
  fun updateDumpProfile() {
    contexts.get<ProjectModelTestContext>().profile.dumpDirList.clear()
    contexts.get<ProjectModelTestContext>().profile.dumpDirList.add(activeSolutionDirectory.resolve("Intermediate/ProjectFiles"))
    contexts.get<ProjectModelTestContext>().profile.fileNames.add("$activeSolution.vcxproj.filters")
  }

  @Test(dataProvider = "AllEngines_slnOnly")
  fun refreshSolution(@Suppress("UNUSED_PARAMETER") caseName: String,
                      openWith: UnrealTestContext.UnrealProjectModelType, engine: UnrealEngine) {
    val pmContext = contexts.get<ProjectModelTestContext>()

    withDump(contexts) {
      dumpAfterAction("Init", pmContext) {}
      dumpAfterAction("Copy TestPlugin to project", pmContext) {
        TestDataStorage.defaultTestDataDirectory.combine("additionalSource", "EmptyTestPlugin")
          .copyRecursively(activeSolutionDirectory.resolve("Plugins").resolve("EmptyTestPlugin"))
       }

      dumpAfterAction("Invoking refresh solution", pmContext) {
        project.solution.rdRiderModel.refreshProjects.fire()
        waitPumping(Duration.ofSeconds(1))
        waitAndPump(Duration.ofSeconds(30),
                    { !project.solution.rdRiderModel.refreshInProgress.value },
                    { "Response from UBT took longer than expected time" })

        VfsWriteOperationsHost.getInstance(project).refreshPaths(
          RdFsRefreshRequest(listOf(activeSolutionDirectory.combine("Intermediate", "ProjectFiles", "$activeSolution.vcxproj").path), false)
        )
        waitPumping(Duration.ofSeconds(1))
        waitBackendAndWorkspaceModel(project)
      }
    }
  }
}