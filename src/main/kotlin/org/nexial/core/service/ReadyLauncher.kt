/*
 * Copyright 2012-2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

package org.nexial.core.service

import org.apache.commons.io.FileUtils
import org.apache.commons.lang3.StringUtils
import org.nexial.core.NexialConst.Data.THIRD_PARTY_LOG_PATH
import org.nexial.core.NexialConst.Project
import org.nexial.core.NexialConst.Project.NEXIAL_HOME
import org.nexial.core.NexialConst.Project.resolveStandardPaths
import org.nexial.core.NexialConst.SUBDIR_LOGS
import org.nexial.core.model.ExecutionContext
import org.nexial.core.model.ExecutionDefinition
import org.nexial.core.model.TestProject
import org.nexial.core.utils.ExecUtils
import org.springframework.boot.ExitCodeGenerator
import org.springframework.boot.SpringApplication
import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.autoconfigure.jackson.JacksonAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceAutoConfiguration
import org.springframework.boot.autoconfigure.jdbc.DataSourceTransactionManagerAutoConfiguration
import org.springframework.boot.builder.SpringApplicationBuilder
import org.springframework.boot.web.servlet.support.SpringBootServletInitializer
import org.springframework.context.ConfigurableApplicationContext
import org.springframework.context.annotation.Configuration
import java.io.File
import java.io.File.separator
import java.util.*

@SpringBootApplication(scanBasePackages = ["org.nexial.core.service"],
                       exclude = [JacksonAutoConfiguration::class, DataSourceAutoConfiguration::class, DataSourceTransactionManagerAutoConfiguration::class])
@Configuration
class ReadyLauncher : SpringBootServletInitializer() {

    companion object {

        private val args = ArrayList<String>()
        lateinit var springContext: ConfigurableApplicationContext
        lateinit var application: SpringApplication
        private lateinit var context: ExecutionContext

        @Throws(Exception::class)
        @JvmStatic
        fun main(args: Array<String>) = start(args)

        fun start(args: Array<String>) {
            Companion.args += args

            // todo: TO BE REMOVED
            // 0. set NEXIAL_HOME
            System.setProperty(NEXIAL_HOME, File("").absolutePath)
            val project = resolveProject()

            // 1. create `service` project
            val runId = ExecUtils.deriveRunId()

            // 2. register log directory and system properties
            System.setProperty(THIRD_PARTY_LOG_PATH, Project.appendLog(project.outPath) + SUBDIR_LOGS)

            // 3. new context
            val execDef = ExecutionDefinition()
            execDef.project = project
            execDef.runId = runId
            context = ExecutionContext(execDef)

            // 4. start spring boot
            val builder = SpringApplicationBuilder(ReadyLauncher::class.java)
            application = builder.application()
            springContext = builder.build().run(*args)
            springContext.registerShutdownHook()
        }

        @Deprecated("to be removed soon")
        fun context() = context

        private fun resolveProject(): TestProject {
            // todo need to revisit this.. do we need it still?
            var project = TestProject()
            project.projectHome =
                    StringUtils.appendIfMissing(File("").absoluteFile.parent, separator) + "nexial-services"
            project.isStandardStructure = true
            project = resolveStandardPaths(project)
            FileUtils.forceMkdir(File(project.scriptPath))
            FileUtils.forceMkdir(File(project.dataPath))
            FileUtils.forceMkdir(File(project.planPath))
            FileUtils.forceMkdir(File(project.outPath + SUBDIR_LOGS))
            return project
        }

//        fun refresh() {
//            requireNotNull(springContext)
//
//            springContext.stop()
//            try {
//                Thread.sleep(1500)
//            } catch (e: Exception) {
//                e.printStackTrace()
//            }
//            springContext.refresh()
//            springContext.start()
//        }

        fun restart() {
            requireNotNull(springContext)
            requireNotNull(application)

            shutdown()

            val temp = args.toArray(Array(args.size) { "" })
            args.clear()
            start(temp)
        }

        fun shutdown() {
            requireNotNull(springContext)

            SpringApplication.exit(springContext, ExitCodeGenerator { 0 })
            springContext.close()
        }
    }

    override fun configure(app: SpringApplicationBuilder): SpringApplicationBuilder =
            app.sources(ReadyLauncher::class.java)

//    open fun fowardToIndex(): WebMvcAutoConfigurationAdapter {
//
//    }
}