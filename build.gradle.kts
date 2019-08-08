import com.jetbrains.rd.generator.gradle.RdgenParams
import com.jetbrains.rd.generator.gradle.RdgenTask
import org.apache.tools.ant.taskdefs.condition.Os
import org.jetbrains.intellij.tasks.*
import org.jetbrains.kotlin.gradle.tasks.KotlinCompile
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Paths

buildscript {
    repositories {
        mavenLocal()
        maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
        maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
        maven { setUrl("https://cache-redirector.jetbrains.com/dl.bintray.com/kotlin/kotlin-eap") }
        maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
        maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
//    maven { setUrl("https://cache-redirector.jetbrains.com/intellij-repository/snapshots") }
//    maven { setUrl("https://repo.labs.intellij.net/central-proxy") }
//    maven { setUrl("https://cache-redirector.jetbrains.com/myget.org.rd-snapshots.maven") }
        maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
    }

    dependencies {
        classpath(BuildPlugins.gradleIntellijPlugin)
        classpath(BuildPlugins.rdGenPlugin)
    }
}

plugins {
    id("java")
    id(Libraries.gradleIntellijPluginId)
    kotlin("jvm") version kotlinVersion
}

dependencies {
    compile("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
}

val repoRoot = project.rootDir
//val rdLibDirectory = File(repoRoot, "build/riderRD-$sdkVersion-SNAPSHOT/lib/rd")
val rdLibDirectory = File("C:\\Work\\riderRD-2019.2-SNAPSHOT\\lib\\rd")
//val reSharperHostSdkDirectory = File(repoRoot, "build/riderRD-$sdkVersion-SNAPSHOT/lib/ReSharperHostSdk")
val sdkDirectory = File("C:\\Work\\JetBrains.Rider-2019.2-EAP7D-192.5895.894.Checked.win")
val reSharperHostSdkDirectory = File(sdkDirectory, "lib\\ReSharperHost")

val dotNetDir = File(repoRoot, "src/dotnet")
val dotNetSolutionId = "resharper_unreal"
val dotNetRootId = "ReSharperPlugin"
val dotNetPluginId = "$dotNetRootId.UnrealEditor"

extra.apply {
    set("repoRoot", repoRoot)
    set("isWindows", Os.isFamily(Os.FAMILY_WINDOWS))
    set("sdkVersion", sdkVersion)
    set("rdLibDirectory", rdLibDirectory)
    set("reSharperHostSdkDirectory", reSharperHostSdkDirectory)
    set("dotNetDir", dotNetDir)
    set("pluginPropsFile", File(dotNetDir, "Plugin.props"))
    set("dotNetRootId", dotNetRootId)
    set("dotNetPluginId", dotNetPluginId)
    set("dotNetSolutionId", dotNetSolutionId)
    set("dotnetSolution", File(repoRoot, "$dotNetSolutionId.sln"))
}

repositories {
    maven { setUrl("https://cache-redirector.jetbrains.com/www.myget.org/F/rd-snapshots/maven") }
    maven { setUrl("https://cache-redirector.jetbrains.com/maven-central") }
    maven { setUrl("https://cache-redirector.jetbrains.com/plugins.gradle.org") }
//  maven { url "https://repo.labs.intellij.net/jitpack.io" }
//  mavenLocal()
    flatDir { dirs(rdLibDirectory.absolutePath) }
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks {
    wrapper {
        gradleVersion = "5.0"
        distributionType = Wrapper.DistributionType.ALL
        distributionUrl = "https://cache-redirector.jetbrains.com/services.gradle.org/distributions/gradle-$gradleVersion-all.zip"
    }
}

val buildConfiguration = (ext.properties.getOrPut("BuildConfiguration") { "Release" } as String)

project.version = (ext.properties.getOrPut("pluginVersion") { "1.3.3.7" } as String)

tasks {
    withType<PublishTask> {
        if (project.extra.has("username"))
            setUsername(ext["username"] as String)
        if (project.extra.has("password"))
            setPassword(ext["password"] as String)
    }
//    val version = "11_0_2b159"
//    val version = "11_0_3b304"
    val f = File(sdkDirectory, "jbr/bin/java.exe")
    withType<RunIdeTask> {
        //        setJbrVersion(version)
        setExecutable(f)
    }
    withType<BuildSearchableOptionsTask> {
        //        setJbrVersion(version)
        setExecutable(f)
    }

}

the<JavaPluginConvention>().sourceSets {
    "main" {
        java {
            srcDir("src/rider/main/kotlin")
        }
        resources {
            srcDir("src/rider/main/resources")
        }
    }
}

tasks.withType<KotlinCompile> {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}

tasks {
    create("findMsBuild") {
        doLast {
            val stdout = ByteArrayOutputStream()

            val hostOs = System.getProperty("os.name")
            val isWindows by extra(hostOs.startsWith("Windows"))
            if (isWindows) {
                extra["executable"] = "C:\\Program Files (x86)\\Microsoft Visual Studio\\2019\\BuildTools\\MSBuild\\Current\\Bin\\MSBuild.exe"
            } else {
                exec {
                    executable = "which"
                    args = listOf("msbuild")
                    standardOutput = stdout
                    workingDir = project.rootDir
                }
                extra["executable"] = stdout.toString().trim()
            }
        }
    }

    create("patchPropsFile") {
        doLast {
            val version = File(reSharperHostSdkDirectory, "DeploymentPackagingIdentity.txt").bufferedReader().readLine()

            (project.extra["pluginPropsFile"] as File).writeText("""
            |<Project>
            |   <PropertyGroup>
            |       <SdkVersion>$version</SdkVersion>
            |       <Title>resharper_unreal</Title>
            |   </PropertyGroup>
            |</Project>""".trimMargin())
        }
    }

    create("compileDotNet") {
        dependsOn("findMsBuild")
        dependsOn("patchPropsFile")
        doLast {
            exec {
                executable = project.tasks.getByName("findMsBuild").extra["executable"] as String
                args = listOf("/t:Restore;Rebuild", "${project.extra["dotnetSolution"]}", "/v:minimal", "/p:Configuration=$buildConfiguration")
            }
        }
    }
}


tasks {
    named<Zip>("buildPlugin") {
        dependsOn("findMsBuild")
        outputs.upToDateWhen { false }
        doLast {
            copy {
                from("$buildDir/distributions/${rootProject.name}-$version.zip")
                into("$rootDir/output")
            }

            val changelogText = File("$repoRoot/CHANGELOG.md").readText()
            val changelogMatches = Regex("/(?s)(-.+?)(?=##|$)/").findAll(changelogText)
            val changeNotes = changelogMatches.toList().map {
                it.groups[1]!!.value.replace(Regex("/(?s)- /"), "\u2022 ").replace(Regex("/`/"), "").replace(Regex("/,/"), "%2C")
            }.take(1).joinToString("", "", "")

            exec {
                executable = getByName("findMsBuild").extra["executable"] as String
                args = listOf("/t:Pack", "${project.extra["dotnetSolution"]}", "/v:minimal", "/p:Configuration=$buildConfiguration", "/p:PackageOutputPath=$rootDir/output", "/p:PackageReleaseNotes=$changeNotes", "/p:PackageVersion=$version")
            }
        }
    }
}

intellij {
    //    type = "RD"
//    version = "$sdkVersion-SNAPSHOT"
    localPath = "C:\\Work\\JetBrains.Rider-2019.2-EAP7D-192.5895.894.Checked.win"
    downloadSources = false
}

apply(plugin = Libraries.rdGenPluginId)
//apply(from = "model.gradle.kts")

val modelDir = File(repoRoot, "protocol/src/main/kotlin/model")
val hashBaseDir = File(repoRoot, "build/rdgen")
tasks {
    create<RdgenTask>("generateRiderModel") {
        configure<RdgenParams> {
            val csOutput = File(repoRoot, "src/dotnet/ReSharperPlugin.resharper_unreal/model/RdRiderProtocol")
            val ktOutput = File(repoRoot, "src/rider/main/kotlin/com/jetbrains/rider/model/RdRiderProtocol")


            // NOTE: classpath is evaluated lazily, at execution time, because it comes from the unzipped
            // intellij SDK, which is extracted in afterEvaluate
            verbose = true
            classpath("${rootProject.extra["rdLibDirectory"]}/rider-model.jar")
            sources("$modelDir/rider")
            packages = "model.rider"
            hashFolder = "$hashBaseDir/rider"

            generator {
                language = "kotlin"
                transform = "asis"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                //            namespace = "com.jetbrains.rider.plugins.unreal"
                namespace = "com.jetbrains.rider.model"
                directory = "$ktOutput"

            }

            generator {
                language = "csharp"
                transform = "reversed"
                root = "com.jetbrains.rider.model.nova.ide.IdeRoot"
                namespace = "JetBrains.Rider.Model"
                directory = "$csOutput"
            }
            properties["model.out.src.rider.csharp.dir"] = "$csOutput"
            properties["model.out.src.rider.kotlin.dir"] = "$ktOutput"
        }
    }


    create<RdgenTask>("generateEditorPluginModel") {
        configure<RdgenParams> {
            val backendCsOutput = File(repoRoot, "src/dotnet/ReSharperPlugin.resharper_unreal/model/RdEditorProtocol")
            val unrealEditorCppOutput = File(repoRoot, "src/cpp/Source/RiderLink/Private/RdEditorProtocol")


            verbose = true
            classpath("${rootProject.extra["rdLibDirectory"]}/rider-model.jar")
            sources("$modelDir/editorPlugin")
            hashFolder = "$hashBaseDir/editorPlugin"
            packages = "model.editorPlugin"
//            changeCompiled()

            generator {
                language = "csharp"
                transform = "asis"
                namespace = "JetBrains.Platform.Unreal.EditorPluginModel"
                root = "model.editorPlugin.RdEditorModel"
                directory = "$backendCsOutput"
            }

            generator {
                language = "cpp"
                transform = "reversed"
                namespace = "jetbrains.editorplugin"
                root = "model.editorPlugin.RdEditorModel"
                directory = "$unrealEditorCppOutput"
            }
            properties["model.out.src.editorPlugin.csharp.dir"] = "$backendCsOutput"
            properties["model.out.src.editorPlugin.cpp.dir"] = "$unrealEditorCppOutput"
        }
    }

    create("generateModel") {
        group = "protocol"
        description = "Generates protocol models."
        dependsOn("generateRiderModel"/*, "generateEditorPluginModel"*/)
    }
    withType<Jar> {
        dependsOn("generateModel")
    }
}



tasks {
    withType<PatchPluginXmlTask> {
        val changelogText = File("$repoRoot/CHANGELOG.md").readText()
        val changelogMatches = Regex("""/(?s)(-.+?)(?=##|$)/""").findAll(changelogText)

        setChangeNotes(changelogMatches.map {
            it.groups[1]!!.value.replace(Regex("/(?s)\r?\n/"), "<br />\n")
        }.take(1).joinToString("", "", "")
        )
    }

    withType<PrepareSandboxTask> {
        dependsOn("compileDotNet")
        val outputFolder = Paths.get(
                dotNetDir.absolutePath,
                "$dotNetRootId.$dotNetSolutionId",
                "bin",
                dotNetPluginId,
                buildConfiguration).toFile()
        val dllFiles = listOf(
                File(outputFolder, "$dotNetPluginId.dll"),
                File(outputFolder, "$dotNetPluginId.pdb")
        )

        dllFiles.forEach { file ->
            from(file)
            into("${intellij.pluginName}/dotnet")
        }

        doLast {
            dllFiles.forEach { file ->
                if (!file.exists()) throw RuntimeException("File $file does not exist")
            }
        }
    }
}

apply(from = "cpp.gradle.kts")